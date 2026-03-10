package com.goriant.jidelite.ui.theme

enum class ThemeMode(
    val storageValue: String,
    val isDarkTheme: Boolean
) {
    DARK(
        storageValue = "dark",
        isDarkTheme = true
    ),
    LIGHT(
        storageValue = "light",
        isDarkTheme = false
    );

    companion object {
        fun fromStorage(value: String?): ThemeMode? {
            return entries.firstOrNull { mode ->
                mode.storageValue.equals(value, ignoreCase = true)
            }
        }
    }
}
