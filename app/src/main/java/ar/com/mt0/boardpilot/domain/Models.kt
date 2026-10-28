package ar.com.mt0.boardpilot.domain

/**
 * Tipos de dominio puros (sin Android), testeables en JVM.
 * Los textos visibles llegan ya localizados (ES/EN) desde la capa i18n/demo;
 * el dominio solo maneja claves estables (SafetyGate, RejectReason, Stage).
 */

/** Caja de un componente en coordenadas normalizadas 0..1 sobre la foto de la placa. */
data class NormBox(val x: Float, val y: Float, val w: Float, val h: Float) {
    init {
        require(x in 0f..1f && y in 0f..1f && w in 0f..1f && h in 0f..1f) {
            "NormBox fuera de rango 0..1"
        }
    }
}

enum class ComponentKind {
    CAPACITOR, MOSFET, IC, FUSE, TRANSFORMER, OPTOCOUPLER, DIODE, RESISTOR, CONNECTOR, OTHER,
}

data class BoardComponent(
    val id: String,
    val designator: String,
    val kind: ComponentKind,
    val box: NormBox,
    val visibleDamage: String? = null,
)

enum class BoardType { SMPS, TV_MONITOR, INVERTER_APPLIANCE }

data class Hypothesis(
    val id: String,
    val title: String,
    val componentId: String,
    val confidence: Double,
    val rationale: String,
    val confirmedByMeasurement: Boolean = false,
    /** Que reemplazar o reparar si se confirma (sugerencia del modelo; vacio en el demo). */
    val fix: String = "",
)

enum class MeasurementKind { CONTINUITY, RESISTANCE, VOLTAGE_DC, DIODE }

/** Compuerta de seguridad a confirmar antes de medir. La UI muestra su advertencia localizada. */
enum class SafetyGate { DE_ENERGIZED, LIVE_VOLTAGE }

/**
 * Compuerta obligada por tipo de medicion: jamas depende del modelo de IA.
 * Continuidad, resistencia y diodo solo con el equipo desenergizado.
 */
fun safetyGateFor(kind: MeasurementKind): SafetyGate = when (kind) {
    MeasurementKind.VOLTAGE_DC -> SafetyGate.LIVE_VOLTAGE
    MeasurementKind.CONTINUITY,
    MeasurementKind.RESISTANCE,
    MeasurementKind.DIODE,
    -> SafetyGate.DE_ENERGIZED
}

/**
 * Donde apoyar una punta: un punto normalizado de la foto sobre un componente
 * (el medido u otro, como la masa del primario) y el terminal ("anodo", "+").
 * Un "?" en el terminal indica que la polaridad no se ve en la foto.
 */
data class ProbePoint(val componentId: String, val x: Float, val y: Float, val terminal: String)

/**
 * Guia de puntas de una medicion (sugerida por Muse Spark o fija en el demo).
 * Sin puntos cuando los terminales no se ven en la foto: queda la nota.
 */
data class ProbeGuide(val red: ProbePoint?, val black: ProbePoint?, val note: String = "") {
    val points: List<ProbePoint> get() = listOfNotNull(red, black)
    val uncertainPolarity: Boolean get() = points.any { '?' in it.terminal }
}

data class MeasurementRequest(
    val id: String,
    val componentId: String,
    val kind: MeasurementKind,
    val instruction: String,
    val normalDescription: String,
    val abnormalDescription: String,
    val gate: SafetyGate,
    val targetHypothesisId: String,
    val probes: ProbeGuide? = null,
)

enum class MeasurementOutcome { NORMAL, ABNORMAL, UNCLEAR }

data class MeasurementResult(
    val requestId: String,
    val outcome: MeasurementOutcome,
    val note: String = "",
    /** Componente medido (lo completa el motor); vacio en sesiones viejas. */
    val componentId: String = "",
)

/** Motivos de rechazo estables; la UI los convierte a texto localizado. */
enum class RejectReason { NO_PENDING_REQUEST, WRONG_REQUEST, SAFETY_NOT_CONFIRMED }

enum class Stage { INTAKE, MAPPING, HYPOTHESES, MEASURING, VERDICT, REPAIR, VERIFIED }

data class DiagnosisSession(
    val boardType: BoardType,
    val symptom: String,
    val components: List<BoardComponent> = emptyList(),
    val hypotheses: List<Hypothesis> = emptyList(),
    val pendingRequests: List<MeasurementRequest> = emptyList(),
    val safetyConfirmedForCurrent: Boolean = false,
    val results: List<MeasurementResult> = emptyList(),
    val stage: Stage = Stage.INTAKE,
    val verdictComponentId: String? = null,
    val replacement: String? = null,
) {
    val currentRequest: MeasurementRequest? get() = pendingRequests.firstOrNull()
}
