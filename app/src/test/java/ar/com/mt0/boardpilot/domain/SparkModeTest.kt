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
    fun `sin eleccion del usuario o con un valor desconocido queda Standard`() {
        assertEquals(SparkMode.STANDARD, SparkMode.DEFAULT)
        assertEquals(SparkMode.STANDARD, SparkMode.fromStored(null))
        assertEquals(SparkMode.STANDARD, SparkMode.fromStored(""))
        assertEquals(SparkMode.STANDARD, SparkMode.fromStored("contributor"))
    }

    @Test
    fun `el modo elegido se recupera`() {
        assertEquals(SparkMode.CONTRIBUTOR, SparkMode.fromStored("CONTRIBUTOR"))
        assertEquals(SparkMode.STANDARD, SparkMode.fromStored("STANDARD"))
    }
}
