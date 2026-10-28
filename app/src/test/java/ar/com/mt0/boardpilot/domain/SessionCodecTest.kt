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

    @Test
    fun `hipotesis con reemplazo sugerido y sesiones viejas sin ese campo`() {
        val session = DemoCaseSmps.newSession(AppLanguage.ES).copy(
            components = DemoCaseSmps.components(AppLanguage.ES),
            hypotheses = listOf(
                Hypothesis("h1", "Q3 en corto", "q3", 0.7, "r", fix = "MOSFET N 600 V 4 A (4N60)"),
            ),
            stage = Stage.HYPOTHESES,
        )
        val text = SessionCodec.encode(session)
        assertEquals(session, SessionCodec.decode(text))
        // Formato anterior: la hipotesis sin el septimo campo.
        val legacy = text.lines().joinToString("\n") { line ->
            val parts = line.split("|")
            if (parts.size == 7) parts.take(6).joinToString("|") else line
        }
        assertEquals("", SessionCodec.decode(legacy).hypotheses.single().fix)
    }

    @Test
    fun `guia de puntas visible u oculta y sesiones viejas sin puntas`() {
        val base = DemoCaseSmps.requests(AppLanguage.ES).first()
        val session = DemoCaseSmps.newSession(AppLanguage.ES).copy(
            components = DemoCaseSmps.components(AppLanguage.ES),
            hypotheses = DemoCaseSmps.hypotheses(AppLanguage.ES),
            pendingRequests = listOf(
                base,
                base.copy(id = "m_c12", probes = ProbeGuide(null, null, "Patas debajo;\nlado, soldaduras")),
                base.copy(id = "m_q3", probes = null),
            ),
            results = listOf(MeasurementResult("m0", MeasurementOutcome.NORMAL, "medido", "f1")),
            stage = Stage.HYPOTHESES,
        )
        val text = SessionCodec.encode(session)
        assertEquals(session, SessionCodec.decode(text))
        // Formato anterior: mediciones de 8 campos y resultados de 3.
        val legacy = text.lines().joinToString("\n") { line ->
            val parts = line.split("|")
            when (parts.size) {
                9 -> parts.take(8).joinToString("|")
                4 -> parts.take(3).joinToString("|")
                else -> line
            }
        }
        val old = SessionCodec.decode(legacy)
        assertEquals(listOf(null, null, null), old.pendingRequests.map { it.probes })
        assertEquals("", old.results.single().componentId)
    }
}
