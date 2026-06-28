package ch.kohlnet.sillon.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.Canvas
import ch.kohlnet.sillon.data.ServerType

/**
 * Marques (logos) des serveurs, dessinées au Canvas — miroir de `ServerMarks.swift` (iOS).
 * Différencient visuellement Jellyfin / Navidrome dans le badge source (PAS du texte, comme iOS/iPadOS/macOS).
 */

/** Logo Jellyfin : triangles arrondis imbriqués, dégradé violet → bleu (haut-gauche → bas-droite). */
@Composable
fun JellyfinMark(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val s = size.minDimension
        val pad = s * 0.12f
        val brush = Brush.linearGradient(
            colors = listOf(Color(0xFFAA5CC3), Color(0xFF00A4DC)),
            start = Offset(0f, 0f),
            end = Offset(s, s),
        )
        // Sommets d'un triangle pointant vers le haut.
        val top = Offset(s / 2f, pad)
        val bl = Offset(pad, s - pad)
        val br = Offset(s - pad, s - pad)
        // Anneau triangulaire (contour épais arrondi).
        val ring = Path().apply {
            moveTo(top.x, top.y); lineTo(br.x, br.y); lineTo(bl.x, bl.y); close()
        }
        drawPath(ring, brush, style = Stroke(width = s * 0.16f, join = StrokeJoin.Round, cap = StrokeCap.Round))
        // Triangle plein central (réduit vers le centroïde).
        val cx = (top.x + bl.x + br.x) / 3f
        val cy = (top.y + bl.y + br.y) / 3f
        fun toward(p: Offset, f: Float) = Offset(cx + (p.x - cx) * f, cy + (p.y - cy) * f)
        val inner = Path().apply {
            val t = toward(top, 0.40f); val l = toward(bl, 0.40f); val r = toward(br, 0.40f)
            moveTo(t.x, t.y); lineTo(r.x, r.y); lineTo(l.x, l.y); close()
        }
        drawPath(inner, brush)
    }
}

/** Logo Navidrome : disque vinyle bleu cerné de noir, sillons + étiquette + trou central. */
@Composable
fun NavidromeMark(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val r = size.minDimension / 2f
        val c = Offset(size.width / 2f, size.height / 2f)
        drawCircle(Color(0xFF101216), radius = r, center = c)                       // cerne noir
        drawCircle(Color(0xFF1E8CFF), radius = r * 0.92f, center = c)               // disque bleu
        // Sillons (2 cercles fins plus sombres).
        drawCircle(Color(0x66000000), radius = r * 0.70f, center = c, style = Stroke(width = r * 0.05f))
        drawCircle(Color(0x66000000), radius = r * 0.52f, center = c, style = Stroke(width = r * 0.05f))
        drawCircle(Color.White, radius = r * 0.34f, center = c)                      // étiquette
        drawCircle(Color(0xFF101216), radius = r * 0.08f, center = c)               // trou
    }
}

/** Dispatcher : la marque correspondant au type de serveur. */
@Composable
fun ServerMark(type: ServerType, modifier: Modifier = Modifier) {
    when (type) {
        ServerType.JELLYFIN -> JellyfinMark(modifier)
        ServerType.SUBSONIC -> NavidromeMark(modifier)
    }
}

/**
 * Badge source posé en bas-droite d'une pochette (façon iOS `SourceBadge`).
 * - 1 seule source → pastille blanche ronde avec le logo du serveur.
 * - plusieurs sources → capsule sombre « N » avec une icône de pile.
 * N'est censé s'afficher que si >1 serveur actif (décidé par l'appelant).
 */
@Composable
fun SourceBadge(types: List<ServerType>, sourceCount: Int, modifier: Modifier = Modifier) {
    if (sourceCount > 1) {
        Row(
            modifier = modifier
                .clip(RoundedCornerShape(50))
                .background(Color.Black.copy(alpha = 0.6f))
                .border(0.5.dp, Color.White.copy(alpha = 0.3f), RoundedCornerShape(50))
                .padding(horizontal = 5.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Icon(Icons.Filled.Layers, contentDescription = null, tint = Color.White, modifier = Modifier.size(9.dp))
            Text(
                text = "$sourceCount",
                color = Color.White,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    } else {
        val type = types.firstOrNull() ?: return
        Box(
            modifier = modifier
                .size(20.dp)
                .clip(CircleShape)
                .background(Color.White)
                .border(0.5.dp, Color.Black.copy(alpha = 0.12f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            ServerMark(type, Modifier.size(15.dp))
        }
    }
}
