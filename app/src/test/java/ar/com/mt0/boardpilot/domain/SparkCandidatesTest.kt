package ar.com.mt0.boardpilot.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class SparkCandidatesTest {

    private fun part(n: Int, size: Float, damage: String? = null) = BoardComponent(
        "part$n-sam$n", "PART $n", ComponentKind.OTHER,
        NormBox(0.01f * (n % 50), 0.01f * (n % 50), size, size), damage,
    )

    @Test
    fun `con pocos candidatos se mandan todos tal cual`() {
        val few = List(10) { part(it, 0.05f) }
        assertSame(few, SparkCandidates.select(few))
    }

    @Test
    fun `placa densa se recorta priorizando lo en juego el dano y lo grande`() {
        // Visto con una fuente de PC: 116 candidatos dejaron a Muse Spark sin respuesta.
        val dense = List(116) { part(it, 0.01f + it * 0.0001f) }.toMutableList()
        dense[3] = part(3, 0.005f, damage = "quemado")
        val chosen = SparkCandidates.select(dense, mustInclude = setOf("part0-sam0"))
        assertEquals(SparkCandidates.MAX, chosen.size)
        val ids = chosen.map { it.id }
        // El de la hipotesis y el danado entran aunque sean de los mas chicos.
        assertTrue("part0-sam0" in ids && "part3-sam3" in ids)
        // Entre el resto, los mas grandes; los mas chicos quedan afuera.
        assertTrue("part115-sam115" in ids)
        assertTrue("part1-sam1" !in ids)
        // Se conserva el orden original.
        assertEquals(ids.sortedBy { it.removePrefix("part").substringBefore("-").toInt() }, ids)
    }
}
