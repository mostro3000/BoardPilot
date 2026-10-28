package ar.com.mt0.boardpilot.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TitleTapDetectorTest {

    @Test
    fun `seis toques seguidos dentro de la ventana abren el huevo`() {
        val detector = TitleTapDetector()
        var openedAt = -1
        for (i in 0 until 6) {
            if (detector.onTap(1_000L + i * 300L)) openedAt = i
        }
        assertTrue("debio abrir exactamente en el sexto toque", openedAt == 5)
    }

    @Test
    fun `cinco toques no alcanzan`() {
        val detector = TitleTapDetector()
        repeat(5) { i ->
            assertFalse(detector.onTap(5_000L + i * 200L))
        }
    }

    @Test
    fun `una pausa mayor a la ventana reinicia la racha`() {
        val detector = TitleTapDetector()
        repeat(5) { i -> detector.onTap(10_000L + i * 200L) }
        // Pausa de 2 s (> 1,5 s): la racha muere y este cuenta como primer toque.
        assertFalse(detector.onTap(13_000L))
        repeat(4) { i ->
            assertFalse(detector.onTap(13_200L + i * 200L))
        }
        assertTrue(detector.onTap(14_000L))
    }

    @Test
    fun `despues de abrir la racha queda en cero`() {
        val detector = TitleTapDetector()
        repeat(6) { i -> detector.onTap(20_000L + i * 100L) }
        assertFalse(detector.onTap(20_700L))
    }
}
