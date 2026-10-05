package com.emabuia.pokevault.util

import java.text.Normalizer
import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * La chiave di un illustratore e' l'id del documento "seguito" su Firestore:
 * iOS deve calcolarla uguale ad Android, che usa java.text.Normalizer. Il
 * codice comune non ce l'ha e ripiega gli accenti con una tabella: qui, dove
 * il Normalizer c'e', le due strade si confrontano carattere per carattere.
 */
class IllustratorKeyNormalizerTest {

    /** keyOf di Android, riga per riga (util/Illustrators.kt). */
    private fun androidKeyOf(name: String): String =
        Normalizer.normalize(name.trim(), Normalizer.Form.NFD)
            .replace(Regex("""\p{Mn}+"""), "")
            .lowercase(Locale.ROOT)
            .replace(Regex("""[.,'`’‘-]"""), "")
            .replace(Regex("""\s+"""), " ")
            .trim()

    @Test
    fun everyLatinLetterFoldsLikeTheNormalizer() {
        val ranges = listOf(0x20..0x24F, 0x1E00..0x1EFF)
        val different = ranges.flatMap { it.toList() }
            .map { Char(it).toString() }
            .filter { IllustratorNames.keyOf("a${it}a") != androidKeyOf("a${it}a") }
        assertEquals(emptyList(), different)
    }

    @Test
    fun textAlreadyDecomposedFoldsToo() {
        val decomposed = Normalizer.normalize("Mékayu Pokémon Kōki Saitō", Normalizer.Form.NFD)
        assertEquals(androidKeyOf(decomposed), IllustratorNames.keyOf(decomposed))
    }

    /** I nomi con accenti o punteggiatura di /v1/illustrators (05/10/2026), e qualche grafia da set futuri. */
    @Test
    fun realCatalogNamesGiveAndroidsKeys() {
        listOf(
            "Mékayu", "Illus. & Direc. The Pokémon Company Art Team", "Natsuko Shoji été",
            "K. Hoshiba", "K Hoshiba", "Zu-ka", "Zu-Ka", "takuyoa", "Saitou, Kouki",
            "Kōji Nishino", "Øyvind", "Łukasz", "Ångström", "Dœ", "İzmir",
        ).forEach { assertEquals(androidKeyOf(it), IllustratorNames.keyOf(it), it) }
    }
}
