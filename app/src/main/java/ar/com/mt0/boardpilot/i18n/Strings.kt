package ar.com.mt0.boardpilot.i18n

import ar.com.mt0.boardpilot.domain.MeasurementKind
import ar.com.mt0.boardpilot.domain.RejectReason
import ar.com.mt0.boardpilot.domain.SafetyGate
import ar.com.mt0.boardpilot.domain.Stage

enum class AppLanguage { ES, EN }

enum class LanguageMode { AUTO, ES, EN }

/** AUTO: español si el sistema está en español; en cualquier otro caso, inglés. */
fun resolveLanguage(mode: LanguageMode, systemLanguageTag: String): AppLanguage = when (mode) {
    LanguageMode.ES -> AppLanguage.ES
    LanguageMode.EN -> AppLanguage.EN
    LanguageMode.AUTO ->
        if (systemLanguageTag.lowercase().startsWith("es")) AppLanguage.ES else AppLanguage.EN
}

/**
 * La configuracion inicial se considera completa si ya fue marcada como tal,
 * o (compatibilidad) si ya hay clave guardada y eleccion explicita de idioma.
 */
fun isSetupComplete(
    storedCompleted: Boolean,
    hasApiKey: Boolean,
    hasLanguageChoice: Boolean,
): Boolean = storedCompleted || (hasApiKey && hasLanguageChoice)

interface AppStrings {
    val appTagline: String
    val safetyIntro: String
    val languageTitle: String
    val languageAuto: String
    val themeTitle: String
    val themeAuto: String
    val themeLight: String
    val themeDark: String
    val reasoningTitle: String
    val reasoningHint: String
    val reasoningAuto: String
    val reasoningMinimal: String
    val reasoningLow: String
    val reasoningMedium: String
    val reasoningHigh: String
    val reasoningMax: String
    val apiTitle: String
    val apiLoaded: String
    val apiMissing: String
    val apiSave: String
    val apiDelete: String
    val demoTitle: String
    val demoStart: String
    val stageLabel: String
    val symptomLabel: String
    val hypothesesTitle: String
    val confirmedSuffix: String
    val mapComponents: String
    val generateHypotheses: String
    val startMeasuring: String
    val needMoreEvidence: String
    val restartDemo: String
    val measurementTitle: String
    val normalLabel: String
    val abnormalLabel: String
    val unclearLabel: String
    val normalDescLabel: String
    val abnormalDescLabel: String
    val confirmSafety: String
    val verdictPrefix: String
    val alreadyReplaced: String
    val verifyQuestion: String
    val yesOn: String
    val notOn: String
    val verifiedOk: String
    val backHome: String
    val settingsTitle: String
    val firstSetupTitle: String
    val firstSetupIntro: String
    val continueWithoutKey: String
    val aboutTitle: String
    val nameLabel: String
    val authorLabel: String
    val versionLabel: String
    val packageLabel: String
    val permissionsTitle: String
    val permissionInternet: String
    val permissionCamera: String
    val permissionsNone: String
    val permissionGranted: String
    val permissionNotGranted: String
    val cameraPermissionDenied: String
    val cameraPermissionPermanent: String
    val cameraUnavailable: String
    val openAppSettings: String
    val licenseLabel: String
    val photoTitle: String
    val photoGallery: String
    val photoCamera: String
    val photoRemove: String
    val photoNone: String
    val photoReady: String
    val mapLiveSam: String
    val mappingBusy: String
    val samNoMatches: String
    val samFailed: String
    val liveMappingNote: String
    val symptomPlaceholder: String
    val diagnoseStart: String
    val projectsTitle: String
    val newProject: String
    val newProjectTitle: String
    val projectsEmpty: String
    val statusInProgress: String
    val statusWaiting: String
    val statusRepaired: String
    val waitingMark: String
    val waitingResume: String
    val waitingNoteLabel: String
    val swipeDelete: String
    val swipeHint: String
    val undo: String
    fun projectDeleted(title: String): String
    val projectNameLabel: String
    val backToProjects: String
    val renameSave: String
    fun missingPart(note: String): String
    val justNow: String
    val yesterday: String
    fun minutesAgo(minutes: Long): String
    fun hoursAgo(hours: Long): String
    /** Fecha corta de la lista; [year] nulo si es el año en curso. */
    fun shortDate(day: Int, month: Int, year: Int?): String
    val referenceTitle: String
    val referenceGenerate: String
    val referenceBusy: String
    val referenceFailed: String
    val referenceNote: String
    val sparkGenerate: String
    val sparkBusy: String
    val sparkFailed: String
    val sparkNoData: String
    fun liveReplacement(designator: String): String
    val eggHeader: String
    val eggCharging: String
    val eggAftermath: String
    val eggPsu: String
    fun gateWarning(gate: SafetyGate): String
    fun stageName(stage: Stage): String
    fun kindName(kind: MeasurementKind): String
    fun rejectMessage(reason: RejectReason): String
}

fun appStrings(language: AppLanguage): AppStrings = when (language) {
    AppLanguage.ES -> EsStrings
    AppLanguage.EN -> EnStrings
}

object EsStrings : AppStrings {
    override val appTagline = "Copiloto de reparación electrónica a nivel componente"
    override val safetyIntro = "⚠ Seguridad: este copiloto guía; el técnico decide. Leé cada advertencia antes de medir."
    override val languageTitle = "Idioma / Language"
    override val languageAuto = "Auto"
    override val themeTitle = "Tema"
    override val themeAuto = "Auto"
    override val themeLight = "Claro"
    override val themeDark = "Oscuro"
    override val reasoningTitle = "Razonamiento (Muse Spark)"
    override val reasoningHint = "Más esfuerzo razona más en diagnósticos difíciles y gasta más tokens; Auto lo decide la API."
    override val reasoningAuto = "Auto"
    override val reasoningMinimal = "Mínimo"
    override val reasoningLow = "Bajo"
    override val reasoningMedium = "Medio"
    override val reasoningHigh = "Alto"
    override val reasoningMax = "Máximo"
    override val apiTitle = "Meta Model API (tu clave, BYOK)"
    override val apiLoaded = "Clave cargada en este dispositivo. Solo sale hacia api.meta.ai."
    override val apiMissing = "Sin clave: la app funciona en modo demo offline."
    override val apiSave = "Guardar clave en el dispositivo"
    override val apiDelete = "Borrar clave"
    override val demoTitle = "Demo offline — Caso 1: fuente SMPS"
    override val demoStart = "Iniciar diagnóstico demo"
    override val stageLabel = "Etapa"
    override val symptomLabel = "Síntoma"
    override val hypothesesTitle = "Hipótesis"
    override val confirmedSuffix = " (confirmado por medición)"
    override val mapComponents = "Mapear componentes (demo SAM)"
    override val generateHypotheses = "Generar hipótesis (demo Muse Spark)"
    override val startMeasuring = "Empezar mediciones guiadas"
    override val needMoreEvidence = "Sin mediciones pendientes y sin evidencia suficiente: en vivo se pediría una foto macro u otra medición."
    override val restartDemo = "Reiniciar demo"
    override val measurementTitle = "Medición"
    override val normalLabel = "Normal"
    override val abnormalLabel = "Anormal"
    override val unclearLabel = "No claro"
    override val normalDescLabel = "Normal"
    override val abnormalDescLabel = "Anormal"
    override val confirmSafety = "Confirmo la seguridad para medir"
    override val verdictPrefix = "Veredicto: reemplazar"
    override val alreadyReplaced = "Ya reemplacé el componente"
    override val verifyQuestion = "Verificación: energizá el equipo con precaución. ¿Encendió?"
    override val yesOn = "Sí, encendió"
    override val notOn = "No encendió"
    override val verifiedOk = "✅ Reparación verificada: el equipo volvió a funcionar."
    override val backHome = "Volver al inicio"
    override val settingsTitle = "Configuración"
    override val firstSetupTitle = "Primera configuración"
    override val firstSetupIntro = "Elegí el idioma y cargá tu clave de Meta Model API. Después, esto queda en la ruedita de arriba."
    override val continueWithoutKey = "Continuar sin clave (modo demo)"
    override val aboutTitle = "Acerca de BoardPilot"
    override val nameLabel = "Nombre"
    override val authorLabel = "Autor"
    override val versionLabel = "Versión"
    override val packageLabel = "Paquete"
    override val permissionsTitle = "Permisos de la app"
    override val permissionInternet = "Internet — para comunicarse con Meta Model API"
    override val permissionCamera = "Cámara — para fotografiar la placa a diagnosticar"
    override val permissionsNone = "Sin permisos especiales declarados"
    override val permissionGranted = "✓ Otorgado"
    override val permissionNotGranted = "✗ No otorgado"
    override val cameraPermissionDenied = "Sin permiso de cámara: no se abrió la cámara. Tocá «Cámara» de nuevo para autorizarla, o usá «Galería»."
    override val cameraPermissionPermanent = "El permiso de cámara quedó denegado. Para activarlo, otorgalo en los ajustes de la app; mientras tanto podés usar «Galería»."
    override val cameraUnavailable = "No se pudo abrir la cámara en este dispositivo. Probá con «Galería»."
    override val openAppSettings = "Abrir ajustes de la app"
    override val licenseLabel = "Licencia"
    override val photoTitle = "Foto de la placa"
    override val photoGallery = "Galería"
    override val photoCamera = "Cámara"
    override val photoRemove = "Quitar foto"
    override val photoNone = "Sin foto: se usa el esquema del demo."
    override val photoReady = "Foto lista"
    override val mapLiveSam = "Mapear con SAM 3.1 en vivo"
    override val mappingBusy = "Segmentando con SAM 3.1..."
    override val samNoMatches = "SAM no encontró componentes en esta foto. Probá más cerca, con buena luz y la placa de frente."
    override val samFailed = "No se pudo segmentar con SAM. Revisá la conexión y tu clave, y reintentá."
    override val liveMappingNote = "Mapeo en vivo listo. Revisá las cajas sobre la foto y generá las hipótesis con Muse Spark."
    override val symptomPlaceholder = "Ej.: no enciende, sin LED"
    override val diagnoseStart = "Iniciar diagnóstico"
    override val projectsTitle = "Mis reparaciones"
    override val newProject = "Nueva reparación"
    override val newProjectTitle = "Nueva reparación"
    override val projectsEmpty = "Todavía no hay reparaciones guardadas. Creá la primera."
    override val statusInProgress = "▶ En curso"
    override val statusWaiting = "⏸ En espera"
    override val statusRepaired = "✅ Reparada"
    override val waitingMark = "Pausar: falta un repuesto"
    override val waitingResume = "Reanudar reparación"
    override val waitingNoteLabel = "Repuesto que falta"
    override val swipeDelete = "🗑 Borrar"
    override val swipeHint = "Deslizá una reparación hacia un costado para borrarla."
    override val undo = "Deshacer"
    override fun projectDeleted(title: String) = "Se borró «$title»"
    override val projectNameLabel = "Nombre del proyecto"
    override val backToProjects = "← Mis reparaciones"
    override val renameSave = "Guardar nombre"
    override fun missingPart(note: String) = "falta: $note"
    override val justNow = "recién"
    override val yesterday = "ayer"
    override fun minutesAgo(minutes: Long) = "hace $minutes min"
    override fun hoursAgo(hours: Long) = "hace $hours h"
    override fun shortDate(day: Int, month: Int, year: Int?): String {
        val name = listOf("ene", "feb", "mar", "abr", "may", "jun",
            "jul", "ago", "sep", "oct", "nov", "dic")[month - 1]
        return "$day $name" + (year?.let { " $it" } ?: "")
    }
    override val referenceTitle = "Referencia de reparación"
    override val referenceGenerate = "Ver referencia de reparación (Muse Image)"
    override val referenceBusy = "Generando referencia con Muse Image..."
    override val referenceFailed = "No se pudo generar la referencia. Revisá la conexión y tu clave, y reintentá."
    override val referenceNote = "Imagen de referencia generada por IA: aspecto esperado tras la reparación. No es una foto de tu placa ni reemplaza la verificación."
    override val sparkGenerate = "Generar hipótesis (Muse Spark en vivo)"
    override val sparkBusy = "Razonando con Muse Spark..."
    override val sparkFailed = "No se pudieron generar hipótesis en vivo. Revisá la conexión y tu clave, y reintentá."
    override val sparkNoData = "Muse Spark no devolvió hipótesis utilizables para esta foto. Probá una foto más cerca, de frente y con buena luz."
    override fun liveReplacement(designator: String): String =
        "Reemplazar $designator por uno equivalente (mismo tipo y valores nominales). " +
            "Verificar los componentes asociados antes de energizar."
    override val eggHeader = "SOBRETENSIÓN // C12 FUERA DE SERVICIO"
    override val eggCharging = "24 V en un capacitor de 16 V..."
    override val eggAftermath = "Eso era C12. Hay que medir antes de energizar."
    override val eggPsu = "FUENTE"

    override fun gateWarning(gate: SafetyGate): String = when (gate) {
        SafetyGate.DE_ENERGIZED ->
            "Equipo DESENCHUFADO y capacitores descargados. Nunca midas continuidad/resistencia con tensión aplicada."
        SafetyGate.LIVE_VOLTAGE ->
            "Medición CON TENSIÓN: riesgo de descarga eléctrica. Trabajá con una sola mano y puntas aisladas."
    }

    override fun stageName(stage: Stage): String = when (stage) {
        Stage.INTAKE -> "Ingreso"
        Stage.MAPPING -> "Mapeo de placa"
        Stage.HYPOTHESES -> "Hipótesis"
        Stage.MEASURING -> "Mediciones guiadas"
        Stage.VERDICT -> "Veredicto"
        Stage.REPAIR -> "Reparación"
        Stage.VERIFIED -> "Verificado"
    }

    override fun kindName(kind: MeasurementKind): String = when (kind) {
        MeasurementKind.CONTINUITY -> "continuidad"
        MeasurementKind.RESISTANCE -> "resistencia"
        MeasurementKind.VOLTAGE_DC -> "tensión DC"
        MeasurementKind.DIODE -> "diodo"
    }

    override fun rejectMessage(reason: RejectReason): String = when (reason) {
        RejectReason.NO_PENDING_REQUEST -> "No queda ninguna medición pendiente."
        RejectReason.WRONG_REQUEST -> "La respuesta no corresponde a la medición pedida."
        RejectReason.SAFETY_NOT_CONFIRMED -> "Primero confirmá la compuerta de seguridad."
    }
}

object EnStrings : AppStrings {
    override val appTagline = "Multimodal copilot for component-level board repair"
    override val safetyIntro = "⚠ Safety: this copilot guides, the technician decides. Read every warning before measuring."
    override val languageTitle = "Language / Idioma"
    override val languageAuto = "Auto"
    override val themeTitle = "Theme"
    override val themeAuto = "Auto"
    override val themeLight = "Light"
    override val themeDark = "Dark"
    override val reasoningTitle = "Reasoning (Muse Spark)"
    override val reasoningHint = "More effort reasons harder on tough diagnoses and spends more tokens; Auto lets the API decide."
    override val reasoningAuto = "Auto"
    override val reasoningMinimal = "Minimal"
    override val reasoningLow = "Low"
    override val reasoningMedium = "Medium"
    override val reasoningHigh = "High"
    override val reasoningMax = "Max"
    override val apiTitle = "Meta Model API (your key, BYOK)"
    override val apiLoaded = "Key stored on this device. It only goes to api.meta.ai."
    override val apiMissing = "No key: the app runs in offline demo mode."
    override val apiSave = "Save key on this device"
    override val apiDelete = "Delete key"
    override val demoTitle = "Offline demo — Case 1: SMPS power supply"
    override val demoStart = "Start demo diagnosis"
    override val stageLabel = "Stage"
    override val symptomLabel = "Symptom"
    override val hypothesesTitle = "Hypotheses"
    override val confirmedSuffix = " (confirmed by measurement)"
    override val mapComponents = "Map components (SAM demo)"
    override val generateHypotheses = "Generate hypotheses (Muse Spark demo)"
    override val startMeasuring = "Start guided measurements"
    override val needMoreEvidence = "No measurements left and not enough evidence: live mode would ask for a macro photo or another measurement."
    override val restartDemo = "Restart demo"
    override val measurementTitle = "Measurement"
    override val normalLabel = "Normal"
    override val abnormalLabel = "Abnormal"
    override val unclearLabel = "Unclear"
    override val normalDescLabel = "Normal"
    override val abnormalDescLabel = "Abnormal"
    override val confirmSafety = "I confirm it is safe to measure"
    override val verdictPrefix = "Verdict: replace"
    override val alreadyReplaced = "I already replaced the component"
    override val verifyQuestion = "Verification: power the unit on with care. Did it turn on?"
    override val yesOn = "Yes, it works"
    override val notOn = "No, still dead"
    override val verifiedOk = "✅ Repair verified: the unit is working again."
    override val backHome = "Back to start"
    override val settingsTitle = "Settings"
    override val firstSetupTitle = "First-time setup"
    override val firstSetupIntro = "Pick your language and add your Meta Model API key. Later, all of this lives under the gear icon above."
    override val continueWithoutKey = "Continue without a key (demo mode)"
    override val aboutTitle = "About BoardPilot"
    override val nameLabel = "Name"
    override val authorLabel = "Author"
    override val versionLabel = "Version"
    override val packageLabel = "Package"
    override val permissionsTitle = "App permissions"
    override val permissionInternet = "Internet — to talk to the Meta Model API"
    override val permissionCamera = "Camera — to photograph the board being diagnosed"
    override val permissionsNone = "No special permissions declared"
    override val permissionGranted = "✓ Granted"
    override val permissionNotGranted = "✗ Not granted"
    override val cameraPermissionDenied = "No camera permission: the camera was not opened. Tap \"Camera\" again to allow it, or use \"Gallery\"."
    override val cameraPermissionPermanent = "Camera permission is denied. To enable it, grant it in the app settings; in the meantime you can use \"Gallery\"."
    override val cameraUnavailable = "The camera could not be opened on this device. Try \"Gallery\"."
    override val openAppSettings = "Open app settings"
    override val licenseLabel = "License"
    override val photoTitle = "Board photo"
    override val photoGallery = "Gallery"
    override val photoCamera = "Camera"
    override val photoRemove = "Remove photo"
    override val photoNone = "No photo: the demo schematic will be used."
    override val photoReady = "Photo ready"
    override val mapLiveSam = "Map with live SAM 3.1"
    override val mappingBusy = "Segmenting with SAM 3.1..."
    override val samNoMatches = "SAM found no components in this photo. Try closer, with good light and the board straight on."
    override val samFailed = "SAM segmentation failed. Check your connection and key, then retry."
    override val liveMappingNote = "Live mapping is ready. Review the boxes on the photo, then generate hypotheses with Muse Spark."
    override val symptomPlaceholder = "E.g.: completely dead, no LED"
    override val diagnoseStart = "Start diagnosis"
    override val projectsTitle = "My repairs"
    override val newProject = "New repair"
    override val newProjectTitle = "New repair"
    override val projectsEmpty = "No saved repairs yet. Create the first one."
    override val statusInProgress = "▶ In progress"
    override val statusWaiting = "⏸ Waiting"
    override val statusRepaired = "✅ Repaired"
    override val waitingMark = "Pause: waiting for a part"
    override val waitingResume = "Resume repair"
    override val waitingNoteLabel = "Part needed"
    override val swipeDelete = "🗑 Delete"
    override val swipeHint = "Swipe a repair sideways to delete it."
    override val undo = "Undo"
    override fun projectDeleted(title: String) = "Deleted “$title”"
    override val projectNameLabel = "Project name"
    override val backToProjects = "← My repairs"
    override val renameSave = "Save name"
    override fun missingPart(note: String) = "needs: $note"
    override val justNow = "just now"
    override val yesterday = "yesterday"
    override fun minutesAgo(minutes: Long) = "$minutes min ago"
    override fun hoursAgo(hours: Long) = "$hours h ago"
    override fun shortDate(day: Int, month: Int, year: Int?): String {
        val name = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun",
            "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")[month - 1]
        return "$name $day" + (year?.let { ", $it" } ?: "")
    }
    override val referenceTitle = "Repair reference"
    override val referenceGenerate = "View repair reference (Muse Image)"
    override val referenceBusy = "Generating reference with Muse Image..."
    override val referenceFailed = "The reference could not be generated. Check your connection and key, then retry."
    override val referenceNote = "AI-generated reference image: expected look after the repair. It is not a photo of your board and does not replace verification."
    override val sparkGenerate = "Generate hypotheses (live Muse Spark)"
    override val sparkBusy = "Reasoning with Muse Spark..."
    override val sparkFailed = "Live hypotheses could not be generated. Check your connection and key, then retry."
    override val sparkNoData = "Muse Spark returned no usable hypotheses for this photo. Try a closer, straight-on photo with good light."
    override fun liveReplacement(designator: String): String =
        "Replace $designator with an equivalent part (same type and ratings). " +
            "Check the surrounding components before powering up."
    override val eggHeader = "OVERVOLTAGE // C12 OUT OF SERVICE"
    override val eggCharging = "24 V into a 16 V capacitor..."
    override val eggAftermath = "That was C12. Always measure before powering up."
    override val eggPsu = "PSU"

    override fun gateWarning(gate: SafetyGate): String = when (gate) {
        SafetyGate.DE_ENERGIZED ->
            "Unit UNPLUGGED and capacitors discharged. Never measure continuity/resistance with power applied."
        SafetyGate.LIVE_VOLTAGE ->
            "LIVE measurement: electric shock risk. Work one-handed with insulated probes."
    }

    override fun stageName(stage: Stage): String = when (stage) {
        Stage.INTAKE -> "Intake"
        Stage.MAPPING -> "Board mapping"
        Stage.HYPOTHESES -> "Hypotheses"
        Stage.MEASURING -> "Guided measurements"
        Stage.VERDICT -> "Verdict"
        Stage.REPAIR -> "Repair"
        Stage.VERIFIED -> "Verified"
    }

    override fun kindName(kind: MeasurementKind): String = when (kind) {
        MeasurementKind.CONTINUITY -> "continuity"
        MeasurementKind.RESISTANCE -> "resistance"
        MeasurementKind.VOLTAGE_DC -> "DC voltage"
        MeasurementKind.DIODE -> "diode"
    }

    override fun rejectMessage(reason: RejectReason): String = when (reason) {
        RejectReason.NO_PENDING_REQUEST -> "No measurement is pending."
        RejectReason.WRONG_REQUEST -> "That answer does not match the requested measurement."
        RejectReason.SAFETY_NOT_CONFIRMED -> "Confirm the safety gate first."
    }
}
