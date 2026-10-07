package com.emabuia.pokevault.ui.theme

import com.emabuia.pokevault.data.FileCache
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.builtins.serializer

/**
 * Il Pokemon fisso della Home, che un Premium sceglie nelle impostazioni:
 * selectedHomeSpriteId di PremiumManager su Android. 0 vuol dire casuale.
 */
class HomeSpritePreference(private val store: FileCache) {
    private val _selectedId = MutableStateFlow(valid(store.read(KEY, Int.serializer())?.data ?: 0))
    val selectedId: StateFlow<Int> = _selectedId.asStateFlow()

    fun set(spriteId: Int) {
        val id = valid(spriteId)
        store.write(KEY, Int.serializer(), id)
        _selectedId.value = id
    }

    private fun valid(spriteId: Int): Int = if (spriteId in IDS) spriteId else 0

    companion object {
        private const val KEY = "home_sprite_id"

        /** HOME_SPRITE_IDS di PremiumManager: gli stessi della Home. */
        val IDS = listOf(25, 1, 4, 7, 133, 150, 151, 384, 448, 94, 158, 258, 393, 6, 9, 3)
    }
}
