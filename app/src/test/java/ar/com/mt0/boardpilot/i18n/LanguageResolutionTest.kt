package ar.com.mt0.boardpilot.i18n

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class LanguageResolutionTest {

    @Test
    fun `auto con sistema en espanol resuelve ES`() {
        assertEquals(AppLanguage.ES, resolveLanguage(LanguageMode.AUTO, "es"))
        assertEquals(AppLanguage.ES, resolveLanguage(LanguageMode.AUTO, "es-AR"))
    }

    @Test
    fun `auto con sistema en otro idioma resuelve EN`() {
        assertEquals(AppLanguage.EN, resolveLanguage(LanguageMode.AUTO, "en"))
        assertEquals(AppLanguage.EN, resolveLanguage(LanguageMode.AUTO, "pt"))
        assertEquals(AppLanguage.EN, resolveLanguage(LanguageMode.AUTO, ""))
    }

    @Test
    fun `el modo manual pisa el idioma del sistema`() {
        assertEquals(AppLanguage.ES, resolveLanguage(LanguageMode.ES, "en"))
        assertEquals(AppLanguage.EN, resolveLanguage(LanguageMode.EN, "es"))
    }

    @Test
    fun `la configuracion inicial exige clave e idioma o estar marcada completa`() {
        assertEquals(true, isSetupComplete(storedCompleted = true, hasApiKey = false, hasLanguageChoice = false))
        assertEquals(true, isSetupComplete(storedCompleted = false, hasApiKey = true, hasLanguageChoice = true))
        assertEquals(false, isSetupComplete(storedCompleted = false, hasApiKey = true, hasLanguageChoice = false))
        assertEquals(false, isSetupComplete(storedCompleted = false, hasApiKey = false, hasLanguageChoice = true))
        assertEquals(false, isSetupComplete(storedCompleted = false, hasApiKey = false, hasLanguageChoice = false))
    }

    @Test
    fun `ambos paquetes de textos cubren textos clave distintos`() {
        val es = appStrings(AppLanguage.ES)
        val en = appStrings(AppLanguage.EN)
        assertNotEquals(es.appTagline, en.appTagline)
        assertNotEquals(es.demoStart, en.demoStart)
        assertNotEquals(es.verifiedOk, en.verifiedOk)
    }
}
