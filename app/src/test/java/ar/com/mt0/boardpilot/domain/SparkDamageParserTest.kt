package ar.com.mt0.boardpilot.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SparkDamageParserTest {

    private val components = listOf(
        BoardComponent("rect1-sam1", "RECT 1", ComponentKind.OTHER,
            NormBox(0.59f, 0.47f, 0.10f, 0.06f)),
        BoardComponent("part4-sam5", "PART 4", ComponentKind.OTHER,
            NormBox(0.62f, 0.65f, 0.34f, 0.05f)),
    )

    @Test
    fun `parsea hallazgos y limita a tres`() {
        val many = (1..5).joinToString(",") {
            """{"description":"Quemado $it","x":0.1,"y":0.1,"w":0.1,"h":0.1}"""
        }
        val findings = SparkDamageParser.parse("""{"damages":[$many]}""")
        assertEquals(3, findings.size)
        assertEquals("Quemado 1", findings[0].description)
    }

    @Test
    fun `sin dano o json invalido devuelve vacio`() {
        assertTrue(SparkDamageParser.parse("""{"damages":[]}""").isEmpty())
        assertTrue(SparkDamageParser.parse("nada").isEmpty())
        assertTrue(
            SparkDamageParser.parse(
                """{"damages":[{"description":"","x":0,"y":0,"w":1,"h":1}]}""",
            ).isEmpty(),
        )
    }

    @Test
    fun `un hallazgo de un sector pasa a coordenadas de la foto entera`() {
        val sector = SparkDamageParser.SCAN_SECTORS.last()
        val found = SparkDamageParser.fromSector(
            DamageFinding("Quemado", NormBox(0.5f, 0.25f, 0.5f, 0.5f)), sector,
        )
        assertEquals(0.7f, found.box.x, 1e-5f)
        assertEquals(0.55f, found.box.y, 1e-5f)
        assertEquals(0.3f, found.box.w, 1e-5f)
        assertEquals(0.3f, found.box.h, 1e-5f)
    }

    @Test
    fun `los sectores cubren toda la foto y se solapan`() {
        val sectors = SparkDamageParser.SCAN_SECTORS
        assertEquals(1f, sectors.maxOf { it.x + it.w }, 1e-5f)
        assertEquals(1f, sectors.maxOf { it.y + it.h }, 1e-5f)
        assertTrue(sectors[1].x < sectors[0].x + sectors[0].w)
        assertTrue(sectors[2].y < sectors[0].y + sectors[0].h)
    }

    @Test
    fun `el mismo dano en varias pasadas queda uno con el texto del primero y la caja mayor`() {
        val merged = SparkDamageParser.merge(listOf(
            listOf(DamageFinding("Foto entera", NormBox(0.40f, 0.40f, 0.10f, 0.10f))),
            emptyList(),
            // Un sector ve la pieza completa y otro solo una parte.
            listOf(DamageFinding("Sector completo", NormBox(0.38f, 0.38f, 0.14f, 0.14f))),
            listOf(DamageFinding("Sector parcial", NormBox(0.40f, 0.40f, 0.04f, 0.10f))),
        ))
        assertEquals(1, merged.size)
        assertEquals("Foto entera", merged[0].description)
        assertEquals(NormBox(0.38f, 0.38f, 0.14f, 0.14f), merged[0].box)
    }

    @Test
    fun `lo que solo ve un sector se suma y danos separados no se unen`() {
        val merged = SparkDamageParser.merge(listOf(
            listOf(DamageFinding("A", NormBox(0.1f, 0.1f, 0.1f, 0.1f))),
            listOf(DamageFinding("B", NormBox(0.5f, 0.5f, 0.1f, 0.1f))),
            // Se toca apenas con A: es otra pieza.
            listOf(DamageFinding("C", NormBox(0.18f, 0.1f, 0.1f, 0.1f))),
        ))
        assertEquals(listOf("A", "B", "C"), merged.map { it.description })
    }

    @Test
    fun `sin hallazgos queda vacio y con muchos quedan los tres mas vistos`() {
        assertTrue(SparkDamageParser.merge(listOf(emptyList(), emptyList())).isEmpty())
        fun at(x: Float, name: String) = DamageFinding(name, NormBox(x, 0.1f, 0.05f, 0.05f))
        val merged = SparkDamageParser.merge(listOf(
            listOf(at(0.1f, "A"), at(0.2f, "B"), at(0.3f, "C"), at(0.4f, "D")),
            listOf(at(0.4f, "D2"), at(0.3f, "C2")),
            listOf(at(0.4f, "D3")),
        ))
        assertEquals(listOf("D", "C", "A"), merged.map { it.description })
    }

    @Test
    fun `centro dentro de un componente lo marca y fuera crea DMG`() {
        val findings = listOf(
            // Centro (0.64, 0.50): dentro de RECT 1
            DamageFinding("Opto quemado", NormBox(0.60f, 0.47f, 0.08f, 0.06f)),
            // Centro (0.65, 0.60): fuera de todo candidato
            DamageFinding("Zona carbonizada", NormBox(0.55f, 0.54f, 0.20f, 0.12f)),
        )
        val (damages, extras) = SparkDamageParser.assign(findings, components)
        assertEquals(mapOf("rect1-sam1" to "Opto quemado"), damages)
        assertEquals(1, extras.size)
        assertEquals("dmg1-spark", extras[0].id)
        assertEquals("DMG 1", extras[0].designator)
        assertEquals("Zona carbonizada", extras[0].visibleDamage)
    }

    @Test
    fun `los ids DMG no chocan con los ya existentes`() {
        val withDmg = components + BoardComponent(
            "dmg1-spark", "DMG 1", ComponentKind.OTHER,
            NormBox(0.1f, 0.1f, 0.1f, 0.1f), visibleDamage = "Previo",
        )
        val (_, extras) = SparkDamageParser.assign(
            listOf(DamageFinding("Otro quemado", NormBox(0.55f, 0.54f, 0.2f, 0.12f))),
            withDmg,
        )
        assertEquals("dmg2-spark", extras[0].id)
        assertEquals("DMG 2", extras[0].designator)
        assertEquals(3, SparkDamageParser.nextDamageIndex(listOf("dmg2-spark", "x")))
    }
}
