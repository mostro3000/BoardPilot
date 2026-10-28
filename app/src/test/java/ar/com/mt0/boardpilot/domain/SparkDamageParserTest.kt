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
