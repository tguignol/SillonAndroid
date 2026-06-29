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

    /**
     * L'utilisateur BALAIE l'app (depuis les récents) = il veut la fermer → on met en pause et on arrête
     * VRAIMENT le service. Sinon le service média « mediaPlayback » garde l'app « en cours » (Samsung
     * « X applications en cours ») et elle peut « revenir ». N.B. ceci ne se déclenche QUE sur le balayage,
     * PAS sur le bouton Accueil / changement d'app → la lecture en arrière-plan normale reste possible.
     */
    override fun onTaskRemoved(rootIntent: Intent?) {
        pauseAllPlayersAndStopSelf()
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
