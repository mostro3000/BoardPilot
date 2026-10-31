package ar.com.mt0.boardpilot.domain

import ar.com.mt0.boardpilot.demo.GoldenCaseSmps
import ar.com.mt0.boardpilot.i18n.AppLanguage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class DiagnosisEngineTest {

    private fun engineAtMeasuring(language: AppLanguage): DiagnosisEngine {
        val engine = DiagnosisEngine(GoldenCaseSmps.newSession(language))
        engine.loadMapping(GoldenCaseSmps.components(language))
        engine.loadHypotheses(
            GoldenCaseSmps.hypotheses(language),
            GoldenCaseSmps.requests(language),
        )
        engine.startMeasuring()
        return engine
    }

    private val safetyParts = listOf("c1", "c2", "c3").map {
        BoardComponent(it, it.uppercase(), ComponentKind.OTHER, NormBox(0.1f, 0.1f, 0.1f, 0.1f))
    }

    // h1 queda lider y confirmada con un anormal, pero sin alcanzar el umbral del veredicto.
    private val safetyHypotheses = listOf(
        Hypothesis("h1", "t1", "c1", 0.2, ""),
        Hypothesis("h2", "t2", "c2", 0.4, ""),
        Hypothesis("h3", "t3", "c3", 0.4, ""),
    )

    private fun measurement(id: String, kind: MeasurementKind, target: String = "h1") = MeasurementRequest(
        id = id,
        componentId = "c" + target.drop(1),
        kind = kind,
        instruction = "i",
        normalDescription = "n",
        abnormalDescription = "a",
        gate = safetyGateFor(kind),
        targetHypothesisId = target,
    )

    private fun engineWith(
        requests: List<MeasurementRequest>,
        parts: List<BoardComponent> = safetyParts,
    ): DiagnosisEngine {
        val engine = DiagnosisEngine(DiagnosisSession(BoardType.SMPS, "s"))
        engine.loadMapping(parts)
        engine.loadHypotheses(safetyHypotheses, requests)
        return engine
    }

    private fun DiagnosisEngine.answer(outcome: MeasurementOutcome) {
        startMeasuringIfNeeded()
        confirmSafety()
        submitResult(MeasurementResult(session.currentRequest!!.id, outcome))
    }

    private fun DiagnosisEngine.startMeasuringIfNeeded() {
        if (session.stage == Stage.HYPOTHESES) startMeasuring()
    }

    private val live = measurement("v1", MeasurementKind.VOLTAGE_DC, "h2")
    private val coldA = measurement("c1", MeasurementKind.CONTINUITY)
    private val coldB = measurement("c2", MeasurementKind.DIODE, "h3")

    @Test
    fun `las mediciones sin tension siempre van antes que las con tension`() {
        val engine = engineWith(listOf(live, coldA, coldB))
        assertEquals(listOf("c1", "c2", "v1"), engine.session.pendingRequests.map { it.id })
    }

    @Test
    fun `con dano visible no se ofrece ninguna medicion con tension`() {
        val parts = safetyParts.map { if (it.id == "c3") it.copy(visibleDamage = "Quemado") else it }
        val engine = engineWith(listOf(live, coldA), parts)
        assertEquals(listOf("c1"), engine.session.pendingRequests.map { it.id })
    }

    @Test
    fun `una falla medida sin tension quita las mediciones con tension pendientes`() {
        val engine = engineWith(listOf(coldA, coldB, live))
        engine.answer(MeasurementOutcome.ABNORMAL)
        assertEquals(Stage.MEASURING, engine.session.stage)
        assertEquals(listOf("c2"), engine.session.pendingRequests.map { it.id })
    }

    @Test
    fun `un chequeo sin tension correcto y sin fallas habilita la medicion con tension`() {
        val engine = engineWith(listOf(coldA, live))
        engine.answer(MeasurementOutcome.NORMAL)
        assertEquals(listOf("v1"), engine.session.pendingRequests.map { it.id })
    }

    @Test
    fun `una lectura dudosa sin tension no habilita enchufar`() {
        val engine = engineWith(listOf(coldA, live))
        engine.answer(MeasurementOutcome.UNCLEAR)
        assertEquals(Stage.HYPOTHESES, engine.session.stage)
        assertTrue(engine.session.pendingRequests.isEmpty())
    }

    @Test
    fun `sin chequeo previo no se cargan mediciones con tension y un plan solo con tension no pasa`() {
        val engine = engineWith(listOf(live))
        assertTrue(engine.session.pendingRequests.isEmpty())
        engine.addRequests(listOf(measurement("v2", MeasurementKind.VOLTAGE_DC, "h3")))
        assertTrue(engine.session.pendingRequests.isEmpty())
        // Con un chequeo sin tension en el mismo plan, si: y va primero.
        engine.addRequests(listOf(measurement("v2", MeasurementKind.VOLTAGE_DC, "h3"), coldA))
        assertEquals(listOf("c1", "v2"), engine.session.pendingRequests.map { it.id })
    }

    @Test
    fun `una falla medida sin tension tambien frena los planes siguientes`() {
        val engine = engineWith(listOf(coldA))
        engine.answer(MeasurementOutcome.ABNORMAL)
        // h1 lidera confirmada: queda el veredicto; igual se comprueba la regla sobre otra sesion.
        val session = engine.session.copy(stage = Stage.HYPOTHESES)
        val resumed = DiagnosisEngine(session)
        resumed.addRequests(listOf(measurement("v2", MeasurementKind.VOLTAGE_DC, "h3")))
        assertTrue(resumed.session.pendingRequests.isEmpty())
    }

    @Test
    fun `una lectura anormal con tension no frena las siguientes con tension`() {
        val engine = engineWith(listOf(coldA, live, measurement("v2", MeasurementKind.VOLTAGE_DC, "h3")))
        engine.answer(MeasurementOutcome.NORMAL)
        engine.answer(MeasurementOutcome.ABNORMAL)
        assertEquals(listOf("v2"), engine.session.pendingRequests.map { it.id })
    }

    @Test
    fun `una sesion guardada con la medicion con tension primero se corrige al cargarla`() {
        val saved = DiagnosisSession(
            boardType = BoardType.SMPS,
            symptom = "s",
            components = safetyParts,
            hypotheses = safetyHypotheses,
            pendingRequests = listOf(live, coldA),
            safetyConfirmedForCurrent = true,
            stage = Stage.MEASURING,
        )
        val engine = DiagnosisEngine(saved)
        assertEquals(listOf("c1", "v1"), engine.session.pendingRequests.map { it.id })
        // La compuerta confirmada era la de otra medicion.
        assertFalse(engine.session.safetyConfirmedForCurrent)
    }

    @Test
    fun `un anormal viejo sin compuerta cuenta como falla y sin medicion pendiente no se queda midiendo`() {
        val saved = DiagnosisSession(
            boardType = BoardType.SMPS,
            symptom = "s",
            components = safetyParts,
            hypotheses = safetyHypotheses,
            pendingRequests = listOf(live),
            results = listOf(MeasurementResult("c0", MeasurementOutcome.ABNORMAL, "n", "c1")),
            stage = Stage.MEASURING,
        )
        val engine = DiagnosisEngine(saved)
        assertTrue(engine.session.pendingRequests.isEmpty())
        assertEquals(Stage.HYPOTHESES, engine.session.stage)
    }

    @Test
    fun `el resultado guarda la compuerta de la medicion`() {
        val engine = engineWith(listOf(coldA, live))
        engine.answer(MeasurementOutcome.NORMAL)
        assertEquals(SafetyGate.DE_ENERGIZED, engine.session.results.single().gate)
    }

    @Test
    fun `el tipo de placa se fija al ingreso o al mapear y no despues`() {
        val engine = DiagnosisEngine(GoldenCaseSmps.newSession(AppLanguage.ES))
        engine.updateBoardType(BoardType.UNKNOWN)
        engine.loadMapping(GoldenCaseSmps.components(AppLanguage.ES))
        // Lo que detecta Muse Spark entra antes de cargar las hipotesis.
        engine.updateBoardType(BoardType.TV_MONITOR)
        assertEquals(BoardType.TV_MONITOR, engine.session.boardType)
        engine.loadHypotheses(GoldenCaseSmps.hypotheses(AppLanguage.ES), GoldenCaseSmps.requests(AppLanguage.ES))
        assertThrows(IllegalStateException::class.java) { engine.updateBoardType(BoardType.SMPS) }
    }

    @Test
    fun `las puntas se suman a las mediciones pendientes y el resultado recuerda el componente`() {
        val engine = engineAtMeasuring(AppLanguage.ES)
        val guide = ProbeGuide(ProbePoint("q3", 0.6f, 0.3f, "drenaje"), null, "")
        engine.attachProbes(mapOf("m_q3" to guide, "inexistente" to guide))
        assertEquals(guide, engine.session.pendingRequests.single { it.id == "m_q3" }.probes)
        assertEquals(Stage.MEASURING, engine.session.stage)

        engine.confirmSafety()
        engine.submitResult(MeasurementResult("m_f1", MeasurementOutcome.NORMAL))
        assertEquals("f1", engine.session.results.single().componentId)
    }

    @Test
    fun `no se puede medir sin confirmar la compuerta de seguridad`() {
        val engine = engineAtMeasuring(AppLanguage.ES)
        val result = engine.submitResult(
            MeasurementResult("m_f1", MeasurementOutcome.NORMAL),
        )
        val rejected = result as DiagnosisEngine.SubmitResult.Rejected
        assertEquals(RejectReason.SAFETY_NOT_CONFIRMED, rejected.reason)
        assertEquals(Stage.MEASURING, engine.session.stage)
        assertTrue(engine.session.results.isEmpty())
    }

    @Test
    fun `camino dorado SMPS fusible sano y Q3 en corto llega al veredicto Q3 en espanol`() {
        val engine = engineAtMeasuring(AppLanguage.ES)
        engine.confirmSafety()
        engine.submitResult(MeasurementResult("m_f1", MeasurementOutcome.NORMAL))
        assertEquals(Stage.MEASURING, engine.session.stage)
        engine.confirmSafety()
        engine.submitResult(MeasurementResult("m_q3", MeasurementOutcome.ABNORMAL))

        assertEquals(Stage.VERDICT, engine.session.stage)
        assertEquals(GoldenCaseSmps.EXPECTED_VERDICT_COMPONENT, engine.session.verdictComponentId)
    }

    @Test
    fun `golden path in English reaches the same Q3 verdict`() {
        val engine = engineAtMeasuring(AppLanguage.EN)
        assertTrue(engine.session.symptom.contains("power supply"))
        engine.confirmSafety()
        engine.submitResult(MeasurementResult("m_f1", MeasurementOutcome.NORMAL))
        engine.confirmSafety()
        engine.submitResult(MeasurementResult("m_q3", MeasurementOutcome.ABNORMAL))

        assertEquals(Stage.VERDICT, engine.session.stage)
        assertEquals(GoldenCaseSmps.EXPECTED_VERDICT_COMPONENT, engine.session.verdictComponentId)
    }

    @Test
    fun `si todo mide normal no hay veredicto inventado`() {
        val engine = engineAtMeasuring(AppLanguage.ES)
        engine.confirmSafety()
        engine.submitResult(MeasurementResult("m_f1", MeasurementOutcome.NORMAL))
        engine.confirmSafety()
        engine.submitResult(MeasurementResult("m_q3", MeasurementOutcome.NORMAL))

        assertEquals(Stage.HYPOTHESES, engine.session.stage)
        assertEquals(null, engine.session.verdictComponentId)
    }

    @Test
    fun `las confidencias quedan normalizadas y ordenadas`() {
        val engine = engineAtMeasuring(AppLanguage.ES)
        engine.confirmSafety()
        engine.submitResult(MeasurementResult("m_f1", MeasurementOutcome.NORMAL))

        val confidences = engine.session.hypotheses.map { it.confidence }
        assertEquals(1.0, confidences.sum(), 1e-9)
        assertEquals(confidences.sortedDescending(), confidences)
    }

    @Test
    fun `sintoma editable solo al ingreso y dano visible sobre el mapeo`() {
        val engine = DiagnosisEngine(GoldenCaseSmps.newSession(AppLanguage.ES))
        engine.updateSymptom("  Cargador 18650 no enciende, sin LED  ")
        assertEquals("Cargador 18650 no enciende, sin LED", engine.session.symptom)
        engine.updateSymptom("   ")
        assertEquals("Cargador 18650 no enciende, sin LED", engine.session.symptom)

        engine.loadMapping(GoldenCaseSmps.components(AppLanguage.ES))
        engine.addComponents(listOf(
            BoardComponent(SparkOutputParser.UNMAPPED_DAMAGE_ID, "DMG 1",
                ComponentKind.OTHER, NormBox(0.55f, 0.56f, 0.23f, 0.15f),
                visibleDamage = "Zona carbonizada"),
            GoldenCaseSmps.components(AppLanguage.ES).first(),
        ))
        assertEquals(7, engine.session.components.size)
        engine.applyVisibleDamage(mapOf("q3" to "Quemado"))
        assertEquals(
            "Quemado",
            engine.session.components.first { it.id == "q3" }.visibleDamage,
        )
    }

    @Test
    fun `reparar y verificar que enciende cierra la sesion en ambos idiomas`() {
        for (language in AppLanguage.entries) {
            val engine = engineAtMeasuring(language)
            engine.confirmSafety()
            engine.submitResult(MeasurementResult("m_f1", MeasurementOutcome.NORMAL))
            engine.confirmSafety()
            engine.submitResult(MeasurementResult("m_q3", MeasurementOutcome.ABNORMAL))
            engine.markRepaired(GoldenCaseSmps.replacement(language))
            engine.markVerified(powersOn = true)
            assertEquals(Stage.VERIFIED, engine.session.stage)
        }
    }

    @Test
    fun `sin veredicto se suman mediciones nuevas y cada resultado recuerda que se midio`() {
        val engine = engineAtMeasuring(AppLanguage.ES)
        // Todas normales: se agotan sin confirmar ninguna hipotesis.
        while (engine.session.currentRequest != null) {
            engine.confirmSafety()
            engine.submitResult(
                MeasurementResult(engine.session.currentRequest!!.id, MeasurementOutcome.NORMAL))
        }
        assertEquals(Stage.HYPOTHESES, engine.session.stage)
        assertTrue(engine.session.results.all { it.note.isNotBlank() })
        val extra = MeasurementRequest("m9", "c12", MeasurementKind.RESISTANCE, "C12 en ohms",
            "sube por carga", "0 ohm", SafetyGate.DE_ENERGIZED, "h_c12")
        engine.addRequests(listOf(extra, extra.copy(id = "m10", targetHypothesisId = "nope")))
        // Solo entra la que apunta a una hipotesis cargada.
        assertEquals(listOf("m9"), engine.session.pendingRequests.map { it.id })
        engine.startMeasuring()
        assertEquals(Stage.MEASURING, engine.session.stage)
    }
}
