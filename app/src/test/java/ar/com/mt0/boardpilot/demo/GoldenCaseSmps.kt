package ar.com.mt0.boardpilot.demo

import ar.com.mt0.boardpilot.domain.BoardComponent
import ar.com.mt0.boardpilot.domain.BoardType
import ar.com.mt0.boardpilot.domain.ComponentKind
import ar.com.mt0.boardpilot.domain.DiagnosisSession
import ar.com.mt0.boardpilot.domain.HazardZone
import ar.com.mt0.boardpilot.domain.Hypothesis
import ar.com.mt0.boardpilot.domain.MeasurementKind
import ar.com.mt0.boardpilot.domain.MeasurementRequest
import ar.com.mt0.boardpilot.domain.NormBox
import ar.com.mt0.boardpilot.domain.ProbeGuide
import ar.com.mt0.boardpilot.domain.ProbePoint
import ar.com.mt0.boardpilot.domain.SafetyGate
import ar.com.mt0.boardpilot.i18n.AppLanguage

/**
 * Caso dorado de prueba (ES/EN): fuente conmutada (SMPS) que no enciende, con
 * falla conocida MOSFET Q3 en corto (veredicto esperado "q3"). Fue el modo demo
 * offline de la app hasta el 28 oct; hoy valida en los tests la maquina de
 * estados de punta a punta con datos realistas.
 */
object GoldenCaseSmps {
    const val EXPECTED_VERDICT_COMPONENT = "q3"

    fun components(language: AppLanguage): List<BoardComponent> {
        val bulging = when (language) {
            AppLanguage.ES -> "Capacitor levemente hinchado en la tapa"
            AppLanguage.EN -> "Slightly bulging capacitor top"
        }
        return listOf(
            BoardComponent("f1", "F1", ComponentKind.FUSE, NormBox(0.04f, 0.06f, 0.16f, 0.10f)),
            BoardComponent("c12", "C12", ComponentKind.CAPACITOR, NormBox(0.30f, 0.08f, 0.14f, 0.22f),
                visibleDamage = bulging),
            BoardComponent("q3", "Q3", ComponentKind.MOSFET, NormBox(0.55f, 0.12f, 0.16f, 0.20f)),
            BoardComponent("t1", "T1", ComponentKind.TRANSFORMER, NormBox(0.38f, 0.42f, 0.26f, 0.30f)),
            BoardComponent("u5", "U5", ComponentKind.OPTOCOUPLER, NormBox(0.72f, 0.48f, 0.12f, 0.10f)),
            BoardComponent("d8", "D8", ComponentKind.DIODE, NormBox(0.74f, 0.20f, 0.10f, 0.12f)),
        )
    }

    fun hypotheses(language: AppLanguage): List<Hypothesis> = when (language) {
        AppLanguage.ES -> listOf(
            Hypothesis("h_q3", "MOSFET Q3 en corto (drenaje-fuente)", "q3", 0.50,
                "Falla clásica del primario en SMPS que ni intenta arrancar."),
            Hypothesis("h_c12", "Capacitor C12 degradado/hinchado", "c12", 0.30,
                "Se observa hinchazón leve; puede impedir el arranque del primario."),
            Hypothesis("h_f1", "Fusible F1 abierto", "f1", 0.20,
                "Si el fusible está abierto no llega tensión al puente rectificador."),
        )
        AppLanguage.EN -> listOf(
            Hypothesis("h_q3", "Q3 MOSFET shorted (drain-source)", "q3", 0.50,
                "Classic primary-side failure in a dead SMPS that never tries to start."),
            Hypothesis("h_c12", "C12 capacitor degraded/bulging", "c12", 0.30,
                "Slight bulging is visible; it can prevent primary start-up."),
            Hypothesis("h_f1", "F1 fuse open", "f1", 0.20,
                "An open fuse means no voltage reaches the bridge rectifier."),
        )
    }

    fun requests(language: AppLanguage): List<MeasurementRequest> = when (language) {
        AppLanguage.ES -> listOf(
            MeasurementRequest(
                id = "m_f1", componentId = "f1", kind = MeasurementKind.CONTINUITY,
                instruction = "Multímetro en continuidad: medí entre los dos extremos del fusible F1.",
                normalDescription = "Pita / cercano a 0 Ω: fusible sano.",
                abnormalDescription = "Abierto (OL): fusible quemado.",
                gate = SafetyGate.DE_ENERGIZED, targetHypothesisId = "h_f1",
                probes = fuseProbes("extremo izquierdo", "extremo derecho"),
                zone = HazardZone.PRIMARY,
            ),
            MeasurementRequest(
                id = "m_q3", componentId = "q3", kind = MeasurementKind.RESISTANCE,
                instruction = "Multímetro en resistencia/diodo: medí entre el drenaje (pata central) de Q3 y el GND del primario.",
                normalDescription = "Resistencia alta o caída de diodo (~0,4–0,6 V en un sentido).",
                abnormalDescription = "Corto: ~0 Ω / pita en ambos sentidos. MOSFET destruido.",
                gate = SafetyGate.DE_ENERGIZED, targetHypothesisId = "h_q3",
                probes = mosfetProbes("drenaje (pata central)", "− de C12 (masa del primario)"),
                zone = HazardZone.PRIMARY,
            ),
        )
        AppLanguage.EN -> listOf(
            MeasurementRequest(
                id = "m_f1", componentId = "f1", kind = MeasurementKind.CONTINUITY,
                instruction = "Meter in continuity mode: measure across both ends of fuse F1.",
                normalDescription = "Beeps / near 0 Ω: fuse is good.",
                abnormalDescription = "Open (OL): fuse blown.",
                gate = SafetyGate.DE_ENERGIZED, targetHypothesisId = "h_f1",
                probes = fuseProbes("left end", "right end"),
                zone = HazardZone.PRIMARY,
            ),
            MeasurementRequest(
                id = "m_q3", componentId = "q3", kind = MeasurementKind.RESISTANCE,
                instruction = "Meter in resistance/diode mode: measure between the drain (middle pin) of Q3 and primary-side GND.",
                normalDescription = "High resistance or a diode drop (~0.4–0.6 V one way).",
                abnormalDescription = "Short: ~0 Ω / beeps both ways. MOSFET destroyed.",
                gate = SafetyGate.DE_ENERGIZED, targetHypothesisId = "h_q3",
                probes = mosfetProbes("drain (middle pin)", "C12 − (primary ground)"),
                zone = HazardZone.PRIMARY,
            ),
        )
    }

    // Puntas fijas del caso dorado (en la app las sugiere Muse Spark): una en cada
    // extremo de F1; en Q3, roja en el drenaje y negra en el negativo de C12.
    private fun fuseProbes(red: String, black: String) = ProbeGuide(
        ProbePoint("f1", 0.05f, 0.11f, red),
        ProbePoint("f1", 0.19f, 0.11f, black),
    )

    private fun mosfetProbes(red: String, black: String) = ProbeGuide(
        ProbePoint("q3", 0.63f, 0.31f, red),
        ProbePoint("c12", 0.33f, 0.29f, black),
    )

    fun symptom(language: AppLanguage): String = when (language) {
        AppLanguage.ES -> "Fuente de TV que no enciende, sin standby"
        AppLanguage.EN -> "TV power supply, completely dead, no standby"
    }

    fun replacement(language: AppLanguage): String = when (language) {
        AppLanguage.ES ->
            "Desoldar Q3 y reemplazar por un MOSFET de igual o mayor Vds/Id y Rdson similar o menor. " +
                "Revisar también C12 (hinchado) y la resistencia de gate antes de energizar."
        AppLanguage.EN ->
            "Desolder Q3 and replace it with a MOSFET of equal or higher Vds/Id and similar or lower Rdson. " +
                "Also check C12 (bulging) and the gate resistor before powering up."
    }

    fun newSession(language: AppLanguage): DiagnosisSession = DiagnosisSession(
        boardType = BoardType.SMPS,
        symptom = symptom(language),
    )
}
