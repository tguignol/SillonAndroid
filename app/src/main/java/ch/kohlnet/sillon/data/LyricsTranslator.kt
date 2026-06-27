package ch.kohlnet.sillon.data

import com.google.mlkit.nl.languageid.LanguageIdentification
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.TranslatorOptions
import kotlinx.coroutines.tasks.await

/**
 * Traduction des paroles SUR L'APPAREIL via ML Kit (gratuit, hors-ligne après téléchargement du
 * modèle, aucune donnée envoyée), équivalent du framework Translation d'Apple côté iOS.
 */
object LyricsTranslator {
    /** Langues prises en charge (source détectée ET cible), comme l'iOS. */
    val supported = setOf("de", "fr", "it", "es", "en")

    /** Langue dominante d'un texte (code BCP-47, ex. « en »), ou null si indéterminée. */
    suspend fun detectLanguage(text: String): String? {
        val code = LanguageIdentification.getClient().identifyLanguage(text).await()
        return code.takeIf { it != "und" }
    }

    /**
     * Traduit chaque ligne (non vide) de `source` vers `target` (codes BCP-47).
     * Renvoie une map index→traduction. Télécharge le modèle au besoin.
     */
    suspend fun translate(lines: List<String>, source: String, target: String): Map<Int, String> {
        val src = TranslateLanguage.fromLanguageTag(source) ?: return emptyMap()
        val tgt = TranslateLanguage.fromLanguageTag(target) ?: return emptyMap()
        val translator = Translation.getClient(
            TranslatorOptions.Builder().setSourceLanguage(src).setTargetLanguage(tgt).build()
        )
        return try {
            translator.downloadModelIfNeeded().await()
            buildMap {
                lines.forEachIndexed { i, line ->
                    if (line.isNotBlank()) put(i, translator.translate(line).await())
                }
            }
        } finally {
            translator.close()
        }
    }
}
