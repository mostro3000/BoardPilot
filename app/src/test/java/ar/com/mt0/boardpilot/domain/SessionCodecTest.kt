package ar.com.mt0.boardpilot.domain

import ar.com.mt0.boardpilot.demo.DemoCaseSmps
import ar.com.mt0.boardpilot.i18n.AppLanguage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class SessionCodecTest {

    @Test
    fun `la sesion demo en medicion sobrevive encode y decode`() {
        val engine = DiagnosisEngine(DemoCaseSmps.newSession(AppLanguage.ES))
        engine.loadMapping(DemoCaseSmps.components(AppLanguage.ES))
        engine.loadHypotheses(
            DemoCaseSmps.hypotheses(AppLanguage.ES),
            DemoCaseSmps.requests(AppLanguage.ES),
        )
        engine.startMeasuring()
        engine.confirmSafety()
        engine.submitResult(MeasurementResult("m_f1", MeasurementOutcome.NORMAL, "pita | OK"))
        engine.confirmSafety()

        val restored = SessionCodec.decode(SessionCodec.encode(engine.session))

        assertEquals(engine.session, restored)
        assertEquals(Stage.MEASURING, restored.stage)
        assertEquals(true, restored.safetyConfirmedForCurrent)
    }

    @Test
    fun `conserva textos con acentos saltos y separadores`() {
        val session = DiagnosisSession(
            boardType = BoardType.TV_MONITOR,
            symptom = "No enciende | olor a quemado\nlínea 220 V",
            components = listOf(
                BoardComponent(
                    "id|raro", "C 12", ComponentKind.CAPACITOR,
                    NormBox(0.1f, 0.2f, 0.3f, 0.4f), "hinchado\narriba",
                ),
            ),
            stage = Stage.VERDICT,
            verdictComponentId = "id|raro",
            replacement = "Reemplazar por 470 µF | 35 V",
        )
        assertEquals(session, SessionCodec.decode(SessionCodec.encode(session)))
    }

    @Test
    fun `un texto corrupto se rechaza para poder volver al demo`() {
        assertThrows(IllegalArgumentException::class.java) {
            SessionCodec.decode("no-es-una-sesion")
        }
    }
}
