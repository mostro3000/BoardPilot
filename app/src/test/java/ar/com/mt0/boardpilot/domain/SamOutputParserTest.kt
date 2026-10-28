package ar.com.mt0.boardpilot.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SamOutputParserTest {

    @Test
    fun `parsea la linea de ejemplo oficial con confianza`() {
        val line =
            "<0f>1<|box;x1=444;y1=625;x2=679;y2=850;w=1800;h=1200;c=0.9609375|>" +
                "<|mask;x=0;y=0;c=0.9609375;data=226,236,!abc|>"
        val boxes = SamOutputParser.parse(line)
        assertEquals(1, boxes.size)
        val b = boxes[0]
        assertEquals(1, b.objectId)
        assertEquals(444f / 1800f, b.box.x, 1e-6f)
        assertEquals(625f / 1200f, b.box.y, 1e-6f)
        assertEquals((679f - 444f) / 1800f, b.box.w, 1e-6f)
        assertEquals(0.9609375, b.confidence!!, 1e-9)
    }

    @Test
    fun `parsea varias cajas y tolera falta de confianza`() {
        val line =
            "<0f>0<|box;x1=353;y1=382;x2=453;y2=427;w=537;h=561|><|mask;x=0;y=0;data=46,101,~x|>" +
                ",2<|box;x1=516;y1=117;x2=536;y2=150;w=537;h=561|><|mask;x=0;y=0;data=34,21,~y|>"
        val boxes = SamOutputParser.parse(line)
        assertEquals(listOf(0, 2), boxes.map { it.objectId })
        assertTrue(boxes.all { it.confidence == null })
        assertTrue(boxes.all { it.box.x in 0f..1f && it.box.w > 0f })
    }

    @Test
    fun `iou de cajas identicas es uno y de disjuntas es cero`() {
        val a = NormBox(0.10f, 0.10f, 0.30f, 0.30f)
        val b = NormBox(0.60f, 0.60f, 0.20f, 0.20f)
        assertEquals(1f, SamOutputParser.iou(a, a), 1e-6f)
        assertEquals(0f, SamOutputParser.iou(a, b), 1e-6f)
        val overlap = SamOutputParser.iou(a, NormBox(0.25f, 0.10f, 0.30f, 0.30f))
        assertEquals(1f / 3f, overlap, 1e-5f)
    }

    @Test
    fun `salida vacia es cero coincidencias no un error`() {
        assertTrue(SamOutputParser.parse("").isEmpty())
        assertTrue(SamOutputParser.parse("texto sin tokens").isEmpty())
    }
}
