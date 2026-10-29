package ar.com.mt0.boardpilot.i18n

import ar.com.mt0.boardpilot.domain.BoardType
import ar.com.mt0.boardpilot.domain.ComponentKind
import ar.com.mt0.boardpilot.domain.HazardZone
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
    /** Descripción del ojito del campo de la clave (lectores de pantalla). */
    val showKey: String
    val hideKey: String
    val stageLabel: String
    val symptomLabel: String
    val hypothesesTitle: String
    val confirmedSuffix: String
    val startMeasuring: String
    val nextStepTitle: String
    val nextStepHint: String
    fun firstMeasurement(kind: String, designator: String): String
    val noMeasurementsLive: String
    val measuredNoVerdict: String
    val askMeasurements: String
    val askingMeasurements: String
    val askFailed: String
    val startOver: String
    val fixLabel: String
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
    val photoCrop: String
    val cropTitle: String
    val cropTurn: String
    val cropHint: String
    val cropRotate: String
    val cropReset: String
    val cropApply: String
    val cropCancel: String
    val mapFit: String
    val photoNone: String
    val photoReady: String
    val mapLiveSam: String
    val mappingBusy: String
    val samNoMatches: String
    val samFailed: String
    val liveMappingNote: String
    val symptomPlaceholder: String
    val diagnoseStart: String
    val symptomTitle: String
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
    val eggResistorHeader: String
    val eggResistorDuring: String
    val eggResistorAfter: String
    val eggDiodeHeader: String
    val eggDiodeDuring: String
    val eggDiodeAfter: String
    fun gateWarning(gate: SafetyGate, zone: HazardZone): String
    fun stageName(stage: Stage): String
    fun kindName(kind: MeasurementKind): String
    /** Nombre del tipo de componente; null en OTHER (no dice nada y no se muestra). */
    fun componentKindName(kind: ComponentKind): String?
    fun rejectMessage(reason: RejectReason): String

    // Guia visual de mediciones (lupa, multimetro, puntas, mapa de evidencia).
    fun measurementOf(n: Int, total: Int): String
    fun meterMode(kind: MeasurementKind): String
    val redJack: String
    val blackJack: String
    val redTag: String
    val blackTag: String
    val probesAiNote: String
    val probesLoading: String
    val probesLocate: String
    val probesHidden: String
    val diodeBothWays: String
    val mapMeasureTag: String
    val mapVerdictTag: String
    fun mapShowAll(total: Int): String
    fun mapMainOnly(count: Int): String

    // Tipo de placa y zona de riesgo de cada medicion.
    val boardTypeLabel: String
    val boardTypeHint: String
    fun boardTypeName(type: BoardType): String
    val zoneLabel: String
    fun zoneName(zone: HazardZone): String

    // La app necesita la clave y la foto para diagnosticar.
    val keyIntro: String
    val needKey: String
    val loadKey: String
    val needPhoto: String
    val needPhotoAndKey: String
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
    override val apiMissing = "Sin clave: BoardPilot no puede diagnosticar."
    override val apiSave = "Guardar clave en el dispositivo"
    override val apiDelete = "Borrar clave"
    override val showKey = "Mostrar la clave"
    override val hideKey = "Ocultar la clave"
    override val stageLabel = "Etapa"
    override val symptomLabel = "Síntoma"
    override val hypothesesTitle = "Hipótesis"
    override val confirmedSuffix = " (confirmado por medición)"
    override val startMeasuring = "Empezar mediciones guiadas"
    override val nextStepTitle = "Siguiente paso: medir"
    override val nextStepHint = "BoardPilot te guía una medición por vez: dónde poner las puntas, qué tenés que ver en el multímetro y la compuerta de seguridad antes de cada una."
    override fun firstMeasurement(kind: String, designator: String) = "Primera: $kind en $designator"
    override val noMeasurementsLive = "Muse Spark todavía no propuso mediciones. Pedíselas: te va a decir qué medir para confirmar o descartar estas hipótesis."
    override val measuredNoVerdict = "Las mediciones hechas no alcanzaron para un veredicto. Pedí las siguientes: Muse Spark las elige con estos resultados."
    override val askMeasurements = "Pedir mediciones a Muse Spark"
    override val askingMeasurements = "Muse Spark está eligiendo qué medir..."
    override val askFailed = "No se pudieron pedir las mediciones. Revisá la conexión y tu clave, y reintentá."
    override val startOver = "Empezar de nuevo"
    override val fixLabel = "Si se confirma"
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
    override val photoCrop = "Recortar / girar"
    override val cropTitle = "Encuadrá la placa"
    override val cropHint = "Arrastrá las esquinas o los bordes del recuadro; tocá adentro para moverlo. Girá la foto con dos dedos o con la barra."
    override val cropTurn = "Girar"
    override val cropRotate = "↻ 90°"
    override val cropReset = "Restablecer"
    override val cropApply = "Aplicar recorte"
    override val cropCancel = "Cancelar"
    override val mapFit = "Ver completo"
    override val photoNone = "Todavía no hay foto: sacala con la cámara o elegila de la galería."
    override val photoReady = "Foto lista"
    override val mapLiveSam = "Mapear con SAM 3.1 en vivo"
    override val mappingBusy = "Segmentando con SAM 3.1..."
    override val samNoMatches = "SAM no encontró componentes en esta foto. Probá más cerca, con buena luz y la placa de frente."
    override val samFailed = "No se pudo segmentar con SAM. Revisá la conexión y tu clave, y reintentá."
    override val liveMappingNote = "Mapeo en vivo listo. Revisá las cajas sobre la foto y generá las hipótesis con Muse Spark."
    override val symptomPlaceholder = "Ej.: no enciende, sin LED"
    override val diagnoseStart = "Iniciar diagnóstico"
    override val symptomTitle = "¿Qué le pasa a la placa?"
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
    override val eggResistorHeader = "SOBREPOTENCIA // R7 FUERA DE SERVICIO"
    override val eggResistorDuring = "40 W en una resistencia de 1/4 W..."
    override val eggResistorAfter = "Eso era R7. Calculá la potencia antes de energizar."
    override val eggDiodeHeader = "SOBRECORRIENTE // D3 FUERA DE SERVICIO"
    override val eggDiodeDuring = "2 A por un 1N4148 de 300 mA..."
    override val eggDiodeAfter = "Eso era D3. Quedó en corto: medilo en modo diodo."

    override fun gateWarning(gate: SafetyGate, zone: HazardZone): String = when (gate) {
        SafetyGate.DE_ENERGIZED -> when (zone) {
            HazardZone.PRIMARY ->
                "Equipo DESENCHUFADO y el capacitor grande del lado de red descargado (puede guardar más de 300 V). Nunca midas continuidad/resistencia con tensión aplicada."
            HazardZone.SECONDARY ->
                "Equipo DESENCHUFADO y capacitores descargados. Nunca midas continuidad/resistencia con tensión aplicada."
            HazardZone.HIGH_VOLTAGE ->
                "ZONA DE ALTA TENSIÓN: puede guardar carga mortal aun desenchufada. Descargala con una resistencia adecuada y verificá 0 V antes de tocar."
        }
        SafetyGate.LIVE_VOLTAGE -> when (zone) {
            HazardZone.PRIMARY ->
                "Medición CON TENSIÓN en el lado de red (más de 300 V): riesgo de descarga mortal. Una sola mano, puntas aisladas y sin tocar la placa."
            HazardZone.SECONDARY ->
                "Medición CON TENSIÓN en baja tensión, con el equipo enchufado: no toques el lado de red. Una sola mano y puntas aisladas."
            HazardZone.HIGH_VOLTAGE ->
                "Nunca se guían mediciones con tensión en zonas de alta tensión."
        }
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

    override fun componentKindName(kind: ComponentKind): String? = when (kind) {
        ComponentKind.CAPACITOR -> "capacitor"
        ComponentKind.MOSFET -> "MOSFET"
        ComponentKind.IC -> "integrado"
        ComponentKind.FUSE -> "fusible"
        ComponentKind.TRANSFORMER -> "transformador"
        ComponentKind.OPTOCOUPLER -> "optoacoplador"
        ComponentKind.DIODE -> "diodo"
        ComponentKind.RESISTOR -> "resistencia"
        ComponentKind.CONNECTOR -> "conector"
        ComponentKind.OTHER -> null
    }

    override fun measurementOf(n: Int, total: Int) = "Medición $n de $total"
    override fun meterMode(kind: MeasurementKind): String = when (kind) {
        MeasurementKind.CONTINUITY -> "Selector en continuidad (pitido)"
        MeasurementKind.RESISTANCE -> "Selector en resistencia (Ω)"
        MeasurementKind.VOLTAGE_DC -> "Selector en tensión continua (V DC)"
        MeasurementKind.DIODE -> "Selector en prueba de diodo"
    }
    override val redJack = "roja → VΩ"
    override val blackJack = "negra → COM"
    override val redTag = "R"
    override val blackTag = "N"
    override val probesAiNote = "Ubicación sugerida por IA: confirmala con la serigrafía."
    override val probesLoading = "Muse Spark está ubicando las puntas..."
    override val probesLocate = "Ubicar las puntas con Muse Spark"
    override val probesHidden = "Las patas no se ven en esta foto."
    override val diodeBothWays = "Si no ves la marca de polaridad, probá en los dos sentidos: en uno marca la caída (≈0,5–0,7 V; un LED ≈1–2 V) y en el otro OL."
    override val mapMeasureTag = "medir"
    override val mapVerdictTag = "veredicto"
    override fun mapShowAll(total: Int) = "Ver las $total cajas"
    override fun mapMainOnly(count: Int) = "Ver solo las $count principales"
    override val boardTypeLabel = "Tipo de placa"
    override val boardTypeHint = "Si no lo elegís, lo detecta Muse Spark al diagnosticar."
    override fun boardTypeName(type: BoardType): String = when (type) {
        BoardType.SMPS -> "Fuente conmutada o cargador"
        BoardType.TV_MONITOR -> "Placa de TV o monitor"
        BoardType.INVERTER_APPLIANCE -> "Inverter de motor o compresor"
        BoardType.CONTROL_BOARD -> "Placa de control de electrodoméstico"
        BoardType.AUDIO_AMP -> "Amplificador de audio"
        BoardType.OTHER -> "Otra placa"
        BoardType.UNKNOWN -> "Detectar con Muse Spark"
    }
    override val zoneLabel = "Zona"
    override fun zoneName(zone: HazardZone): String = when (zone) {
        HazardZone.PRIMARY -> "lado de red (primario)"
        HazardZone.SECONDARY -> "baja tensión (secundario)"
        HazardZone.HIGH_VOLTAGE -> "alta tensión"
    }
    override val keyIntro = "Para diagnosticar, BoardPilot usa tu propia clave de la Meta Model API (dev.meta.ai). Queda guardada solo en este teléfono."
    override val needKey = "Falta tu clave de la Meta Model API para diagnosticar."
    override val loadKey = "Cargar la clave"
    override val needPhoto = "Agregá la foto de la placa para empezar."
    override val needPhotoAndKey = "Para mapear hacen falta la foto de la placa y tu clave."

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
    override val apiMissing = "No key: BoardPilot cannot diagnose."
    override val apiSave = "Save key on this device"
    override val apiDelete = "Delete key"
    override val showKey = "Show key"
    override val hideKey = "Hide key"
    override val stageLabel = "Stage"
    override val symptomLabel = "Symptom"
    override val hypothesesTitle = "Hypotheses"
    override val confirmedSuffix = " (confirmed by measurement)"
    override val startMeasuring = "Start guided measurements"
    override val nextStepTitle = "Next step: measure"
    override val nextStepHint = "BoardPilot guides one measurement at a time: where to put the probes, what the meter should show and the safety gate before each one."
    override fun firstMeasurement(kind: String, designator: String) = "First: $kind on $designator"
    override val noMeasurementsLive = "Muse Spark has not proposed measurements yet. Ask for them: it will tell you what to measure to confirm or rule out these hypotheses."
    override val measuredNoVerdict = "The measurements so far were not enough for a verdict. Ask for the next ones: Muse Spark picks them using these results."
    override val askMeasurements = "Ask Muse Spark for measurements"
    override val askingMeasurements = "Muse Spark is choosing what to measure..."
    override val askFailed = "Could not get measurements. Check your connection and key, then retry."
    override val startOver = "Start over"
    override val fixLabel = "If confirmed"
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
    override val photoCrop = "Crop / rotate"
    override val cropTitle = "Frame the board"
    override val cropHint = "Drag the corners or edges of the box; drag inside to move it. Rotate the photo with two fingers or the slider."
    override val cropTurn = "Rotate"
    override val cropRotate = "↻ 90°"
    override val cropReset = "Reset"
    override val cropApply = "Apply crop"
    override val cropCancel = "Cancel"
    override val mapFit = "Fit view"
    override val photoNone = "No photo yet: take one with the camera or pick it from the gallery."
    override val photoReady = "Photo ready"
    override val mapLiveSam = "Map with live SAM 3.1"
    override val mappingBusy = "Segmenting with SAM 3.1..."
    override val samNoMatches = "SAM found no components in this photo. Try closer, with good light and the board straight on."
    override val samFailed = "SAM segmentation failed. Check your connection and key, then retry."
    override val liveMappingNote = "Live mapping is ready. Review the boxes on the photo, then generate hypotheses with Muse Spark."
    override val symptomPlaceholder = "E.g.: completely dead, no LED"
    override val diagnoseStart = "Start diagnosis"
    override val symptomTitle = "What is wrong with the board?"
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
    override val eggResistorHeader = "OVERPOWER // R7 OUT OF SERVICE"
    override val eggResistorDuring = "40 W into a 1/4 W resistor..."
    override val eggResistorAfter = "That was R7. Check the power rating before powering up."
    override val eggDiodeHeader = "OVERCURRENT // D3 OUT OF SERVICE"
    override val eggDiodeDuring = "2 A through a 300 mA 1N4148..."
    override val eggDiodeAfter = "That was D3. It failed short: check it in diode mode."

    override fun gateWarning(gate: SafetyGate, zone: HazardZone): String = when (gate) {
        SafetyGate.DE_ENERGIZED -> when (zone) {
            HazardZone.PRIMARY ->
                "Unit UNPLUGGED and the big mains-side capacitor discharged (it can hold more than 300 V). Never measure continuity/resistance with power applied."
            HazardZone.SECONDARY ->
                "Unit UNPLUGGED and capacitors discharged. Never measure continuity/resistance with power applied."
            HazardZone.HIGH_VOLTAGE ->
                "HIGH-VOLTAGE ZONE: it can hold a lethal charge even unplugged. Discharge it with a suitable resistor and check 0 V before touching."
        }
        SafetyGate.LIVE_VOLTAGE -> when (zone) {
            HazardZone.PRIMARY ->
                "LIVE measurement on the mains side (over 300 V): lethal shock risk. One hand, insulated probes and do not touch the board."
            HazardZone.SECONDARY ->
                "LIVE low-voltage measurement with the unit plugged in: do not touch the mains side. One hand and insulated probes."
            HazardZone.HIGH_VOLTAGE ->
                "Live measurements are never guided in high-voltage zones."
        }
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

    override fun componentKindName(kind: ComponentKind): String? = when (kind) {
        ComponentKind.CAPACITOR -> "capacitor"
        ComponentKind.MOSFET -> "MOSFET"
        ComponentKind.IC -> "IC"
        ComponentKind.FUSE -> "fuse"
        ComponentKind.TRANSFORMER -> "transformer"
        ComponentKind.OPTOCOUPLER -> "optocoupler"
        ComponentKind.DIODE -> "diode"
        ComponentKind.RESISTOR -> "resistor"
        ComponentKind.CONNECTOR -> "connector"
        ComponentKind.OTHER -> null
    }

    override fun measurementOf(n: Int, total: Int) = "Measurement $n of $total"
    override fun meterMode(kind: MeasurementKind): String = when (kind) {
        MeasurementKind.CONTINUITY -> "Dial on continuity (beep)"
        MeasurementKind.RESISTANCE -> "Dial on resistance (Ω)"
        MeasurementKind.VOLTAGE_DC -> "Dial on DC voltage (V DC)"
        MeasurementKind.DIODE -> "Dial on diode test"
    }
    override val redJack = "red → VΩ"
    override val blackJack = "black → COM"
    override val redTag = "R"
    override val blackTag = "B"
    override val probesAiNote = "AI-suggested location: confirm it with the silkscreen."
    override val probesLoading = "Muse Spark is locating the probe points..."
    override val probesLocate = "Locate the probes with Muse Spark"
    override val probesHidden = "The leads are not visible in this photo."
    override val diodeBothWays = "If you can't see the polarity mark, try both ways: one shows the drop (≈0.5–0.7 V; an LED ≈1–2 V) and the other OL."
    override val mapMeasureTag = "measure"
    override val mapVerdictTag = "verdict"
    override fun mapShowAll(total: Int) = "Show all $total boxes"
    override fun mapMainOnly(count: Int) = "Show only the $count main ones"
    override val boardTypeLabel = "Board type"
    override val boardTypeHint = "If you don't choose one, Muse Spark detects it when diagnosing."
    override fun boardTypeName(type: BoardType): String = when (type) {
        BoardType.SMPS -> "Switch-mode power supply or charger"
        BoardType.TV_MONITOR -> "TV or monitor board"
        BoardType.INVERTER_APPLIANCE -> "Motor or compressor inverter"
        BoardType.CONTROL_BOARD -> "Appliance control board"
        BoardType.AUDIO_AMP -> "Audio amplifier"
        BoardType.OTHER -> "Other board"
        BoardType.UNKNOWN -> "Detect with Muse Spark"
    }
    override val zoneLabel = "Zone"
    override fun zoneName(zone: HazardZone): String = when (zone) {
        HazardZone.PRIMARY -> "mains side (primary)"
        HazardZone.SECONDARY -> "low voltage (secondary)"
        HazardZone.HIGH_VOLTAGE -> "high voltage"
    }
    override val keyIntro = "To diagnose, BoardPilot uses your own Meta Model API key (dev.meta.ai). It is stored only on this phone."
    override val needKey = "Your Meta Model API key is needed to diagnose."
    override val loadKey = "Add the key"
    override val needPhoto = "Add a photo of the board to start."
    override val needPhotoAndKey = "Mapping needs the board photo and your key."

    override fun rejectMessage(reason: RejectReason): String = when (reason) {
        RejectReason.NO_PENDING_REQUEST -> "No measurement is pending."
        RejectReason.WRONG_REQUEST -> "That answer does not match the requested measurement."
        RejectReason.SAFETY_NOT_CONFIRMED -> "Confirm the safety gate first."
    }
}
