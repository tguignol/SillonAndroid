package ch.kohlnet.sillon.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.view.View
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

/** Préférences par widget (fond translucide ou pochette colorée), choisies dans [SillonWidgetConfig]. */
object WidgetPrefs {
    private fun prefs(c: Context) = c.applicationContext.getSharedPreferences("sillon_widget", Context.MODE_PRIVATE)
    fun setTranslucent(c: Context, id: Int, v: Boolean) = prefs(c).edit().putBoolean("translucent_$id", v).apply()
    fun isTranslucent(c: Context, id: Int) = prefs(c).getBoolean("translucent_$id", false)
    fun clear(c: Context, id: Int) = prefs(c).edit().remove("translucent_$id").apply()
}

/**
 * Widget « Lecture en cours » (écran d'accueil), façon iOS : POCHETTE FLOUTÉE en fond (coloré) + voile
 * pour la lisibilité, titre/artiste/qualité + transport (précédent / lecture-pause / suivant). Option de
 * FOND TRANSLUCIDE par widget (le papier peint transparaît). Tap sur le corps = ouvre l'app. Reconstruit
 * à chaque changement de morceau / d'état via [update] (appelé par le PlayerController).
 */
class SillonWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        update(context)
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        appWidgetIds.forEach { WidgetPrefs.clear(context, it) }
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

        /** Reconstruit TOUS les widgets posés depuis l'état courant du lecteur (chacun selon son option). */
        fun update(context: Context) {
            val ctx = context.applicationContext
            val mgr = AppWidgetManager.getInstance(ctx)
            val ids = mgr.getAppWidgetIds(ComponentName(ctx, SillonWidget::class.java))
            if (ids.isEmpty()) return

            val track = PlayerController.current.value
            val playing = PlayerController.isPlaying.value
            val url = track?.coverUrl
            val blurred = url?.let { coverCache[it] }
            if (url != null && blurred == null) scope.launch { loadAndCache(ctx, url) }

            for (id in ids) {
                val translucent = WidgetPrefs.isTranslucent(ctx, id)
                val views = RemoteViews(ctx.packageName, R.layout.widget_now_playing)

                views.setTextViewText(R.id.widget_title, track?.title ?: ctx.getString(R.string.app_name))
                views.setTextViewText(R.id.widget_artist, track?.artist ?: "")
                views.setTextViewText(R.id.widget_quality, track?.qualityLabel() ?: "")
                views.setImageViewResource(
                    R.id.widget_play_icon,
                    if (playing) R.drawable.ic_widget_pause else R.drawable.ic_widget_play,
                )

                if (translucent) {
                    // Papier peint visible : ni pochette de fond ni voile sombre (le texte garde son ombre).
                    views.setViewVisibility(R.id.widget_cover_bg, View.GONE)
                    views.setViewVisibility(R.id.widget_scrim, View.GONE)
                    views.setInt(R.id.widget_root, "setBackgroundResource", R.drawable.widget_bg_translucent)
                } else {
                    views.setInt(R.id.widget_root, "setBackgroundResource", R.drawable.widget_bg)
                    views.setViewVisibility(R.id.widget_scrim, View.VISIBLE)
                    if (blurred != null) {
                        views.setViewVisibility(R.id.widget_cover_bg, View.VISIBLE)
                        views.setImageViewBitmap(R.id.widget_cover_bg, blurred)
                    } else {
                        views.setViewVisibility(R.id.widget_cover_bg, View.GONE)
                    }
                }

                views.setOnClickPendingIntent(R.id.widget_root, openApp(ctx))
                views.setOnClickPendingIntent(R.id.widget_play, broadcast(ctx, ACTION_PLAY_PAUSE, 1))
                views.setOnClickPendingIntent(R.id.widget_prev, broadcast(ctx, ACTION_PREV, 2))
                views.setOnClickPendingIntent(R.id.widget_next, broadcast(ctx, ACTION_NEXT, 3))

                mgr.updateAppWidget(id, views)
            }
        }

        private suspend fun loadAndCache(ctx: Context, url: String) {
            val bmp = withContext(Dispatchers.IO) { runCatching { blur(loadBitmap(url)) }.getOrNull() } ?: return
            coverCache[url] = bmp
            while (coverCache.size > 8) coverCache.remove(coverCache.keys.first())
            update(ctx)
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

        /** Flou « pauvre » (réduction puis agrandissement bilinéaire) → fond coloré façon Sortie média. */
        private fun blur(src: Bitmap?): Bitmap? {
            src ?: return null
            val small = Bitmap.createScaledBitmap(src, (src.width / 14).coerceAtLeast(1), (src.height / 14).coerceAtLeast(1), true)
            return Bitmap.createScaledBitmap(small, src.width.coerceAtMost(600), src.height.coerceAtMost(600), true)
        }
    }
}
