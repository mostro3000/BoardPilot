package ar.com.mt0.boardpilot.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SparkOutputParserTest {

    private val components = listOf(
        BoardComponent("trf1-sam0", "TRF 1", ComponentKind.TRANSFORMER,
            NormBox(0.27f, 0.47f, 0.32f, 0.05f)),
        BoardComponent("cyl5-sam7", "CYL 5", ComponentKind.OTHER,
            NormBox(0.31f, 0.34f, 0.20f, 0.08f)),
    )

    private val validJson = """
        {"hypotheses":[
          {"id":"h1","title":"Capacitor bulk degradado en CYL 5","componentId":"cyl5-sam7","confidence":0.4,"rationale":"Electrolitico de primario."},
          {"id":"h2","title":"Primario sin oscilacion","componentId":"trf1-sam0","confidence":0.3,"rationale":"Sin conmutacion no hay salida."}
        ],"measurements":[
          {"id":"m1","componentId":"cyl5-sam7","kind":"RESISTANCE","instruction":"Desenchufado, medir resistencia en CYL 5.","normal":"La lectura sube por carga.","abnormal":"0 ohmios estable (corto).","targetHypothesisId":"h1"},
          {"id":"m2","componentId":"trf1-sam0","kind":"VOLTAGE_DC","instruction":"Con precaucion, medir el bus DC.","normal":"~310 V DC tras el puente.","abnormal":"0 V: no llega tension.","targetHypothesisId":"h2"}
        ],"needMoreEvidence":""}
    """.trimIndent()

    @Test
    fun `parsea hipotesis y mediciones validas`() {
        val d = SparkOutputParser.parse(validJson, components)
        assertEquals(2, d.hypotheses.size)
        assertEquals(2, d.requests.size)
        assertEquals("cyl5-sam7", d.hypotheses[0].componentId)
        assertNull(d.needMoreEvidence)
    }

    @Test
    fun `la compuerta la fija el dominio segun el tipo y no el modelo`() {
        val d = SparkOutputParser.parse(validJson, components)
        assertEquals(SafetyGate.DE_ENERGIZED, d.requests[0].gate)
        assertEquals(SafetyGate.LIVE_VOLTAGE, d.requests[1].gate)
        assertEquals(SafetyGate.DE_ENERGIZED, safetyGateFor(MeasurementKind.CONTINUITY))
        assertEquals(SafetyGate.DE_ENERGIZED, safetyGateFor(MeasurementKind.DIODE))
    }

    @Test
    fun `tolera cercos markdown y texto alrededor`() {
        val d = SparkOutputParser.parse("Claro:\n```json\n$validJson\n```", components)
        assertEquals(2, d.hypotheses.size)
    }

    @Test
    fun `descarta ids fuera de los candidatos kinds invalidos y destinos rotos`() {
        val json = """
            {"hypotheses":[
              {"id":"h1","title":"Ok","componentId":"trf1-sam0","confidence":0.5,"rationale":"r"},
              {"id":"hx","title":"Inventado","componentId":"q99","confidence":0.9,"rationale":"r"}
            ],"measurements":[
              {"id":"m1","componentId":"q99","kind":"RESISTANCE","instruction":"i","normal":"n","abnormal":"a","targetHypothesisId":"h1"},
              {"id":"m2","componentId":"trf1-sam0","kind":"MAGIA","instruction":"i","normal":"n","abnormal":"a","targetHypothesisId":"h1"},
              {"id":"m3","componentId":"trf1-sam0","kind":"DIODE","instruction":"i","normal":"n","abnormal":"a","targetHypothesisId":"nope"},
              {"id":"m4","componentId":"trf1-sam0","kind":"continuity","instruction":"i","normal":"n","abnormal":"a","targetHypothesisId":"h1"}
            ]}
        """.trimIndent()
        val d = SparkOutputParser.parse(json, components)
        assertEquals(listOf("h1"), d.hypotheses.map { it.id })
        assertEquals(listOf("m4"), d.requests.map { it.id })
        assertEquals(MeasurementKind.CONTINUITY, d.requests[0].kind)
    }

    @Test
    fun `limita cantidades y fuerza ids unicos`() {
        val hyps = (1..6).joinToString(",") {
            """{"id":"h1","title":"H$it","componentId":"trf1-sam0","confidence":5,"rationale":"r"}"""
        }
        val meas = (1..7).joinToString(",") {
            """{"id":"m1","componentId":"trf1-sam0","kind":"DIODE","instruction":"i","normal":"n","abnormal":"a","targetHypothesisId":"h1"}"""
        }
        val d = SparkOutputParser.parse(
            """{"hypotheses":[$hyps],"measurements":[$meas]}""", components)
        assertEquals(4, d.hypotheses.size)
        assertEquals(5, d.requests.size)
        assertEquals(d.hypotheses.size, d.hypotheses.map { it.id }.distinct().size)
        assertEquals(d.requests.size, d.requests.map { it.id }.distinct().size)
        assertTrue(d.hypotheses.all { it.confidence in 0.01..0.99 })
    }

    @Test
    fun `json invalido o vacio devuelve diagnostico vacio`() {
        assertTrue(SparkOutputParser.parse("no json", components).hypotheses.isEmpty())
        assertTrue(SparkOutputParser.parse("", components).hypotheses.isEmpty())
        assertTrue(SparkOutputParser.parse("[]", components).hypotheses.isEmpty())
        val d = SparkOutputParser.parse(
            """{"hypotheses":[],"measurements":[],"needMoreEvidence":"Foto macro del primario"}""",
            components,
        )
        assertTrue(d.hypotheses.isEmpty())
        assertEquals("Foto macro del primario", d.needMoreEvidence)
    }

    @Test
    fun `dano sin caja SAM crea candidato sintetico referenciable`() {
        val json = """
            {"hypotheses":[
              {"id":"h1","title":"Componente quemado DMG 1","componentId":"dmg1-spark","confidence":0.6,"rationale":"Zona carbonizada visible."}
            ],"measurements":[
              {"id":"m1","componentId":"dmg1-spark","kind":"RESISTANCE","instruction":"i","normal":"n","abnormal":"a","targetHypothesisId":"h1"}
            ],"damages":[
              {"componentId":"cyl5-sam7","damage":"Tapa hinchada"},
              {"componentId":"q99","damage":"Inventado"}
            ],"unmappedDamage":{"description":"Resistencia carbonizada bajo el optoacoplador","x":0.55,"y":0.56,"w":0.23,"h":0.15}}
        """.trimIndent()
        val d = SparkOutputParser.parse(json, components)
        assertEquals(1, d.extraComponents.size)
        val dmg = d.extraComponents[0]
        assertEquals(SparkOutputParser.UNMAPPED_DAMAGE_ID, dmg.id)
        assertEquals("DMG 1", dmg.designator)
        assertEquals("Resistencia carbonizada bajo el optoacoplador", dmg.visibleDamage)
        assertEquals(0.55f, dmg.box.x, 0.001f)
        assertEquals(1, d.hypotheses.size)
        assertEquals(1, d.requests.size)
        assertEquals(mapOf("cyl5-sam7" to "Tapa hinchada"), d.damages)
    }

    @Test
    fun `dano sin caja con coordenadas invalidas no crea candidato`() {
        val json = """
            {"hypotheses":[],"measurements":[],
             "unmappedDamage":{"description":"Quemado","x":"a","y":0.5,"w":0.2,"h":0.2}}
        """.trimIndent()
        val d = SparkOutputParser.parse(json, components)
        assertTrue(d.extraComponents.isEmpty())
    }

    @Test
    fun `minijson parsea escapes numeros y anidados`() {
        val v = MiniJson.parse("""{"a":"línea\ncon \"comillas\"","b":[1,-2.5,true,null],"c":{"d":1e2}}""")
            as Map<*, *>
        assertEquals("línea\ncon \"comillas\"", v["a"])
        assertEquals(listOf(1.0, -2.5, true, null), v["b"])
        assertEquals(100.0, (v["c"] as Map<*, *>)["d"])
    }
}
