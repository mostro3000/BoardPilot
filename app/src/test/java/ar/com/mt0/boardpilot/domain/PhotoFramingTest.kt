package ar.com.mt0.boardpilot.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PhotoFramingTest {

    private val w = 900f
    private val h = 1200f
    private val fit = 0.5f

    /** Punto de la foto (px desde su centro) que esta bajo [x], [y] del marco. */
    private fun PhotoFraming.photoPointAt(x: Float, y: Float): Pair<Float, Float> {
        // Invierte toFrame: resta el centro, desgira y desescala.
        val dx = x - (w / 2f + offsetX)
        val dy = y - (h / 2f + offsetY)
        val r = Math.toRadians(-rotation.toDouble())
        val ux = (dx * Math.cos(r) - dy * Math.sin(r)).toFloat()
        val uy = (dx * Math.sin(r) + dy * Math.cos(r)).toFloat()
        return (ux / (fit * scale)) to (uy / (fit * scale))
    }

    private fun assertStaysUnderFingers(before: PhotoFraming, zoom: Float, turn: Float) {
        val cx = 610f
        val cy = 380f
        val (px, py) = before.photoPointAt(cx, cy)
        val after = before.afterGesture(cx, cy, 0f, 0f, zoom, turn, w, h)
        val (fx, fy) = after.toFrame(px, py, fit, w, h)
        assertEquals(cx, fx, 0.05f)
        assertEquals(cy, fy, 0.05f)
    }

    @Test
    fun `al acercar y girar, lo que esta entre los dedos no se mueve`() {
        assertStaysUnderFingers(PhotoFraming(), zoom = 2f, turn = 0f)
        assertStaysUnderFingers(PhotoFraming(1.5f, 20f, 40f, -30f), zoom = 0.7f, turn = 35f)
        assertStaysUnderFingers(PhotoFraming(3f, 300f, -120f, 80f), zoom = 1.2f, turn = -50f)
    }

    @Test
    fun `el arrastre se suma y la escala tiene limites`() {
        val dragged = PhotoFraming().afterGesture(w / 2f, h / 2f, 25f, -40f, 1f, 0f, w, h)
        assertEquals(25f, dragged.offsetX, 0.001f)
        assertEquals(-40f, dragged.offsetY, 0.001f)
        assertEquals(PhotoFraming.MAX_SCALE,
            PhotoFraming(7f).afterGesture(0f, 0f, 0f, 0f, 3f, 0f, w, h).scale, 0.001f)
        assertEquals(PhotoFraming.MIN_SCALE,
            PhotoFraming(0.4f).afterGesture(0f, 0f, 0f, 0f, 0.1f, 0f, w, h).scale, 0.001f)
    }

    @Test
    fun `cuatro giros de 90 grados vuelven al mismo encuadre`() {
        val start = PhotoFraming(1.3f, 0f, 60f, -20f)
        val once = start.turned90()
        assertEquals(90f, once.rotation, 0.001f)
        assertEquals(20f, once.offsetX, 0.001f)
        assertEquals(60f, once.offsetY, 0.001f)
        val back = start.turned90().turned90().turned90().turned90()
        assertEquals(start.rotation, back.rotation, 0.001f)
        assertEquals(start.offsetX, back.offsetX, 0.001f)
        assertEquals(start.offsetY, back.offsetY, 0.001f)
    }

    @Test
    fun `girar 90 grados una foto apaisada pide agrandarla para cubrir el marco`() {
        // Foto 1000x462 en un marco 4:3 (788x591): derecha la cubre tal cual.
        assertEquals(1f, PhotoFraming().coverScale(1000f, 462f, 788f, 591f), 0.001f)
        // Girada queda angosta: hace falta 788 / (462 * 1.279) ~ 1.333 para cubrir.
        val turned = PhotoFraming().turned90().coverScale(1000f, 462f, 788f, 591f)
        assertEquals(788f / (462f * (591f / 462f)), turned, 0.01f)
        // Un giro intermedio pide mas que derecha.
        assertTrue(PhotoFraming(rotation = 30f).coverScale(1000f, 462f, 788f, 591f) > 1f)
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
