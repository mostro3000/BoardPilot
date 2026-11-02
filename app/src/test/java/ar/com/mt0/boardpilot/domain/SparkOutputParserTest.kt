package ar.com.mt0.boardpilot.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SparkOutputParserTest {

    @Test
    fun `el plan suma hipotesis nuevas, dano y mediciones sobre ellas`() {
        val parts = listOf(
            BoardComponent("c1", "CYL 1", ComponentKind.OTHER, NormBox(0.1f, 0.1f, 0.1f, 0.1f)),
            BoardComponent("c2", "RECT 2", ComponentKind.OTHER, NormBox(0.5f, 0.5f, 0.1f, 0.1f)),
        )
        val previas = listOf(Hypothesis("h1", "Vieja", "c1", 0.1, ""))
        val plan = SparkOutputParser.parsePlan(
            """{"damages":[{"componentId":"CYL 1","damage":"Tapa abombada"},{"componentId":"nada","damage":"x"}],
            "newHypotheses":[{"id":"h5","title":"Conector","componentId":"c2","confidence":0.5,"rationale":"r h1","fix":"Si se confirma, resoldar"},
            {"id":"h1","title":"Id repetido","componentId":"c2","confidence":0.3},
            {"id":"h7","title":"Sin pieza","componentId":"zz"},
            {"id":"h8","title":"Sobra","componentId":"c1"}],
            "measurements":[{"id":"m1","componentId":"c2","kind":"CONTINUITY","instruction":"i","targetHypothesisId":"h5"}],
            "needMoreEvidence":""}""",
            parts, previas, emptySet(),
        )
        assertEquals(listOf("h5", "h1b"), plan.hypotheses.map { it.id })
        assertEquals("Resoldar", plan.hypotheses.first().fix)
        assertEquals(mapOf("c1" to "Tapa abombada"), plan.damages)
        assertEquals("h5", plan.requests.single().targetHypothesisId)
    }

    @Test
    fun `un plan sin nada nuevo sigue funcionando`() {
        val parts = listOf(BoardComponent("c1", "CYL 1", ComponentKind.OTHER, NormBox(0.1f, 0.1f, 0.1f, 0.1f)))
        val plan = SparkOutputParser.parsePlan(
            """{"measurements":[],"needMoreEvidence":"Sacá otra foto"}""", parts,
            listOf(Hypothesis("h1", "Vieja", "c1", 0.1, "")), emptySet(),
        )
        assertTrue(plan.hypotheses.isEmpty() && plan.damages.isEmpty() && plan.requests.isEmpty())
        assertEquals("Sacá otra foto", plan.needMoreEvidence)
    }

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
              {"id":"m4","componentId":"trf1-sam0","kind":"continuity","instruction":"i","normal":"n","abnormal":"a","targetHypothesisId":"h1"},
              {"id":"m5","componentId":"cyl5-sam7","kind":"DIODE","instruction":"i","normal":"n","abnormal":"a","targetHypothesisId":"nope"}
            ]}
        """.trimIndent()
        val d = SparkOutputParser.parse(json, components)
        assertEquals(listOf("h1"), d.hypotheses.map { it.id })
        // m3 tiene destino roto pero mide el componente de h1: se conserva para h1.
        // m5 tiene destino roto y su componente no tiene hipotesis: se descarta.
        assertEquals(listOf("m3", "m4"), d.requests.map { it.id })
        assertEquals("h1", d.requests[0].targetHypothesisId)
        assertEquals(MeasurementKind.CONTINUITY, d.requests[1].kind)
    }

    @Test
    fun `acepta el designador en vez del id y sinonimos del tipo de medicion`() {
        val json = """
            {"hypotheses":[
              {"id":"h1","title":"Bulk","componentId":"CYL 5","confidence":0.5,"rationale":"r","fix":"Reemplazar por 22 uF 400 V 105 C"}
            ],"measurements":[
              {"id":"m1","componentId":"cyl5","kind":"Continuidad","instruction":"i","normal":"n","abnormal":"a","targetHypothesisId":"h1"},
              {"id":"m2","componentId":"CYL 5","kind":"Tensión continua","instruction":"i","expected":"310 V","fault":"0 V","targetHypothesisId":"h1"},
              {"id":"m3","componentId":"cyl5-sam7","kind":"ohms","instruction":"i","targetHypothesisId":"h1"},
              {"id":"m4","componentId":"cyl5-sam7","kind":"VOLTAGE_AC","instruction":"i","normal":"n","abnormal":"a","targetHypothesisId":"h1"},
              {"id":"m5","componentId":"cyl5-sam7","kind":"tensión alterna","instruction":"i","normal":"n","abnormal":"a","targetHypothesisId":"h1"}
            ]}
        """.trimIndent()
        val d = SparkOutputParser.parse(json, components)
        assertEquals("cyl5-sam7", d.hypotheses.single().componentId)
        assertEquals("Reemplazar por 22 uF 400 V 105 C", d.hypotheses.single().fix)
        // Las mediciones en alterna (red electrica) se rechazan siempre.
        assertEquals(listOf("m1", "m2", "m3"), d.requests.map { it.id })
        assertEquals(
            listOf(MeasurementKind.CONTINUITY, MeasurementKind.VOLTAGE_DC, MeasurementKind.RESISTANCE),
            d.requests.map { it.kind },
        )
        assertEquals("310 V", d.requests[1].normalDescription)
        assertEquals("0 V", d.requests[1].abnormalDescription)
        assertEquals("", d.requests[2].normalDescription)
        assertEquals(SafetyGate.LIVE_VOLTAGE, d.requests[1].gate)
    }

    @Test
    fun `tipos de medicion por nombre`() {
        assertEquals(MeasurementKind.DIODE, SparkOutputParser.parseKind("diode test"))
        assertEquals(MeasurementKind.VOLTAGE_DC, SparkOutputParser.parseKind("voltage"))
        assertEquals(MeasurementKind.VOLTAGE_DC, SparkOutputParser.parseKind("DC voltage"))
        assertEquals(MeasurementKind.RESISTANCE, SparkOutputParser.parseKind("Resistencia"))
        assertEquals(MeasurementKind.CONTINUITY, SparkOutputParser.parseKind("continuity check"))
        assertNull(SparkOutputParser.parseKind("AC voltage"))
        // La red sin decir "alterna": tambien se rechaza, nunca cae a continua.
        assertNull(SparkOutputParser.parseKind("mains voltage"))
        assertNull(SparkOutputParser.parseKind("line voltage"))
        assertNull(SparkOutputParser.parseKind("grid voltage"))
        assertNull(SparkOutputParser.parseKind("tensión de red"))
        assertNull(SparkOutputParser.parseKind("voltaje de red"))
        assertNull(SparkOutputParser.parseKind("voltaje de línea"))
        assertNull(SparkOutputParser.parseKind("voltaje alterno"))
        assertNull(SparkOutputParser.parseKind("alternating current"))
        assertNull(SparkOutputParser.parseKind("VCA"))
        assertNull(SparkOutputParser.parseKind("capacitance"))
        assertNull(SparkOutputParser.parseKind(""))
    }

    @Test
    fun `una instruccion de alterna descarta la medicion aunque el tipo parezca seguro`() {
        val json = """
            {"hypotheses":[
              {"id":"h1","title":"Bulk","componentId":"cyl5-sam7","confidence":0.5,"rationale":"r"}
            ],"measurements":[
              {"id":"m1","componentId":"cyl5-sam7","kind":"VOLTAGE_DC","instruction":"Medir 220 VAC en la entrada","targetHypothesisId":"h1"},
              {"id":"m2","componentId":"cyl5-sam7","kind":"CONTINUITY","instruction":"Con el equipo desenchufado de la red, medir continuidad","targetHypothesisId":"h1"},
              {"id":"m3","componentId":"cyl5-sam7","kind":"VOLTAGE_DC","instruction":"Enchufar el equipo y medir 12 V en la salida","targetHypothesisId":"h1"},
              {"id":"m4","componentId":"cyl5-sam7","kind":"RESISTANCE","instruction":"Medir la tensión de red en el primario","targetHypothesisId":"h1"},
              {"id":"m5","componentId":"cyl5-sam7","kind":"VOLTAGE_DC","instruction":"Measure 120 VAC at the input","targetHypothesisId":"h1"},
              {"id":"m6","componentId":"cyl5-sam7","kind":"CONTINUITY","instruction":"Unplugged from the mains, check continuity","targetHypothesisId":"h1"},
              {"id":"m7","componentId":"cyl5-sam7","kind":"VOLTAGE_DC","instruction":"Measure the alternating voltage at the bridge","targetHypothesisId":"h1"},
              {"id":"m8","componentId":"cyl5-sam7","kind":"VOLTAGE_DC","instruction":"Medir los 220 V de la red en la bornera","targetHypothesisId":"h1"}
            ]}
        """.trimIndent()
        val d = SparkOutputParser.parse(json, components)
        assertEquals(listOf("m2", "m3", "m6"), d.requests.map { it.id })
    }

    @Test
    fun `confianzas en porcentaje conservan el orden y las imposibles se descartan`() {
        val json = """
            {"hypotheses":[
              {"id":"h1","title":"A","componentId":"cyl5-sam7","confidence":85,"rationale":"r"},
              {"id":"h2","title":"B","componentId":"trf1-sam0","confidence":40,"rationale":"r"},
              {"id":"h3","title":"Negativa","componentId":"trf1-sam0","confidence":-3,"rationale":"r"},
              {"id":"h4","title":"Infinita","componentId":"trf1-sam0","confidence":1e999,"rationale":"r"}
            ],"measurements":[]}
        """.trimIndent()
        val d = SparkOutputParser.parse(json, components)
        assertEquals(listOf("h1", "h2"), d.hypotheses.map { it.id })
        assertEquals(0.85, d.hypotheses[0].confidence, 1e-9)
        assertEquals(0.40, d.hypotheses[1].confidence, 1e-9)
    }

    @Test
    fun `plan de mediciones siguientes sobre hipotesis cargadas`() {
        val hypotheses = listOf(
            Hypothesis("h1", "Bulk", "cyl5-sam7", 0.6, "r"),
            Hypothesis("h2", "Primario", "trf1-sam0", 0.4, "r"),
        )
        val json = """
            {"measurements":[
              {"id":"m1","componentId":"TRF 1","kind":"RESISTANCE","instruction":"Bobinado primario","normal":"pocos ohm","abnormal":"abierto","targetHypothesisId":"h2"},
              {"id":"m2","componentId":"cyl5-sam7","kind":"DIODE","instruction":"i","normal":"n","abnormal":"a","targetHypothesisId":"h1"},
              {"id":"m3","componentId":"q99","kind":"DIODE","instruction":"i","normal":"n","abnormal":"a","targetHypothesisId":"h1"},
              {"id":"m4","componentId":"cyl5-sam7","kind":"RESISTANCE","instruction":"i","normal":"n","abnormal":"a","targetHypothesisId":"h1"},
              {"id":"m5","componentId":"cyl5-sam7","kind":"CONTINUITY","instruction":"i","normal":"n","abnormal":"a","targetHypothesisId":"h1"}
            ],"needMoreEvidence":""}
        """.trimIndent()
        val plan = SparkOutputParser.parsePlan(json, components, hypotheses, takenIds = setOf("m1", "m2"))
        // Hasta 3, sin repetir ids ya usados y sin componentes inventados.
        assertEquals(3, plan.requests.size)
        assertEquals("trf1-sam0", plan.requests[0].componentId)
        assertTrue(plan.requests.none { it.id == "m1" || it.id == "m2" })
        assertEquals(plan.requests.size, plan.requests.map { it.id }.distinct().size)
        assertNull(plan.needMoreEvidence)
        val empty = SparkOutputParser.parsePlan(
            """{"measurements":[],"needMoreEvidence":"Foto del lado soldaduras"}""",
            components, hypotheses, emptySet(),
        )
        assertTrue(empty.requests.isEmpty())
        assertEquals("Foto del lado soldaduras", empty.needMoreEvidence)
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

    @Test
    fun `tipo de placa que identifica Muse Spark`() {
        assertEquals(BoardType.TV_MONITOR, SparkOutputParser.parseBoardType("TV_MONITOR"))
        assertEquals(BoardType.TV_MONITOR, SparkOutputParser.parseBoardType("TV/monitor main board or T-con"))
        assertEquals(BoardType.SMPS, SparkOutputParser.parseBoardType("TV power supply"))
        assertEquals(BoardType.INVERTER_APPLIANCE, SparkOutputParser.parseBoardType("compressor inverter"))
        assertEquals(BoardType.AUDIO_AMP, SparkOutputParser.parseBoardType("audio amplifier"))
        // UNKNOWN es "detectar": el modelo no lo puede devolver; lo ilegible tampoco.
        assertNull(SparkOutputParser.parseBoardType("UNKNOWN"))
        assertNull(SparkOutputParser.parseBoardType("tostadora espacial"))
        val d = SparkOutputParser.parse(validJson.replace("\"needMoreEvidence\":\"\"}",
            "\"needMoreEvidence\":\"\",\"boardType\":\"INVERTER_APPLIANCE\"}"), components)
        assertEquals(BoardType.INVERTER_APPLIANCE, d.boardType)
    }

    @Test
    fun `cada medicion trae su zona y nunca hay tension en alta tension`() {
        assertEquals(HazardZone.PRIMARY, SparkOutputParser.parseZone(null))
        assertEquals(HazardZone.PRIMARY, SparkOutputParser.parseZone("no se"))
        // Por palabras enteras: "below" contiene "low" y no es el lado frio;
        // "low voltage" solo tampoco (salida baja no aislada = referida a red).
        assertEquals(HazardZone.PRIMARY, SparkOutputParser.parseZone("below_transformer"))
        assertEquals(HazardZone.PRIMARY, SparkOutputParser.parseZone("low_voltage"))
        assertEquals(HazardZone.SECONDARY, SparkOutputParser.parseZone("secondary"))
        assertEquals(HazardZone.SECONDARY, SparkOutputParser.parseZone("cold side"))
        assertEquals(HazardZone.SECONDARY, SparkOutputParser.parseZone("lado frío"))
        assertEquals(HazardZone.SECONDARY, SparkOutputParser.parseZone("secundario aislado"))
        // "Hot side" es el lado de red: primario en ambos idiomas.
        assertEquals(HazardZone.PRIMARY, SparkOutputParser.parseZone("hot side"))
        assertEquals(HazardZone.PRIMARY, SparkOutputParser.parseZone("primario"))
        assertEquals(HazardZone.HIGH_VOLTAGE, SparkOutputParser.parseZone("alta tensión"))
        assertEquals(HazardZone.HIGH_VOLTAGE, SparkOutputParser.parseZone("HIGH_VOLTAGE"))
        val json = """
            {"hypotheses":[{"id":"h1","title":"CCFL","componentId":"trf1-sam0","confidence":0.5,"rationale":"r"}],
             "measurements":[
              {"id":"m1","componentId":"trf1-sam0","kind":"VOLTAGE_DC","instruction":"i","targetHypothesisId":"h1","zone":"HIGH_VOLTAGE"},
              {"id":"m2","componentId":"trf1-sam0","kind":"RESISTANCE","instruction":"i","targetHypothesisId":"h1","zone":"HIGH_VOLTAGE"},
              {"id":"m3","componentId":"cyl5-sam7","kind":"VOLTAGE_DC","instruction":"i","targetHypothesisId":"h1","zone":"SECONDARY"},
              {"id":"m4","componentId":"cyl5-sam7","kind":"DIODE","instruction":"i","targetHypothesisId":"h1"}
             ]}
        """.trimIndent()
        val requests = SparkOutputParser.parse(json, components).requests
        // La tension en alta tension se descarta; desenergizado en alta tension, si.
        assertEquals(listOf("m2", "m3", "m4"), requests.map { it.id })
        assertEquals(listOf(HazardZone.HIGH_VOLTAGE, HazardZone.SECONDARY, HazardZone.PRIMARY),
            requests.map { it.zone })
    }

    @Test
    fun `puntas validadas contra la foto`() {
        val m1 = MeasurementRequest(
            "m1", "cyl5-sam7", MeasurementKind.DIODE, "i", "n", "a", SafetyGate.DE_ENERGIZED, "h1",
        )
        val (m2, m3, m4) = listOf("m2", "m3", "m4").map { m1.copy(id = it) }
        val json = """
            {"probes":[
              {"measurementId":"m1","red":{"componentId":"cyl5-sam7","x":0.32,"y":0.38,"terminal":"ánodo?"},"black":{"componentId":"TRF 1","x":0.30,"y":0.49,"terminal":"masa"},"visible":true,"note":""},
              {"measurementId":"m2","red":{"x":0.33,"y":0.36,"terminal":"+"},"black":{"componentId":"cyl5-sam7","x":0.90,"y":0.10,"terminal":"-"},"visible":true},
              {"measurementId":"m3","visible":false,"note":"Patas debajo de cyl5-sam7: medir del lado de soldaduras."},
              {"measurementId":"m4","red":{"x":0.40,"y":0.38,"terminal":"a"},"black":{"x":0.40,"y":0.38,"terminal":"b"}},
              {"measurementId":"m9","red":{"x":0.4,"y":0.38,"terminal":"x"}},
              {"measurementId":"m1","red":{"x":0.5,"y":0.4,"terminal":"repetida"}}
            ]}
        """.trimIndent()
        val guides = SparkOutputParser.parseProbes(json, components, listOf(m1, m2, m3, m4))

        val g1 = guides.getValue("m1")
        assertEquals(ProbePoint("cyl5-sam7", 0.32f, 0.38f, "ánodo?"), g1.red)
        // La negra en otro componente, nombrado por su designador.
        assertEquals("trf1-sam0", g1.black?.componentId)
        assertTrue(g1.uncertainPolarity)
        // Sin componente: el medido. Fuera de su caja: se descarta solo esa punta.
        val g2 = guides.getValue("m2")
        assertEquals("cyl5-sam7", g2.red?.componentId)
        assertNull(g2.black)
        // Terminales que no se ven: sin puntos, con la nota (sin ids internos).
        val g3 = guides.getValue("m3")
        assertTrue(g3.points.isEmpty())
        assertEquals("Patas debajo de CYL 5: medir del lado de soldaduras.", g3.note)
        // Las dos puntas en el mismo lugar y sin nota: no hay guia.
        assertFalse("m4" in guides)
        assertEquals(setOf("m1", "m2", "m3"), guides.keys)
        assertTrue(SparkOutputParser.parseProbes("sin json", components, listOf(m1)).isEmpty())
    }

    @Test
    fun `el arreglo queda como accion directa sin repetir la condicion`() {
        // Visto en vivo: "Si se confirma: Si se confirma abierto, reemplazar...".
        assertEquals("Reemplazar por fusible lento 1A 250V",
            SparkOutputParser.cleanFix("Si se confirma abierto, reemplazar por fusible lento 1A 250V"))
        assertEquals("Replace with SB560 5A 60V",
            SparkOutputParser.cleanFix("If confirmed, replace with SB560 5A 60V"))
        assertEquals("Reemplazar CYL 5 por 22 uF 400 V",
            SparkOutputParser.cleanFix("Reemplazar CYL 5 por 22 uF 400 V"))
    }

    @Test
    fun `los textos para el tecnico no muestran ids internos`() {
        // Visto en vivo: "Anormal: OL sin pitido ... y confirma h1."
        val json = """
            {"hypotheses":[
              {"id":"h1","title":"Fusible abierto","componentId":"cyl5-sam7","confidence":0.5,"rationale":"Si h1 se confirma, revisar trf1-sam0.","fix":"If confirmed: replace CYL5-SAM7."},
              {"id":"fuse","title":"Otra","componentId":"trf1-sam0","confidence":0.3,"rationale":"El fuse de entrada."}
            ],"measurements":[
              {"id":"m1","componentId":"cyl5-sam7","kind":"CONTINUITY","instruction":"Puntas en cyl5-sam7.","normal":"Pitido.","abnormal":"OL: confirma h1, no h10.","targetHypothesisId":"h1"}
            ],"needMoreEvidence":"Foto de trf1-sam0."}
        """.trimIndent()
        val d = SparkOutputParser.parse(json, components)
        assertEquals("Si “Fusible abierto” se confirma, revisar TRF 1.", d.hypotheses[0].rationale)
        assertEquals("Replace CYL 5.", d.hypotheses[0].fix)
        // Un id que es una palabra comun no se toca en los textos.
        assertEquals("El fuse de entrada.", d.hypotheses[1].rationale)
        assertEquals("Puntas en CYL 5.", d.requests[0].instruction)
        assertEquals("OL: confirma “Fusible abierto”, no h10.", d.requests[0].abnormalDescription)
        assertEquals("Foto de TRF 1.", d.needMoreEvidence)

        val plan = SparkOutputParser.parsePlan(
            """{"measurements":[{"componentId":"cyl5-sam7","kind":"DIODE","instruction":"Medir cyl5-sam7 para h1.","targetHypothesisId":"h1"}]}""",
            components, d.hypotheses, takenIds = setOf("m1"),
        )
        assertEquals("Medir CYL 5 para “Fusible abierto”.", plan.requests[0].instruction)
    }
}
