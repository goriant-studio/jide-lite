package com.goriant.jidelite.ui.theme

enum class ThemeMode(val storageValue: String) {
    LIGHT("light"),
    DARK("dark");

    val isDarkTheme: Boolean
        get() = this == DARK

    companion object {
        fun fromStorage(value: String?): ThemeMode? {
            return values().firstOrNull { it.storageValue.equals(value, ignoreCase = true) }
        }
    }
}
