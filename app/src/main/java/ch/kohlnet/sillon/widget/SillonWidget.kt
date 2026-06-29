package ch.kohlnet.sillon.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.view.View
import android.widget.RemoteViews
import ch.kohlnet.sillon.MainActivity
import ch.kohlnet.sillon.R
import ch.kohlnet.sillon.data.AppSettings
import ch.kohlnet.sillon.data.AppearanceMode
import ch.kohlnet.sillon.player.AudioOutputMonitor
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
 * Widget « Lecture en cours », façon Sortie média / iOS : POCHETTE FLOUTÉE en fond (coloré) + voile,
 * nom de la SORTIE audio en haut, grand titre/artiste, BARRE DE PROGRESSION + temps, transport. Respecte
 * le mode clair/sombre/système des réglages Sillon. Option de FOND TRANSLUCIDE par widget. Tap corps =
 * ouvre l'app ; tap sortie = sélecteur de sortie système. Rebâti via [update] (PlayerController).
 */
class SillonWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) = update(context)

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        appWidgetIds.forEach { WidgetPrefs.clear(context, it) }
    }

    // Redimensionnement → on réadapte (masque/affiche sortie + progression selon la hauteur).
    override fun onAppWidgetOptionsChanged(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int, newOptions: android.os.Bundle) {
        update(context)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        when (intent.action) {
            ACTION_PLAY_PAUSE -> PlayerController.togglePlayPause()
            ACTION_NEXT -> PlayerController.next()
            ACTION_PREV -> PlayerController.previous()
            ACTION_TOGGLE_BG -> {
                val id = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
                if (id != AppWidgetManager.INVALID_APPWIDGET_ID) {
                    WidgetPrefs.setTranslucent(context, id, !WidgetPrefs.isTranslucent(context, id))
                }
            }
        }
        if (intent.action in REFRESH_ACTIONS) update(context)
    }

    companion object {
        const val ACTION_PLAY_PAUSE = "ch.kohlnet.sillon.widget.PLAY_PAUSE"
        const val ACTION_NEXT = "ch.kohlnet.sillon.widget.NEXT"
        const val ACTION_PREV = "ch.kohlnet.sillon.widget.PREV"
        const val ACTION_TOGGLE_BG = "ch.kohlnet.sillon.widget.TOGGLE_BG"
        private val REFRESH_ACTIONS = setOf(ACTION_PLAY_PAUSE, ACTION_NEXT, ACTION_PREV, ACTION_TOGGLE_BG)

        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
        // Par URL : pochette NETTE (pour le carré en mode translucide) + FLOUTÉE (pour le fond plein).
        private val coverCache = LinkedHashMap<String, Pair<Bitmap, Bitmap>>()

        fun update(context: Context) {
            val ctx = context.applicationContext
            val mgr = AppWidgetManager.getInstance(ctx)
            val ids = mgr.getAppWidgetIds(ComponentName(ctx, SillonWidget::class.java))
            if (ids.isEmpty()) return

            val track = PlayerController.current.value
            val playing = PlayerController.isPlaying.value
            val pos = PlayerController.positionMs.value
            val dur = PlayerController.durationMs.value.coerceAtLeast(1L)
            val url = track?.coverUrl
            val pair = url?.let { coverCache[it] }
            val sharp = pair?.first
            val blurred = pair?.second
            if (url != null && pair == null) scope.launch { loadAndCache(ctx, url) }
            val dark = isDark(ctx)
            val out = AudioOutputMonitor.output.value

            for (id in ids) {
                val translucent = WidgetPrefs.isTranslucent(ctx, id)
                // Mode « cover » (non translucide) : on affiche TOUJOURS un fond image (pochette réelle, ou
                // dégradé cuivre par défaut quand rien ne joue) → jamais une boîte vide ; texte clair.
                val coverMode = !translucent
                val onDark = coverMode || dark
                val primary = if (onDark) 0xFFFFFFFF.toInt() else 0xFF15140F.toInt()
                val secondary = if (onDark) 0xCCFFFFFF.toInt() else 0x99000000.toInt()
                val faint = if (onDark) 0xB3FFFFFF.toInt() else 0x80000000.toInt()

                val views = RemoteViews(ctx.packageName, R.layout.widget_now_playing)

                views.setTextViewText(R.id.widget_title, track?.title ?: ctx.getString(R.string.app_name))
                views.setTextViewText(R.id.widget_artist, track?.artist?.takeIf { it.isNotBlank() } ?: "Rien en lecture")
                views.setTextViewText(R.id.widget_output_name, outputLabel(out))
                views.setTextViewText(R.id.widget_position, fmt(pos))
                views.setTextViewText(R.id.widget_duration, fmt(dur))
                views.setProgressBar(R.id.widget_progress, 1000, ((pos.toFloat() / dur) * 1000).toInt().coerceIn(0, 1000), false)
                views.setImageViewResource(R.id.widget_play_icon, if (playing) R.drawable.ic_widget_pause else R.drawable.ic_widget_play)

                views.setTextColor(R.id.widget_title, primary)
                views.setTextColor(R.id.widget_artist, secondary)
                views.setTextColor(R.id.widget_output_name, secondary)
                views.setTextColor(R.id.widget_position, faint)
                views.setTextColor(R.id.widget_duration, faint)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    views.setColorStateList(R.id.widget_progress, "setProgressTintList", ColorStateList.valueOf(primary))
                }

                if (translucent) {
                    // Fond translucide (papier peint visible) + POCHETTE EN PETIT CARRÉ à gauche.
                    views.setViewVisibility(R.id.widget_cover_bg, View.GONE)
                    views.setViewVisibility(R.id.widget_scrim, View.GONE)
                    views.setViewVisibility(R.id.widget_thumb, View.VISIBLE)
                    if (sharp != null) {
                        views.setImageViewBitmap(R.id.widget_thumb, sharp)
                    } else {
                        views.setImageViewResource(R.id.widget_thumb, R.drawable.widget_default_cover)
                    }
                    views.setInt(R.id.widget_root, "setBackgroundResource",
                        if (dark) R.drawable.widget_bg_translucent else R.drawable.widget_bg_translucent_light)
                } else {
                    // Mode pochette : pochette floutée en fond PLEIN (ou dégradé par défaut) ; pas de carré.
                    views.setViewVisibility(R.id.widget_thumb, View.GONE)
                    views.setInt(R.id.widget_root, "setBackgroundResource", R.drawable.widget_bg)
                    views.setViewVisibility(R.id.widget_cover_bg, View.VISIBLE)
                    views.setViewVisibility(R.id.widget_scrim, View.VISIBLE)
                    if (blurred != null) {
                        views.setImageViewBitmap(R.id.widget_cover_bg, blurred)
                    } else {
                        views.setImageViewResource(R.id.widget_cover_bg, R.drawable.widget_default_cover)
                    }
                }

                // Widget COURT → on masque la ligne de sortie et la progression (garde titre + contrôles).
                val minH = mgr.getAppWidgetOptions(id).getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 0)
                val compact = minH in 1 until 128
                views.setViewVisibility(R.id.widget_output_row, if (compact) View.GONE else View.VISIBLE)
                views.setViewVisibility(R.id.widget_progress_row, if (compact) View.GONE else View.VISIBLE)

                views.setOnClickPendingIntent(R.id.widget_root, openApp(ctx))
                views.setOnClickPendingIntent(R.id.widget_output_row, outputPanel(ctx))
                views.setOnClickPendingIntent(R.id.widget_bg_toggle, toggleBg(ctx, id))
                views.setOnClickPendingIntent(R.id.widget_play, broadcast(ctx, ACTION_PLAY_PAUSE, 1))
                views.setOnClickPendingIntent(R.id.widget_prev, broadcast(ctx, ACTION_PREV, 2))
                views.setOnClickPendingIntent(R.id.widget_next, broadcast(ctx, ACTION_NEXT, 3))

                mgr.updateAppWidget(id, views)
            }
        }

        /** Mode clair/sombre EFFECTIF selon les réglages Sillon (clair / sombre / système). */
        private fun isDark(ctx: Context): Boolean = when (AppSettings.appearance.value) {
            AppearanceMode.LIGHT -> false
            AppearanceMode.DARK -> true
            AppearanceMode.SYSTEM ->
                (ctx.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        }

        private fun outputLabel(out: AudioOutputMonitor.Output): String = out.name ?: when (out.transport) {
            AudioOutputMonitor.Transport.BLUETOOTH -> "Bluetooth"
            AudioOutputMonitor.Transport.WIRED -> "Écouteurs"
            else -> "Haut-parleur"
        }

        private fun fmt(ms: Long): String {
            val s = (ms / 1000).coerceAtLeast(0)
            return "%d:%02d".format(s / 60, s % 60)
        }

        private suspend fun loadAndCache(ctx: Context, url: String) {
            val pair = withContext(Dispatchers.IO) {
                val src = runCatching { loadBitmap(url) }.getOrNull() ?: return@withContext null
                src to (blur(src) ?: src)
            } ?: return
            coverCache[url] = pair
            while (coverCache.size > 8) coverCache.remove(coverCache.keys.first())
            update(ctx)
        }

        private fun openApp(context: Context): PendingIntent {
            val intent = Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            return PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        }

        private fun outputPanel(context: Context): PendingIntent {
            val intent = Intent("android.settings.panel.action.MEDIA_OUTPUT").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            return PendingIntent.getActivity(context, 4, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        }

        private fun broadcast(context: Context, action: String, code: Int): PendingIntent {
            val intent = Intent(context, SillonWidget::class.java).setAction(action)
            return PendingIntent.getBroadcast(context, code, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        }

        /** Bascule translucide ↔ pochette POUR CE widget (l'id voyage dans l'intent). */
        private fun toggleBg(context: Context, id: Int): PendingIntent {
            val intent = Intent(context, SillonWidget::class.java)
                .setAction(ACTION_TOGGLE_BG)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
            return PendingIntent.getBroadcast(context, 1000 + id, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        }

        private fun loadBitmap(url: String): Bitmap? {
            val conn = URL(url).openConnection().apply { connectTimeout = 8000; readTimeout = 8000 }
            return conn.getInputStream().use { BitmapFactory.decodeStream(it) }
        }

        private fun blur(src: Bitmap?): Bitmap? {
            src ?: return null
            // Flou LÉGER (réduction ×5 puis agrandissement) : la pochette reste reconnaissable, façon Sortie média.
            val small = Bitmap.createScaledBitmap(src, (src.width / 5).coerceAtLeast(1), (src.height / 5).coerceAtLeast(1), true)
            return Bitmap.createScaledBitmap(small, src.width.coerceAtMost(600), src.height.coerceAtMost(600), true)
        }
    }
}
