package ar.com.mt0.boardpilot.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PhotoLoadingTest {

    @Test
    fun `el muestreo nunca deja la foto por debajo del objetivo`() {
        // Visto en el S25: una foto de 4000 px con objetivo 1600 quedaba en 1000 px.
        assertEquals(2, PhotoLoading.sampleSize(4000, 1600))
        assertEquals(2, PhotoLoading.sampleSize(3200, 1600))
        assertEquals(1, PhotoLoading.sampleSize(3199, 1600))
        assertEquals(1, PhotoLoading.sampleSize(1200, 1600))
        assertEquals(8, PhotoLoading.sampleSize(16320, 1600))
        listOf(1601, 2500, 4000, 6000, 9000, 16320).forEach { longest ->
            val decoded = longest / PhotoLoading.sampleSize(longest, 1600)
            assertTrue("$longest -> $decoded", decoded in 1600 until 3200)
        }
    }

    @Test
    fun `la escala final deja el lado mayor en el objetivo sin agrandar`() {
        assertEquals(0.8f, PhotoLoading.scaleFor(2000, 1600), 1e-6f)
        assertEquals(1f, PhotoLoading.scaleFor(1600, 1600), 0f)
        assertEquals(1f, PhotoLoading.scaleFor(900, 1600), 0f)
    }

    @Test
    fun `orientacion EXIF a giro y espejo`() {
        assertTrue(PhotoLoading.exifFix(PhotoLoading.NORMAL).isIdentity)
        assertEquals(ExifFix(90f, false), PhotoLoading.exifFix(PhotoLoading.ROTATE_90))
        assertEquals(ExifFix(180f, false), PhotoLoading.exifFix(PhotoLoading.ROTATE_180))
        assertEquals(ExifFix(-90f, false), PhotoLoading.exifFix(PhotoLoading.ROTATE_270))
        assertEquals(ExifFix(0f, true), PhotoLoading.exifFix(PhotoLoading.FLIP_HORIZONTAL))
        assertEquals(ExifFix(180f, true), PhotoLoading.exifFix(PhotoLoading.FLIP_VERTICAL))
        assertEquals(ExifFix(90f, true), PhotoLoading.exifFix(PhotoLoading.TRANSPOSE))
        assertEquals(ExifFix(-90f, true), PhotoLoading.exifFix(PhotoLoading.TRANSVERSE))
        // Sin etiqueta (0) o fuera del estandar: la foto queda como esta.
        assertTrue(PhotoLoading.exifFix(0).isIdentity)
        assertTrue(PhotoLoading.exifFix(9).isIdentity)
    }

    @Test
    fun `solo los giros de 90 grados intercambian ancho y alto`() {
        val swapping = (1..8).filter { PhotoLoading.exifFix(it).swapsSides }
        assertEquals(
            listOf(PhotoLoading.TRANSPOSE, PhotoLoading.ROTATE_90,
                PhotoLoading.TRANSVERSE, PhotoLoading.ROTATE_270),
            swapping,
        )
    }
}
