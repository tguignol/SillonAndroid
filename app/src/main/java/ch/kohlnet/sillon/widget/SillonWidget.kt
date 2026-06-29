package ch.kohlnet.sillon.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.widget.RemoteViews
import ch.kohlnet.sillon.MainActivity
import ch.kohlnet.sillon.R
import ch.kohlnet.sillon.player.PlayerController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.URL

/**
 * Widget « Lecture en cours » (écran d'accueil), façon iOS : pochette + titre/artiste/qualité + transport
 * (précédent / lecture-pause / suivant). Tap sur le corps = ouvre l'app. Reconstruit à chaque changement
 * de morceau / d'état via [update] (appelé par le PlayerController). Les boutons pilotent le lecteur
 * EN PROCESSUS (le service média garde le process vivant pendant la lecture).
 */
class SillonWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        update(context)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        when (intent.action) {
            ACTION_PLAY_PAUSE -> PlayerController.togglePlayPause()
            ACTION_NEXT -> PlayerController.next()
            ACTION_PREV -> PlayerController.previous()
        }
        if (intent.action in TRANSPORT_ACTIONS) update(context)
    }

    companion object {
        const val ACTION_PLAY_PAUSE = "ch.kohlnet.sillon.widget.PLAY_PAUSE"
        const val ACTION_NEXT = "ch.kohlnet.sillon.widget.NEXT"
        const val ACTION_PREV = "ch.kohlnet.sillon.widget.PREV"
        private val TRANSPORT_ACTIONS = setOf(ACTION_PLAY_PAUSE, ACTION_NEXT, ACTION_PREV)

        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
        private val coverCache = LinkedHashMap<String, Bitmap>()

        /** Reconstruit TOUS les widgets posés depuis l'état courant du lecteur. */
        fun update(context: Context) {
            val ctx = context.applicationContext
            val mgr = AppWidgetManager.getInstance(ctx)
            val ids = mgr.getAppWidgetIds(ComponentName(ctx, SillonWidget::class.java))
            if (ids.isEmpty()) return

            val track = PlayerController.current.value
            val playing = PlayerController.isPlaying.value
            val views = RemoteViews(ctx.packageName, R.layout.widget_now_playing)

            views.setTextViewText(R.id.widget_title, track?.title ?: ctx.getString(R.string.app_name))
            views.setTextViewText(R.id.widget_artist, track?.artist ?: "")
            views.setTextViewText(R.id.widget_quality, track?.qualityLabel() ?: "")
            views.setImageViewResource(
                R.id.widget_play_icon,
                if (playing) R.drawable.ic_widget_pause else R.drawable.ic_widget_play,
            )

            // Pochette : cache mémoire, sinon placeholder + téléchargement en fond puis re-update.
            val url = track?.coverUrl
            val cached = url?.let { coverCache[it] }
            if (cached != null) {
                views.setImageViewBitmap(R.id.widget_cover, cached)
            } else {
                views.setImageViewResource(R.id.widget_cover, R.drawable.widget_cover_bg)
                if (url != null) scope.launch {
                    val bmp = withContext(Dispatchers.IO) { runCatching { loadBitmap(url) }.getOrNull() }
                    if (bmp != null) {
                        coverCache[url] = bmp
                        while (coverCache.size > 8) coverCache.remove(coverCache.keys.first())
                        update(ctx)
                    }
                }
            }

            views.setOnClickPendingIntent(R.id.widget_root, openApp(ctx))
            views.setOnClickPendingIntent(R.id.widget_play, broadcast(ctx, ACTION_PLAY_PAUSE, 1))
            views.setOnClickPendingIntent(R.id.widget_prev, broadcast(ctx, ACTION_PREV, 2))
            views.setOnClickPendingIntent(R.id.widget_next, broadcast(ctx, ACTION_NEXT, 3))

            mgr.updateAppWidget(ids, views)
        }

        private fun openApp(context: Context): PendingIntent {
            val intent = Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            return PendingIntent.getActivity(
                context, 0, intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
        }

        private fun broadcast(context: Context, action: String, code: Int): PendingIntent {
            val intent = Intent(context, SillonWidget::class.java).setAction(action)
            return PendingIntent.getBroadcast(
                context, code, intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
        }

        /** Télécharge + décode la pochette (les URLs portent déjà leurs identifiants ; lecture seule). */
        private fun loadBitmap(url: String): Bitmap? {
            val conn = URL(url).openConnection().apply { connectTimeout = 8000; readTimeout = 8000 }
            return conn.getInputStream().use { BitmapFactory.decodeStream(it) }
        }
    }
}
