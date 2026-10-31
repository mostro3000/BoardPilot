package ar.com.mt0.boardpilot.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class SparkModeTest {

    @Test
    fun `cada modo usa el modelo de la API que le corresponde`() {
        assertEquals("muse-spark-1.3", SparkMode.STANDARD.modelId)
        assertEquals("muse-spark-1.3-contributor", SparkMode.CONTRIBUTOR.modelId)
    }

    @Test
    fun `sin eleccion del usuario o con un valor desconocido queda Contributor`() {
        assertEquals(SparkMode.CONTRIBUTOR, SparkMode.DEFAULT)
        assertEquals(SparkMode.CONTRIBUTOR, SparkMode.fromStored(null))
        assertEquals(SparkMode.CONTRIBUTOR, SparkMode.fromStored(""))
        assertEquals(SparkMode.CONTRIBUTOR, SparkMode.fromStored("contributor"))
    }

    @Test
    fun `sin el aviso aceptado nada se envia en Contributor`() {
        assertEquals(SparkMode.STANDARD, SparkMode.CONTRIBUTOR.effectiveFor(noticeAccepted = false))
        assertEquals(SparkMode.CONTRIBUTOR, SparkMode.CONTRIBUTOR.effectiveFor(noticeAccepted = true))
        assertEquals(SparkMode.STANDARD, SparkMode.STANDARD.effectiveFor(noticeAccepted = false))
        assertEquals(SparkMode.STANDARD, SparkMode.STANDARD.effectiveFor(noticeAccepted = true))
    }

    @Test
    fun `el modo elegido se recupera`() {
        assertEquals(SparkMode.CONTRIBUTOR, SparkMode.fromStored("CONTRIBUTOR"))
        assertEquals(SparkMode.STANDARD, SparkMode.fromStored("STANDARD"))
    }
}
