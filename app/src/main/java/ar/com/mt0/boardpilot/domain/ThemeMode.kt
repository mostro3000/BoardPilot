package ar.com.mt0.boardpilot.domain

enum class ThemeMode { AUTO, LIGHT, DARK }

/** AUTO sigue al sistema; LIGHT/DARK son elecciones manuales persistentes. */
fun resolveDarkTheme(mode: ThemeMode, systemIsDark: Boolean): Boolean = when (mode) {
    ThemeMode.AUTO -> systemIsDark
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
}
