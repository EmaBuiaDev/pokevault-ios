package com.emabuia.pokevault.data.model

import com.emabuia.pokevault.testcompat.assertEquals
import com.emabuia.pokevault.testcompat.assertTrue
import kotlin.test.Test

class DeckImportParserTest {

    @Test
    fun `parse normalizes known set alias to api set id`() {
        val input = "4 Dreepy TWM 128"

        val result = DeckImportParser.parse(input)

        assertEquals(1, result.cards.size)
        val card = result.cards.first()
        assertEquals("Dreepy", card.name)
        assertEquals("sv6", card.set)
        assertEquals("128", card.number)
        assertEquals(4, card.qty)
    }

    @Test
    fun `parse keeps unknown set token unchanged`() {
        val result = DeckImportParser.parse("2 Custom Card XYZ 15")

        assertTrue(result.cards.isNotEmpty())
        assertEquals("XYZ", result.cards.first().set)
    }
}