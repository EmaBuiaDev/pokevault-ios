package com.emabuia.pokevault.ui.theme

import com.emabuia.pokevault.data.FileCache
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.builtins.serializer

/** Come ThemeMode su Android: "system", "light", "dark". */
enum class ThemeMode(val code: String) {
    SYSTEM("system"), LIGHT("light"), DARK("dark");

    companion object {
        fun fromCode(code: String?): ThemeMode = entries.firstOrNull { it.code == code } ?: SYSTEM
    }
}

/**
 * La preferenza di tema dell'utente (ThemePreference su Android), salvata nei
 * dati dell'app: sopravvive ai riavvii, e "sistema" segue l'iPhone.
 */
class ThemePreference(private val store: FileCache) {
    private val _mode = MutableStateFlow(ThemeMode.fromCode(store.read(KEY, String.serializer())?.data))
    val mode: StateFlow<ThemeMode> = _mode.asStateFlow()

    fun set(mode: ThemeMode) {
        store.write(KEY, String.serializer(), mode.code)
        _mode.value = mode
    }

    private companion object {
        const val KEY = "app_theme"
    }
}
