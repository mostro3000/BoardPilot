package ar.com.mt0.boardpilot.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PhotoGesturesTest {

    private val straight = RotatedPhoto(1000f, 800f, 0f)
    private val box = CropBox(200f, 100f, 600f, 500f)

    private fun CropBox.drag(handle: CropHandle, dx: Float, dy: Float, photo: RotatedPhoto = straight) =
        dragged(handle, dx, dy, photo, minSize = 50f)

    @Test
    fun `el toque agarra primero la esquina, despues el borde y despues adentro`() {
        assertEquals(CropHandle.TOP_LEFT, box.hit(205f, 95f, 20f))
        assertEquals(CropHandle.BOTTOM_RIGHT, box.hit(590f, 510f, 20f))
        assertEquals(CropHandle.RIGHT, box.hit(610f, 300f, 20f))
        assertEquals(CropHandle.TOP, box.hit(400f, 90f, 20f))
        assertEquals(CropHandle.MOVE, box.hit(400f, 300f, 20f))
        assertNull(box.hit(900f, 700f, 20f))
    }

    @Test
    fun `con la foto derecha, mover y estirar no salen de la foto`() {
        assertEquals(CropBox(600f, 0f, 1000f, 400f), box.drag(CropHandle.MOVE, 1000f, -1000f))
        assertEquals(CropBox(150f, 100f, 600f, 600f), box.drag(CropHandle.BOTTOM_LEFT, -50f, 100f))
        // Arrastrar de mas no cruza la esquina fija: queda el minimo.
        assertEquals(CropBox(550f, 450f, 600f, 500f), box.drag(CropHandle.TOP_LEFT, 900f, 900f))
        assertEquals(CropBox(200f, 0f, 1000f, 500f), box.drag(CropHandle.TOP_RIGHT, 2000f, -2000f))
        assertEquals(CropBox(200f, 100f, 600f, 800f), box.drag(CropHandle.BOTTOM, 0f, 5000f))
    }

    @Test
    fun `girada, el recuadro maximo no tiene esquinas vacias y no se puede agrandar`() {
        for (angle in listOf(0f, 12f, 30f, 45f, 60f, 90f, -20f, 135f)) {
            val photo = RotatedPhoto(1000f, 800f, angle)
            val best = photo.largestInside()
            assertTrue("$angle: entra", photo.contains(best))
            val grown = CropBox(best.left - 3f, best.top - 3f, best.right + 3f, best.bottom + 3f)
            assertFalse("$angle: es el maximo", photo.contains(grown))
        }
        // Derecha o a 90 grados es la foto entera.
        val turned = RotatedPhoto(1000f, 800f, 90f)
        assertEquals(800f, turned.boundsW, 0.01f)
        assertEquals(800f, turned.largestInside().width, 0.5f)
        assertEquals(1000f, turned.largestInside().height, 0.5f)
    }

    @Test
    fun `girada, mover o estirar se frena contra el borde de la foto`() {
        val photo = RotatedPhoto(1000f, 800f, 20f)
        val start = photo.largestInside()
        listOf(
            start.drag(CropHandle.MOVE, 400f, 300f, photo),
            start.drag(CropHandle.TOP_LEFT, -500f, -500f, photo),
            start.drag(CropHandle.RIGHT, 800f, 0f, photo),
            start.drag(CropHandle.BOTTOM, 0f, 800f, photo),
        ).forEach { assertTrue(it.toString(), photo.contains(it)) }
        // Achicar primero deja lugar para moverlo dentro de la foto.
        val small = start.drag(CropHandle.TOP_LEFT, 200f, 150f, photo)
        assertTrue(photo.contains(small))
        val moved = small.drag(CropHandle.MOVE, -60f, -40f, photo)
        assertTrue(photo.contains(moved))
        assertTrue(moved.left < small.left)
    }

    @Test
    fun `giro libre con cuarto de vuelta mas cercano y ajuste fino`() {
        assertEquals(90f, quarterOf(100f), 0f)
        assertEquals(10f, fineOf(100f), 0.001f)
        assertEquals(-12f, fineOf(-12f), 0.001f)
        assertEquals(180f, quarterOf(160f), 0f)
        assertEquals(-20f, fineOf(160f), 0.001f)
        assertEquals(-170f, normalizedAngle(190f), 0.001f)
        assertEquals(180f, normalizedAngle(-180f), 0.001f)
        assertEquals(90f, normalizedAngle(450f), 0.001f)
    }

    @Test
    fun `pellizcar el mapa acerca hacia los dedos y nunca muestra fuera de la foto`() {
        val start = MapZoom()
        assertFalse(start.zoomed)
        // Acercar 2x hacia un punto: ese punto del mapa queda bajo los dedos.
        val cx = 600f
        val cy = 200f
        val zoomed = start.afterPinch(cx, cy, 0f, 0f, 2f, 800f, 600f)
        assertTrue(zoomed.zoomed)
        val contentX = (cx - 400f - start.panX) / start.zoom
        assertEquals(cx, 400f + zoomed.panX + zoomed.zoom * contentX, 0.05f)
        // Arrastrar de mas queda topeado en el borde de la foto.
        val far = zoomed.afterPinch(400f, 300f, 5000f, -5000f, 1f, 800f, 600f)
        assertEquals((2f - 1f) * 800f / 2f, far.panX, 0.001f)
        assertEquals(-(2f - 1f) * 600f / 2f, far.panY, 0.001f)
        // Alejar del todo vuelve al mapa completo y centrado.
        val fit = far.afterPinch(400f, 300f, 0f, 0f, 0.1f, 800f, 600f)
        assertEquals(1f, fit.zoom, 0f)
        assertEquals(0f, fit.panX, 0f)
        assertEquals(0f, fit.panY, 0f)
    }
}
