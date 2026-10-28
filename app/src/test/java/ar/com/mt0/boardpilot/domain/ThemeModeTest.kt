package ar.com.mt0.boardpilot.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeModeTest {

    @Test
    fun `automatico sigue al sistema`() {
        assertTrue(resolveDarkTheme(ThemeMode.AUTO, systemIsDark = true))
        assertFalse(resolveDarkTheme(ThemeMode.AUTO, systemIsDark = false))
    }

    @Test
    fun `manual pisa al sistema`() {
        assertFalse(resolveDarkTheme(ThemeMode.LIGHT, systemIsDark = true))
        assertFalse(resolveDarkTheme(ThemeMode.LIGHT, systemIsDark = false))
        assertTrue(resolveDarkTheme(ThemeMode.DARK, systemIsDark = true))
        assertTrue(resolveDarkTheme(ThemeMode.DARK, systemIsDark = false))
    }
}
