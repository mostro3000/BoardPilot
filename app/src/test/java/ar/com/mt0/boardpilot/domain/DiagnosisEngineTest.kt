package ar.com.mt0.boardpilot.domain

import ar.com.mt0.boardpilot.demo.DemoCaseSmps
import ar.com.mt0.boardpilot.i18n.AppLanguage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DiagnosisEngineTest {

    private fun engineAtMeasuring(language: AppLanguage): DiagnosisEngine {
        val engine = DiagnosisEngine(DemoCaseSmps.newSession(language))
        engine.loadMapping(DemoCaseSmps.components(language))
        engine.loadHypotheses(
            DemoCaseSmps.hypotheses(language),
            DemoCaseSmps.requests(language),
        )
        engine.startMeasuring()
        return engine
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
        assertEquals(DemoCaseSmps.EXPECTED_VERDICT_COMPONENT, engine.session.verdictComponentId)
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
        assertEquals(DemoCaseSmps.EXPECTED_VERDICT_COMPONENT, engine.session.verdictComponentId)
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
        val engine = DiagnosisEngine(DemoCaseSmps.newSession(AppLanguage.ES))
        engine.updateSymptom("  Cargador 18650 no enciende, sin LED  ")
        assertEquals("Cargador 18650 no enciende, sin LED", engine.session.symptom)
        engine.updateSymptom("   ")
        assertEquals("Cargador 18650 no enciende, sin LED", engine.session.symptom)

        engine.loadMapping(DemoCaseSmps.components(AppLanguage.ES))
        engine.addComponents(listOf(
            BoardComponent(SparkOutputParser.UNMAPPED_DAMAGE_ID, "DMG 1",
                ComponentKind.OTHER, NormBox(0.55f, 0.56f, 0.23f, 0.15f),
                visibleDamage = "Zona carbonizada"),
            DemoCaseSmps.components(AppLanguage.ES).first(),
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
            engine.markRepaired(DemoCaseSmps.replacement(language))
            engine.markVerified(powersOn = true)
            assertEquals(Stage.VERIFIED, engine.session.stage)
        }
    }
}
