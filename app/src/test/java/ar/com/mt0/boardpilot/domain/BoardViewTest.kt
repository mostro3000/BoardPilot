package ar.com.mt0.boardpilot.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BoardViewTest {

    @Test
    fun `la foto entera entra centrada en el recuadro sin deformarse`() {
        // Visto en el S25: foto 739x1600 en un recuadro de proporcion 0,55.
        val tall = BoardView.fit(550f, 1000f, 739f, 1600f)
        assertEquals(1000f, tall.height, 0.01f)
        assertEquals(461.875f, tall.width, 0.01f)
        assertEquals((550f - tall.width) / 2f, tall.left, 0.01f)
        assertEquals(0f, tall.top, 0.01f)
        // Foto mas ancha que el recuadro: bandas arriba y abajo.
        val wide = BoardView.fit(400f, 300f, 1600f, 739f)
        assertEquals(400f, wide.width, 0.01f)
        assertEquals((300f - wide.height) / 2f, wide.top, 0.01f)
        // Misma proporcion: ocupa todo.
        assertEquals(FitRect(0f, 0f, 300f, 400f), BoardView.fit(300f, 400f, 600f, 800f))
    }

    @Test
    fun `la lupa abarca el componente y las puntas con la proporcion pedida`() {
        val target = NormBox(0.379f, 0.61f, 0.174f, 0.031f)
        val points = listOf(0.385f to 0.626f, 0.547f to 0.626f)
        val photoAspect = 591f / 1280f
        val region = BoardView.lensRegion(target, points, photoAspect, 1.6f)
        assertTrue(region.x <= target.x && region.x + region.w >= target.x + target.w)
        assertTrue(region.y <= target.y && region.y + region.h >= target.y + target.h)
        points.forEach { (x, y) ->
            assertTrue(x in region.x..(region.x + region.w) && y in region.y..(region.y + region.h))
        }
        // Proporcion en px de la foto: ancho/alto de la lupa.
        assertEquals(1.6f, region.w * photoAspect / region.h, 0.01f)
        assertTrue(region.x >= 0f && region.y >= 0f)
        assertTrue(region.x + region.w <= 1.0001f && region.y + region.h <= 1.0001f)
    }

    @Test
    fun `la lupa no se sale de la foto aunque la punta este en el borde`() {
        val corner = BoardView.lensRegion(
            NormBox(0.0f, 0.0f, 0.05f, 0.05f), listOf(0.0f to 0.0f), 1.5f, 1.6f,
        )
        assertEquals(0f, corner.x, 0.0001f)
        assertEquals(0f, corner.y, 0.0001f)
        // Un componente casi tan grande como la foto: la lupa es la foto entera en ese eje.
        val huge = BoardView.lensRegion(NormBox(0.05f, 0.05f, 0.9f, 0.9f), emptyList(), 1.5f, 1.6f)
        assertTrue(huge.h <= 1f && huge.w <= 1f)
    }

    @Test
    fun `en placas densas el mapa muestra las cajas principales`() {
        // Visto con una fuente de PC: 116 cajas tapaban la placa.
        val dense = List(116) {
            BoardComponent("cyl$it-sam$it", "CYL $it", ComponentKind.OTHER,
                NormBox(0.005f * (it % 100), 0.005f * (it % 100), 0.01f + it * 0.0001f, 0.02f))
        }
        val shown = BoardView.mapComponents(dense, inPlay = setOf("cyl0-sam0", "cyl1-sam1"))
        assertEquals(BoardView.MAP_LIMIT, shown.size)
        // Lo que esta en juego se ve aunque sea chico; con pocas cajas se ven todas.
        assertTrue(shown.any { it.id == "cyl0-sam0" } && shown.any { it.id == "cyl1-sam1" })
        assertEquals(dense.take(20), BoardView.mapComponents(dense.take(20), emptySet()))
    }

    @Test
    fun `evidencia por componente y porcentaje de su hipotesis`() {
        val results = listOf(
            MeasurementResult("m1", MeasurementOutcome.NORMAL, componentId = "f1"),
            MeasurementResult("m2", MeasurementOutcome.UNCLEAR, componentId = "q3"),
            MeasurementResult("m3", MeasurementOutcome.ABNORMAL, componentId = "q3"),
            // Sesiones viejas: sin componente, no se marca.
            MeasurementResult("m0", MeasurementOutcome.ABNORMAL),
        )
        assertEquals(
            mapOf("f1" to MeasurementOutcome.NORMAL, "q3" to MeasurementOutcome.ABNORMAL),
            BoardView.measuredEvidence(results),
        )
        val hypotheses = listOf(
            Hypothesis("h1", "Q3 en corto", "q3", 0.38, ""),
            Hypothesis("h2", "Q3 abierto", "q3", 0.12, ""),
            Hypothesis("h3", "F1 abierto", "f1", 0.50, ""),
        )
        assertEquals(mapOf("q3" to 38, "f1" to 50), BoardView.hypothesisShares(hypotheses))
    }
}
