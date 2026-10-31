package ar.com.mt0.boardpilot.i18n

import ar.com.mt0.boardpilot.domain.ComponentKind
import ar.com.mt0.boardpilot.domain.MeasurementKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
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

    @Test
    fun `la primera medicion nombra la pieza y el selector, sin confundirlos`() {
        val es = appStrings(AppLanguage.ES)
        val en = appStrings(AppLanguage.EN)
        assertEquals("Primera: CYL 4, con el selector en resistencia",
            es.firstMeasurement(es.kindName(MeasurementKind.RESISTANCE), "CYL 4"))
        assertEquals("First: CYL 4, with the dial on resistance",
            en.firstMeasurement(en.kindName(MeasurementKind.RESISTANCE), "CYL 4"))
    }

    @Test
    fun `el aviso de Contributor dice que Meta puede entrenar y prohibe datos personales`() {
        val es = appStrings(AppLanguage.ES)
        val en = appStrings(AppLanguage.EN)
        for (texto in listOf(es.contributorDialogText, es.sparkModeContributorHint, es.contributorReminder)) {
            assertTrue(texto, "entrenar" in texto && "personales" in texto && "confidenciales" in texto)
        }
        for (texto in listOf(en.contributorDialogText, en.sparkModeContributorHint, en.contributorReminder)) {
            assertTrue(texto, "train" in texto && "personal" in texto && "confidential" in texto)
        }
    }

    @Test
    fun `cada tipo de componente tiene nombre en ambos idiomas salvo OTHER`() {
        for (strings in listOf(appStrings(AppLanguage.ES), appStrings(AppLanguage.EN))) {
            assertEquals(null, strings.componentKindName(ComponentKind.OTHER))
            val nombres = ComponentKind.entries.filter { it != ComponentKind.OTHER }
                .map { strings.componentKindName(it) }
            assertEquals(nombres.size, nombres.filterNotNull().toSet().size)
        }
        assertEquals("fusible", appStrings(AppLanguage.ES).componentKindName(ComponentKind.FUSE))
        assertEquals("fuse", appStrings(AppLanguage.EN).componentKindName(ComponentKind.FUSE))
    }
}
