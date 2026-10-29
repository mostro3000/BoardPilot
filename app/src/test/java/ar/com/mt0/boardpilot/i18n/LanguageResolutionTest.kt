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
    fun `ambos paquetes de textos cubren textos clave distintos`() {
        val es = appStrings(AppLanguage.ES)
        val en = appStrings(AppLanguage.EN)
        assertNotEquals(es.appTagline, en.appTagline)
        assertNotEquals(es.needKey, en.needKey)
        assertNotEquals(es.verifiedOk, en.verifiedOk)
    }
}
