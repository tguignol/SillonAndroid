package ch.kohlnet.sillon.player

import android.content.Context
import android.content.Intent
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService

/**
 * Service de lecture (Media3) : héberge l'`ExoPlayer` + une `MediaSession`. Permet la lecture en
 * ARRIÈRE-PLAN et les contrôles système (notification média, écran verrouillé), façon iOS. L'UI le
 * pilote via un `MediaController` (cf. [PlayerController]). L'ÉGALISEUR ([EqAudioProcessor]) est inséré
 * dans la chaîne audio de sortie.
 */
@UnstableApi
class PlaybackService : MediaSessionService() {
    private var session: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        val eq = EqAudioProcessor()
        val renderersFactory = object : DefaultRenderersFactory(this) {
            override fun buildAudioSink(
                context: Context,
                enableFloatOutput: Boolean,
                enableAudioTrackPlaybackParams: Boolean,
            ): AudioSink =
                DefaultAudioSink.Builder(context)
                    .setAudioProcessors(arrayOf(eq))
                    .build()
        }
        val player = ExoPlayer.Builder(this, renderersFactory)
            .setHandleAudioBecomingNoisy(true)   // pause si le casque est débranché
            .build()
        session = MediaSession.Builder(this, player).build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    /** Si l'utilisateur ferme l'app et que rien ne joue, on arrête le service. */
    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = session?.player
        if (player == null || !player.playWhenReady || player.mediaItemCount == 0) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        session?.run {
            player.release()
            release()
        }
        session = null
        super.onDestroy()
    }
}
