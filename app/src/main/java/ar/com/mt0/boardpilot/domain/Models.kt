package ar.com.mt0.boardpilot.domain

/**
 * Tipos de dominio puros (sin Android), testeables en JVM.
 * Los textos visibles llegan ya localizados (ES/EN) desde la capa i18n o Muse Spark;
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

/**
 * Tipo de placa: define las fallas tipicas y la estrategia de medicion que se le
 * dan a Muse Spark ([BoardPlaybooks]). UNKNOWN = el tecnico no lo eligio y lo
 * detecta Muse Spark en el diagnostico.
 */
enum class BoardType { SMPS, TV_MONITOR, INVERTER_APPLIANCE, CONTROL_BOARD, AUDIO_AMP, OTHER, UNKNOWN }

/**
 * Zona de la placa donde se mide; decide el aviso de seguridad junto con el
 * tipo de medicion. PRIMARY: lado de red (antes del transformador u
 * optoacoplador, el bus de continua y toda placa no aislada); SECONDARY: lado
 * aislado de baja tension; HIGH_VOLTAGE: alta tension especial (inverters de
 * CCFL, microondas, tubos). Ante la duda, PRIMARY.
 */
enum class HazardZone { PRIMARY, SECONDARY, HIGH_VOLTAGE }

data class Hypothesis(
    val id: String,
    val title: String,
    val componentId: String,
    val confidence: Double,
    val rationale: String,
    val confirmedByMeasurement: Boolean = false,
    /** Que reemplazar o reparar si se confirma (sugerencia del modelo; puede faltar). */
    val fix: String = "",
    /** Una medicion dio normal sobre ella: sigue en la lista, tachada. */
    val ruledOut: Boolean = false,
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
 * Mediciones pendientes en orden seguro, sin depender del modelo de IA: primero
 * las que se hacen sin tension. Las que piden enchufar van al final y se quitan
 * si la placa no se puede energizar: hay dano visible, una medicion sin tension
 * ya dio anormal o ninguna dio normal todavia.
 */
fun safeMeasurementQueue(
    pending: List<MeasurementRequest>,
    components: List<BoardComponent>,
    results: List<MeasurementResult>,
): List<MeasurementRequest> {
    val (deEnergized, live) = pending.partition { it.gate == SafetyGate.DE_ENERGIZED }
    val unsafe = components.any { !it.visibleDamage.isNullOrBlank() } ||
        // Un resultado viejo, sin compuerta, cuenta como el caso mas estricto.
        results.any { it.outcome == MeasurementOutcome.ABNORMAL && it.gate != SafetyGate.LIVE_VOLTAGE }
    val checked = deEnergized.isNotEmpty() ||
        results.any { it.outcome == MeasurementOutcome.NORMAL && it.gate == SafetyGate.DE_ENERGIZED }
    return if (unsafe || !checked) deEnergized else deEnergized + live
}

/**
 * Donde apoyar una punta: un punto normalizado de la foto sobre un componente
 * (el medido u otro, como la masa del primario) y el terminal ("anodo", "+").
 * Un "?" en el terminal indica que la polaridad no se ve en la foto.
 */
data class ProbePoint(val componentId: String, val x: Float, val y: Float, val terminal: String)

/**
 * Guia de puntas de una medicion (sugerida por Muse Spark).
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
    val zone: HazardZone = HazardZone.PRIMARY,
)

enum class MeasurementOutcome { NORMAL, ABNORMAL, UNCLEAR }

data class MeasurementResult(
    val requestId: String,
    val outcome: MeasurementOutcome,
    val note: String = "",
    /** Componente medido (lo completa el motor); vacio en sesiones viejas. */
    val componentId: String = "",
    /** Compuerta de la medicion (la completa el motor); null en sesiones viejas. */
    val gate: SafetyGate? = null,
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
    /** Fotos que sumo el tecnico al no aparecer la falla: que muestra cada una (puede faltar). */
    val extraPhotos: List<String> = emptyList(),
) {
    val currentRequest: MeasurementRequest? get() = pendingRequests.firstOrNull()
}

/** Fotos que se pueden sumar a una reparacion, ademas de la principal. */
const val MAX_EXTRA_PHOTOS = 4

/** Sesion de un proyecto nuevo: sin sintoma y con el tipo de placa por detectar. */
fun newDiagnosisSession(): DiagnosisSession =
    DiagnosisSession(boardType = BoardType.UNKNOWN, symptom = "")
