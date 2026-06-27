package ch.kohlnet.sillon.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Palette Sillon — miroir exact de `Palette` (iOS `Theme.swift`).
 *
 * Tension fondatrice : chaleur cuivrée pour la musique / les pochettes ; froid technique
 * (teal + monospace) réservé STRICTEMENT aux données (bitrate, codec, dB, horodatages de sync).
 * Apparence par défaut = sombre ; les valeurs *Dark reproduisent la palette d'origine iOS.
 */

// Clair
val FondNoirLight = Color(0xFFF6F4EF)
val SurfaceEleveeLight = Color(0xFFFFFFFF)
val AccentCuivreLight = Color(0xFFB06D2C)
val SignalTealLight = Color(0xFF2E7D75)
val TexteIvoireLight = Color(0xFF1C1A17)
val TexteSourdineLight = Color(0xFF6E6A64)

// Sombre (rendu d'origine, apparence par défaut)
val FondNoirDark = Color(0xFF0B0D0F)
val SurfaceEleveeDark = Color(0xFF15181B)
val AccentCuivreDark = Color(0xFFD98E4A)
val SignalTealDark = Color(0xFF4FA8A0)
val TexteIvoireDark = Color(0xFFF3F1EC)
val TexteSourdineDark = Color(0xFF9A9590)
