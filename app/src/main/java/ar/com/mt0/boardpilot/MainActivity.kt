package ar.com.mt0.boardpilot

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.withStyle
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ar.com.mt0.boardpilot.api.MetaImageClient
import ar.com.mt0.boardpilot.api.MetaSamClient
import ar.com.mt0.boardpilot.api.MetaSparkClient
import ar.com.mt0.boardpilot.demo.DemoCaseSmps
import ar.com.mt0.boardpilot.domain.BoardComponent
import ar.com.mt0.boardpilot.domain.CameraAction
import ar.com.mt0.boardpilot.domain.CameraAfterRequest
import ar.com.mt0.boardpilot.domain.ComponentKind
import ar.com.mt0.boardpilot.domain.PermissionGrant
import ar.com.mt0.boardpilot.domain.ReasoningEffort
import ar.com.mt0.boardpilot.domain.cameraAction
import ar.com.mt0.boardpilot.domain.cameraAfterRequest
import ar.com.mt0.boardpilot.domain.permissionGrant
import ar.com.mt0.boardpilot.domain.DiagnosisEngine
import ar.com.mt0.boardpilot.domain.DiagnosisSession
import ar.com.mt0.boardpilot.domain.MeasurementOutcome
import ar.com.mt0.boardpilot.domain.MeasurementResult
import ar.com.mt0.boardpilot.domain.SamBox
import ar.com.mt0.boardpilot.domain.SamOutputParser
import ar.com.mt0.boardpilot.domain.SessionCodec
import ar.com.mt0.boardpilot.domain.SparkDamageParser
import ar.com.mt0.boardpilot.domain.SparkOutputParser
import ar.com.mt0.boardpilot.domain.Stage
import ar.com.mt0.boardpilot.domain.ThemeMode
import ar.com.mt0.boardpilot.domain.TitleTapDetector
import ar.com.mt0.boardpilot.domain.resolveDarkTheme
import ar.com.mt0.boardpilot.i18n.AppLanguage
import ar.com.mt0.boardpilot.i18n.AppStrings
import ar.com.mt0.boardpilot.i18n.LanguageMode
import ar.com.mt0.boardpilot.i18n.appStrings
import ar.com.mt0.boardpilot.i18n.isSetupComplete
import ar.com.mt0.boardpilot.i18n.resolveLanguage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

private const val PREFS = "boardpilot_prefs"
private const val PREF_API_KEY = "model_api_key"
private const val PREF_LANGUAGE = "language_mode"
private const val PREF_SETUP_DONE = "setup_completed"
private const val PREF_THEME = "theme_mode"
private const val PREF_EFFORT = "reasoning_effort"
private const val PREF_SESSION = "diagnosis_session"
private const val PREF_STARTED = "session_started"
private const val PREF_LIVE_MAPPING = "live_mapping"
private const val BOARD_PHOTO_FILE = "board_photo.jpg"
private const val REFERENCE_PHOTO_FILE = "reference_photo.jpg"
private const val PREF_REFERENCE_FOR = "reference_component"
private const val APP_AUTHOR = "Mauricio Tagliaferro"
private const val APP_LICENSE = "GNU GPL-3.0-or-later"

/** Aviso de la tarjeta de foto; se resuelve a texto con el idioma vigente. */
private enum class PhotoNotice { CAMERA_DENIED, CAMERA_PERMANENT, CAMERA_UNAVAILABLE }

/** Identidad bordo + ambar (el rojo vivo queda para errores, el violeta se descarto). */
private val BoardPilotLightColors = lightColorScheme(
    primary = Color(0xFF7A1220),
    onPrimary = Color.White,
    secondary = Color(0xFF9A6200),
    background = Color(0xFFF7F3F2),
    surface = Color.White,
    surfaceVariant = Color(0xFFF3E7E3),
    onBackground = Color(0xFF211114),
    onSurface = Color(0xFF211114),
    onSurfaceVariant = Color(0xFF524144),
    outline = Color(0xFF857375),
)

private val BoardPilotDarkColors = darkColorScheme(
    primary = Color(0xFFE45364),
    onPrimary = Color.White,
    secondary = Color(0xFFFFBA7A),
    onSecondary = Color(0xFF472A00),
    background = Color(0xFF171011),
    onBackground = Color(0xFFF1E4E1),
    surface = Color(0xFF21181A),
    onSurface = Color(0xFFF1E4E1),
    surfaceVariant = Color(0xFF342124),
    onSurfaceVariant = Color(0xFFD8C2BE),
    outline = Color(0xFFA08C89),
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { BoardPilotRoot() }
        // Despues de setContent: antes de eso la DecorView aun puede ser null (crash en S25).
        enableImmersiveMode()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) enableImmersiveMode()
    }

    /** Controles de Android minimizados: barras ocultas, vuelven con un gesto desde el borde. */
    private fun enableImmersiveMode() {
        window.setDecorFitsSystemWindows(false)
        val decor = window.decorView
        decor.post {
            decor.windowInsetsController?.let { controller ->
                controller.hide(android.view.WindowInsets.Type.systemBars())
                controller.systemBarsBehavior =
                    android.view.WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        }
    }
}

/** Raiz: resuelve claro/oscuro (auto sigue al sistema) y aplica el tema bordo. */
@Composable
fun BoardPilotRoot() {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences(PREFS, Context.MODE_PRIVATE) }
    var themeMode by remember {
        mutableStateOf(
            runCatching {
                ThemeMode.valueOf(prefs.getString(PREF_THEME, null) ?: "AUTO")
            }.getOrDefault(ThemeMode.AUTO),
        )
    }
    val darkTheme = resolveDarkTheme(themeMode, isSystemInDarkTheme())
    MaterialTheme(
        colorScheme = if (darkTheme) BoardPilotDarkColors else BoardPilotLightColors,
    ) {
        Surface(Modifier.fillMaxSize()) {
            BoardPilotApp(themeMode) { selected ->
                themeMode = selected
                prefs.edit().putString(PREF_THEME, selected.name).apply()
            }
        }
    }
}

@Composable
fun BoardPilotApp(
    themeMode: ThemeMode,
    onThemeModeSelected: (ThemeMode) -> Unit,
) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences(PREFS, Context.MODE_PRIVATE) }
    val systemLanguage = LocalConfiguration.current.locales[0]?.language.orEmpty()
    var mode by remember {
        mutableStateOf(
            runCatching {
                LanguageMode.valueOf(prefs.getString(PREF_LANGUAGE, null) ?: "AUTO")
            }.getOrDefault(LanguageMode.AUTO),
        )
    }
    val language = resolveLanguage(mode, systemLanguage)
    val strings = appStrings(language)
    var reasoningEffort by remember {
        mutableStateOf(ReasoningEffort.fromStored(prefs.getString(PREF_EFFORT, null)))
    }

    var hasApiKey by remember {
        mutableStateOf(!prefs.getString(PREF_API_KEY, null).isNullOrBlank())
    }
    var setupCompleted by remember {
        mutableStateOf(
            isSetupComplete(
                storedCompleted = prefs.getBoolean(PREF_SETUP_DONE, false),
                hasApiKey = !prefs.getString(PREF_API_KEY, null).isNullOrBlank(),
                hasLanguageChoice = prefs.contains(PREF_LANGUAGE),
            ),
        )
    }
    var showSettings by rememberSaveable { mutableStateOf(false) }
    var showAbout by rememberSaveable { mutableStateOf(false) }
    var showEasterEgg by rememberSaveable { mutableStateOf(false) }

    fun completeSetup() {
        if (!setupCompleted) {
            setupCompleted = true
            prefs.edit().putBoolean(PREF_SETUP_DONE, true).apply()
        }
    }

    var runId by remember { mutableStateOf(0) }
    var restoredSession by remember {
        mutableStateOf(
            prefs.getString(PREF_SESSION, null)?.let {
                runCatching { SessionCodec.decode(it) }.getOrNull()
            },
        )
    }
    val engine = remember(runId, language) {
        DiagnosisEngine(
            if (runId == 0) restoredSession ?: DemoCaseSmps.newSession(language)
            else DemoCaseSmps.newSession(language),
        )
    }
    var session by remember(runId, language) { mutableStateOf(engine.session) }
    var started by remember { mutableStateOf(prefs.getBoolean(PREF_STARTED, false)) }
    var liveMapping by remember {
        mutableStateOf(prefs.getBoolean(PREF_LIVE_MAPPING, false))
    }
    fun refresh() {
        session = engine.session
        prefs.edit()
            .putString(PREF_SESSION, SessionCodec.encode(engine.session))
            .putBoolean(PREF_STARTED, started)
            .putBoolean(PREF_LIVE_MAPPING, liveMapping)
            .apply()
    }

    fun resetSession() {
        restoredSession = null
        started = false
        liveMapping = false
        prefs.edit()
            .remove(PREF_SESSION)
            .putBoolean(PREF_STARTED, false)
            .putBoolean(PREF_LIVE_MAPPING, false)
            .apply()
        runId++
    }

    var boardBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var mappingBusy by remember { mutableStateOf(false) }
    var sparkBusy by remember { mutableStateOf(false) }
    var symptomDraft by rememberSaveable { mutableStateOf("") }
    var mappingError by remember { mutableStateOf<String?>(null) }
    var referenceBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var referenceBusy by remember { mutableStateOf(false) }
    var referenceError by remember { mutableStateOf<String?>(null) }
    var referenceForId by remember {
        mutableStateOf(prefs.getString(PREF_REFERENCE_FOR, null))
    }
    val photoScope = rememberCoroutineScope()
    LaunchedEffect(Unit) {
        if (boardBitmap == null) {
            boardBitmap = withContext(Dispatchers.IO) { loadSavedBoardPhoto(context) }
        }
        if (referenceBitmap == null) {
            referenceBitmap = withContext(Dispatchers.IO) {
                loadSavedReferencePhoto(context)
            }
        }
    }
    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent(),
    ) { uri: Uri? ->
        if (uri != null) {
            photoScope.launch {
                boardBitmap = withContext(Dispatchers.IO) {
                    loadBitmapDownscaled(context, uri)?.also { saveBoardPhoto(context, it) }
                }
                liveMapping = false
                mappingError = null
            }
        }
    }
    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicturePreview(),
    ) { bmp: Bitmap? ->
        if (bmp != null) {
            boardBitmap = bmp
            liveMapping = false
            mappingError = null
            photoScope.launch {
                withContext(Dispatchers.IO) { saveBoardPhoto(context, bmp) }
            }
        }
    }

    // La camara NUNCA se invoca sin permiso otorgado: sin el, la app se
    // colgaba en el S25. Sin permiso se pide primero; si el usuario lo
    // deniega, se avisa en la tarjeta de foto y queda Galeria como salida.
    var photoNotice by remember { mutableStateOf<PhotoNotice?>(null) }
    var openCameraAfterGrant by remember { mutableStateOf(false) }
    var permissionFromAbout by remember { mutableStateOf(false) }
    var lastRequestedPermission by remember {
        mutableStateOf(Manifest.permission.CAMERA)
    }
    // Se incrementa al resolverse un pedido: About relee el estado real.
    var permissionTick by remember { mutableStateOf(0) }

    fun launchCamera() {
        photoNotice = null
        runCatching { cameraLauncher.launch(null) }
            .onFailure { photoNotice = PhotoNotice.CAMERA_UNAVAILABLE }
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted: Boolean ->
        permissionTick++
        val rationale = (context as? Activity)
            ?.shouldShowRequestPermissionRationale(lastRequestedPermission) ?: true
        when (cameraAfterRequest(granted, rationale)) {
            CameraAfterRequest.LAUNCH_CAMERA ->
                if (!permissionFromAbout && openCameraAfterGrant) launchCamera()
                else photoNotice = null
            CameraAfterRequest.SHOW_DENIED ->
                if (!permissionFromAbout) photoNotice = PhotoNotice.CAMERA_DENIED
            CameraAfterRequest.SHOW_OPEN_SETTINGS ->
                // Desde About no hay tarjeta donde avisar: ir a Ajustes.
                if (permissionFromAbout) openAppSettings(context)
                else photoNotice = PhotoNotice.CAMERA_PERMANENT
        }
        openCameraAfterGrant = false
        permissionFromAbout = false
    }

    fun requestCameraPermission(openCamera: Boolean) {
        openCameraAfterGrant = openCamera
        permissionFromAbout = false
        lastRequestedPermission = Manifest.permission.CAMERA
        runCatching { cameraPermissionLauncher.launch(Manifest.permission.CAMERA) }
            .onFailure { photoNotice = PhotoNotice.CAMERA_UNAVAILABLE }
    }

    /**
     * Toque sobre el estado de un permiso en About: si falta, se pide con el
     * dialogo del sistema; si ya esta otorgado, se abre Ajustes (unica via
     * para revocarlo). El dialogo denegado para siempre tambien va a Ajustes.
     */
    fun onAboutPermissionTap(permission: String) {
        if (permissionGrant(context.checkSelfPermission(permission))
            == PermissionGrant.GRANTED
        ) {
            openAppSettings(context)
        } else {
            permissionFromAbout = true
            openCameraAfterGrant = false
            lastRequestedPermission = permission
            runCatching { cameraPermissionLauncher.launch(permission) }
        }
    }

    fun openCamera() {
        val granted = permissionGrant(
            context.checkSelfPermission(Manifest.permission.CAMERA),
        ) == PermissionGrant.GRANTED
        when (cameraAction(granted)) {
            CameraAction.LAUNCH_CAMERA -> launchCamera()
            CameraAction.REQUEST_PERMISSION -> requestCameraPermission(openCamera = true)
        }
    }

    fun openSettings() {
        openAppSettings(context)
    }

    fun startMapping() {
        mappingError = null
        val bmp = boardBitmap
        val key = prefs.getString(PREF_API_KEY, null)?.takeIf { it.isNotBlank() }
        if (bmp == null || key == null) {
            liveMapping = false
            engine.loadMapping(DemoCaseSmps.components(language))
            refresh()
            return
        }
        mappingBusy = true
        photoScope.launch {
            try {
                val components = segmentBoardComponents(MetaSamClient(key), bmp)
                if (components.isEmpty()) {
                    mappingError = strings.samNoMatches
                } else {
                    engine.loadMapping(components)
                    liveMapping = true
                    refresh()
                }
            } catch (e: Exception) {
                mappingError = strings.samFailed
            } finally {
                mappingBusy = false
            }
        }
    }

    /** Hipotesis y mediciones en vivo con Muse Spark sobre el mapeo SAM. */
    fun startSpark() {
        mappingError = null
        val bmp = boardBitmap
        val key = prefs.getString(PREF_API_KEY, null)?.takeIf { it.isNotBlank() }
        if (bmp == null || key == null || session.components.isEmpty()) {
            mappingError = strings.sparkFailed
            return
        }
        val symptomAtStart = session.symptom
        sparkBusy = true
        photoScope.launch {
            try {
                val client = MetaSparkClient(key, reasoningEffort = reasoningEffort)
                // 1) Pre-escaneo de dano sobre la foto limpia (pregunta
                //    directa); si falla, el diagnostico sigue sin el.
                val findings = runCatching { client.scanDamage(bmp, language) }
                    .getOrDefault(emptyList())
                if (findings.isNotEmpty()) {
                    val (assignDamages, extras) =
                        SparkDamageParser.assign(findings, session.components)
                    if (extras.isNotEmpty()) engine.addComponents(extras)
                    if (assignDamages.isNotEmpty()) {
                        engine.applyVisibleDamage(assignDamages)
                    }
                    refresh()
                }
                // 2) Diagnostico con los candidatos ya aumentados/marcados.
                val componentsNow = session.components
                val content = client.diagnose(
                    bmp, componentsNow, symptomAtStart, language, findings)
                val diagnosis = SparkOutputParser.parse(content, componentsNow)
                if (diagnosis.hypotheses.isEmpty()) {
                    mappingError = diagnosis.needMoreEvidence ?: strings.sparkNoData
                } else {
                    // Dano visible: candidatos sinteticos (DMG) y marcas
                    // sobre los ya mapeados, antes de cargar las hipotesis.
                    if (diagnosis.extraComponents.isNotEmpty()) {
                        engine.addComponents(diagnosis.extraComponents)
                    }
                    if (diagnosis.damages.isNotEmpty()) {
                        engine.applyVisibleDamage(diagnosis.damages)
                    }
                    engine.loadHypotheses(diagnosis.hypotheses, diagnosis.requests)
                    refresh()
                }
            } catch (e: Exception) {
                mappingError = strings.sparkFailed
            } finally {
                sparkBusy = false
            }
        }
    }

    /** Referencia visual de la reparacion con Muse Image (solo orientativa). */
    fun startReference() {
        referenceError = null
        val bmp = boardBitmap
        val key = prefs.getString(PREF_API_KEY, null)?.takeIf { it.isNotBlank() }
        val component = session.components
            .firstOrNull { it.id == session.verdictComponentId }
        if (bmp == null || key == null || component == null) {
            referenceError = strings.referenceFailed
            return
        }
        referenceBusy = true
        photoScope.launch {
            try {
                val generated = MetaImageClient(key).repairReference(bmp, component)
                referenceBitmap = generated
                referenceForId = component.id
                prefs.edit().putString(PREF_REFERENCE_FOR, component.id).apply()
                withContext(Dispatchers.IO) { saveReferencePhoto(context, generated) }
            } catch (e: Exception) {
                referenceError = strings.referenceFailed
            } finally {
                referenceBusy = false
            }
        }
    }

    fun onLanguageSelected(selected: LanguageMode) {
        mode = selected
        prefs.edit().putString(PREF_LANGUAGE, selected.name).apply()
        if (hasApiKey) completeSetup()
        resetSession()
    }

    fun onApiKeyChanged(nowHasKey: Boolean) {
        hasApiKey = nowHasKey
        if (nowHasKey && prefs.contains(PREF_LANGUAGE)) completeSetup()
    }

    // Toques en el titulo, patron MT: uno abre About tras una pausa corta;
    // seis seguidos (ventana de 1,5 s) abren el huevo de pascua.
    val titleScope = rememberCoroutineScope()
    val titleTaps = remember { TitleTapDetector() }
    var aboutPending by remember { mutableStateOf<Job?>(null) }
    fun onTitleTap() {
        aboutPending?.cancel()
        if (titleTaps.onTap(SystemClock.elapsedRealtime())) {
            showEasterEgg = true
            showAbout = false
            showSettings = false
        } else {
            aboutPending = titleScope.launch {
                delay(550)
                showAbout = true
                showSettings = false
            }
        }
    }

    // Acerca de y Configuracion no llevan botones de cierre: se cierran
    // tocando la pantalla o con el boton atras de Android. Los controles
    // (botones de idioma/tema, campo de clave) consumen su propio toque y
    // no cierran; el desplazamiento tampoco (solo un toque sin arrastrar).
    fun closePanels() {
        showAbout = false
        showSettings = false
        showEasterEgg = false
    }
    BackHandler(enabled = showAbout || showSettings || showEasterEgg) {
        closePanels()
    }

    if (showEasterEgg) {
        EasterEggScreen(strings) { showEasterEgg = false }
        return
    }

    val tapSource = remember { MutableInteractionSource() }
    val tapToClose = if (showAbout || showSettings) {
        Modifier.clickable(tapSource, indication = null) { closePanels() }
    } else {
        Modifier
    }
    Column(
        Modifier.fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .then(tapToClose)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Lockup de marca: mini chip del icono + nombre en un solo
            // color con contraste de peso (Board extrabold, Pilot medium).
            // Todo el conjunto es la zona tocable de Acerca de / huevo.
            Row(
                Modifier.weight(1f).clickable { onTitleTap() },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                AppMark()
                Text(
                    buildAnnotatedString {
                        append("Board")
                        withStyle(SpanStyle(fontWeight = FontWeight.Medium)) {
                            append("Pilot")
                        }
                    },
                    fontSize = 28.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 0.3.sp,
                    maxLines = 1,
                )
            }
            // La ruedita es un interruptor: abre Configuracion y, si ya
            // esta abierta, tocarla de nuevo la cierra.
            SettingsGearButton {
                if (showSettings) {
                    showSettings = false
                } else {
                    showSettings = true
                    showAbout = false
                }
            }
        }

        if (showAbout) {
            AboutContent(strings, permissionTick, ::onAboutPermissionTap)
        } else if (showSettings) {
            Text(strings.settingsTitle, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            LanguageCard(strings, mode, ::onLanguageSelected)
            ThemeCard(strings, themeMode, onThemeModeSelected)
            ReasoningCard(strings, reasoningEffort) { selected ->
                reasoningEffort = selected
                prefs.edit().putString(PREF_EFFORT, selected.name).apply()
            }
            ApiKeyCard(strings, hasApiKey, ::onApiKeyChanged)
        } else {
            Text(strings.appTagline, fontWeight = FontWeight.Medium)
            Text(strings.safetyIntro)
            if (!setupCompleted) {
                Text(strings.firstSetupTitle, fontWeight = FontWeight.Bold)
                Text(strings.firstSetupIntro)
                LanguageCard(strings, mode, ::onLanguageSelected)
                ApiKeyCard(strings, hasApiKey, ::onApiKeyChanged)
                OutlinedButton(
                    onClick = { completeSetup() },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(strings.continueWithoutKey) }
            }
            if (!started) {
                PhotoCard(
                    strings = strings,
                    bitmap = boardBitmap,
                    notice = photoNotice,
                    onGallery = { photoNotice = null; galleryLauncher.launch("image/*") },
                    onCamera = ::openCamera,
                    onOpenSettings = ::openSettings,
                    onRemove = {
                        boardBitmap = null
                        liveMapping = false
                        photoScope.launch {
                            withContext(Dispatchers.IO) { deleteSavedBoardPhoto(context) }
                        }
                    },
                )
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(strings.demoTitle, fontWeight = FontWeight.Bold)
                        OutlinedTextField(
                            value = symptomDraft,
                            onValueChange = { symptomDraft = it },
                            label = { Text(strings.symptomLabel) },
                            placeholder = { Text(strings.symptomPlaceholder) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        if (symptomDraft.isBlank()) {
                            Text(DemoCaseSmps.symptom(language))
                        }
                        Button(
                            onClick = {
                                engine.updateSymptom(symptomDraft)
                                started = true
                                refresh()
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                if (boardBitmap != null && hasApiKey) {
                                    strings.diagnoseStart
                                } else {
                                    strings.demoStart
                                },
                            )
                        }
                    }
                }
            } else {
                SessionContent(
                    engine, session, strings, language, ::refresh,
                    onRestart = { resetSession() },
                    boardBitmap = boardBitmap,
                    canLiveMap = boardBitmap != null && hasApiKey,
                    mappingBusy = mappingBusy,
                    mappingError = mappingError,
                    liveMapping = liveMapping,
                    onMapRequest = ::startMapping,
                    sparkBusy = sparkBusy,
                    onSparkRequest = ::startSpark,
                    referenceBitmap = referenceBitmap
                        .takeIf { referenceForId != null &&
                            referenceForId == session.verdictComponentId },
                    referenceBusy = referenceBusy,
                    referenceError = referenceError,
                    onReferenceRequest = ::startReference,
                )
            }
        }
    }
}

/**
 * Marca de cabecera: replica simplificada del icono de la app (misma
 * geometria y colores del vector `ic_launcher`: placa bordo #2A070D, chip
 * ambar #FFB300 con pines crema #F5E9E2 y pulso de medicion oscuro que
 * termina en un punto-via). Fija en ambos temas, como el icono.
 */
@Composable
private fun AppMark() {
    val board = Color(0xFF2A070D)
    val chip = Color(0xFFFFB300)
    val pins = Color(0xFFF5E9E2)
    Canvas(Modifier.size(36.dp)) {
        val d = size.minDimension
        fun x(f: Float) = size.width * f
        fun y(f: Float) = size.height * f
        // Placa
        drawRoundRect(
            board, Offset.Zero, size,
            cornerRadius = CornerRadius(d * 0.22f, d * 0.22f),
        )
        // Pines: 3 por lado, sobresalen del cuerpo del chip.
        val pinCenters = listOf(0.375f, 0.5f, 0.625f)
        val pinW = d * 0.055f
        val pinLen = d * 0.10f
        pinCenters.forEach { f ->
            // Arriba y abajo
            drawRect(pins, Offset(x(f) - pinW / 2, y(0.30f) - pinLen),
                Size(pinW, pinLen))
            drawRect(pins, Offset(x(f) - pinW / 2, y(0.70f)), Size(pinW, pinLen))
            // Izquierda y derecha
            drawRect(pins, Offset(x(0.30f) - pinLen, y(f) - pinW / 2),
                Size(pinLen, pinW))
            drawRect(pins, Offset(x(0.70f), y(f) - pinW / 2), Size(pinLen, pinW))
        }
        // Cuerpo del chip
        drawRoundRect(
            chip,
            Offset(x(0.30f), y(0.30f)),
            Size(x(0.40f), y(0.40f)),
            cornerRadius = CornerRadius(d * 0.07f, d * 0.07f),
        )
        // Pulso de medicion con punto final
        val pulse = Path().apply {
            moveTo(x(0.365f), y(0.545f))
            lineTo(x(0.445f), y(0.545f))
            lineTo(x(0.485f), y(0.425f))
            lineTo(x(0.54f), y(0.615f))
            lineTo(x(0.575f), y(0.52f))
            lineTo(x(0.61f), y(0.52f))
        }
        drawPath(
            pulse, board,
            style = Stroke(
                width = d * 0.045f, cap = StrokeCap.Round, join = StrokeJoin.Round,
            ),
        )
        drawCircle(board, d * 0.032f, Offset(x(0.65f), y(0.52f)))
    }
}

/** Ruedita dentada de configuracion, dibujada en Canvas (sin dependencias extra). */
@Composable
private fun SettingsGearButton(onClick: () -> Unit) {
    val gearColor = MaterialTheme.colorScheme.primary
    val holeColor = MaterialTheme.colorScheme.background
    IconButton(onClick = onClick) {
        Canvas(Modifier.size(30.dp)) {
            val c = center
            val d = size.minDimension
            // Engranaje clasico como una sola silueta: 8 dientes trapezoidales
            // de flancos rectos y punta plana. Con rectangulos redondeados
            // sueltos salia puntiagudo o con forma de flor.
            val tipRadius = d * 0.46f
            val rootRadius = d * 0.345f
            val teeth = 8
            val pitch = 360f / teeth
            val halfTip = 9f    // mitad del ancho angular en la punta
            val halfRoot = 15f  // mitad del ancho angular en la base
            fun point(angleDeg: Float, radius: Float): Offset {
                val rad = Math.toRadians(angleDeg.toDouble())
                return Offset(
                    c.x + radius * kotlin.math.cos(rad).toFloat(),
                    c.y + radius * kotlin.math.sin(rad).toFloat(),
                )
            }
            val gear = Path()
            for (i in 0 until teeth) {
                val a = -90f + i * pitch
                val rootStart = point(a - halfRoot, rootRadius)
                if (i == 0) gear.moveTo(rootStart.x, rootStart.y)
                else gear.lineTo(rootStart.x, rootStart.y)
                point(a - halfTip, tipRadius).let { gear.lineTo(it.x, it.y) }
                point(a + halfTip, tipRadius).let { gear.lineTo(it.x, it.y) }
                point(a + halfRoot, rootRadius).let { gear.lineTo(it.x, it.y) }
                // Valle hasta el diente siguiente, con punto medio en la
                // raiz para que el fondo siga la circunferencia.
                point(a + pitch / 2f, rootRadius).let { gear.lineTo(it.x, it.y) }
            }
            gear.close()
            drawPath(gear, gearColor)
            drawCircle(holeColor, d * 0.15f, c)
        }
    }
}

@Composable
private fun ThemeCard(
    strings: AppStrings,
    themeMode: ThemeMode,
    onSelect: (ThemeMode) -> Unit,
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(strings.themeTitle, fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    strings.themeAuto to ThemeMode.AUTO,
                    strings.themeLight to ThemeMode.LIGHT,
                    strings.themeDark to ThemeMode.DARK,
                ).forEach { (label, value) ->
                    val btnModifier = Modifier.weight(1f)
                    val btnPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)
                    if (value == themeMode) {
                        Button(onClick = { onSelect(value) }, modifier = btnModifier,
                            contentPadding = btnPadding) {
                            Text(label, maxLines = 1)
                        }
                    } else {
                        OutlinedButton(onClick = { onSelect(value) }, modifier = btnModifier,
                            contentPadding = btnPadding) {
                            Text(label, maxLines = 1)
                        }
                    }
                }
            }
        }
    }
}

/** Nivel de esfuerzo de Muse Spark: dos filas de 3 (fuentes grandes OK). */
@Composable
private fun ReasoningCard(
    strings: AppStrings,
    effort: ReasoningEffort,
    onSelect: (ReasoningEffort) -> Unit,
) {
    val options = listOf(
        strings.reasoningAuto to ReasoningEffort.AUTO,
        strings.reasoningMinimal to ReasoningEffort.MINIMAL,
        strings.reasoningLow to ReasoningEffort.LOW,
        strings.reasoningMedium to ReasoningEffort.MEDIUM,
        strings.reasoningHigh to ReasoningEffort.HIGH,
        strings.reasoningMax to ReasoningEffort.XHIGH,
    )
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(strings.reasoningTitle, fontWeight = FontWeight.Bold)
            Text(strings.reasoningHint)
            options.chunked(3).forEach { rowOptions ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    rowOptions.forEach { (label, value) ->
                        val btnModifier = Modifier.weight(1f)
                        val btnPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)
                        if (value == effort) {
                            Button(onClick = { onSelect(value) }, modifier = btnModifier,
                                contentPadding = btnPadding) {
                                Text(label, maxLines = 1)
                            }
                        } else {
                            OutlinedButton(onClick = { onSelect(value) },
                                modifier = btnModifier, contentPadding = btnPadding) {
                                Text(label, maxLines = 1)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LanguageCard(
    strings: AppStrings,
    mode: LanguageMode,
    onSelect: (LanguageMode) -> Unit,
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(strings.languageTitle, fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    strings.languageAuto to LanguageMode.AUTO,
                    "Español" to LanguageMode.ES,
                    "English" to LanguageMode.EN,
                ).forEach { (label, value) ->
                    val btnModifier = Modifier.weight(1f)
                    val btnPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)
                    if (value == mode) {
                        Button(onClick = { onSelect(value) }, modifier = btnModifier,
                            contentPadding = btnPadding) {
                            Text(label, maxLines = 1)
                        }
                    } else {
                        OutlinedButton(onClick = { onSelect(value) }, modifier = btnModifier,
                            contentPadding = btnPadding) {
                            Text(label, maxLines = 1)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ApiKeyCard(
    strings: AppStrings,
    hasKey: Boolean,
    onKeyChanged: (Boolean) -> Unit,
) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences(PREFS, Context.MODE_PRIVATE) }
    var keyText by remember { mutableStateOf("") }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(strings.apiTitle, fontWeight = FontWeight.Bold)
            Text(if (hasKey) strings.apiLoaded else strings.apiMissing)
            if (!hasKey) {
                OutlinedTextField(
                    value = keyText,
                    onValueChange = { keyText = it },
                    label = { Text("MODEL_API_KEY") },
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                Button(
                    onClick = {
                        prefs.edit().putString(PREF_API_KEY, keyText.trim()).apply()
                        keyText = ""
                        onKeyChanged(true)
                    },
                    enabled = keyText.isNotBlank(),
                ) { Text(strings.apiSave) }
            } else {
                OutlinedButton(onClick = {
                    prefs.edit().remove(PREF_API_KEY).apply()
                    onKeyChanged(false)
                }) { Text(strings.apiDelete) }
            }
        }
    }
}

/**
 * Pantalla "Acerca de": se abre tocando el nombre BoardPilot. Sin botones:
 * se cierra tocando la pantalla o con el boton atras de Android. El estado
 * de la camara ("Otorgado" / "No otorgado") si es tocable: cambia el permiso.
 */
@Composable
private fun AboutContent(
    strings: AppStrings,
    permissionTick: Int,
    onPermissionTap: (String) -> Unit,
) {
    val context = LocalContext.current
    val packageInfo = remember {
        @Suppress("DEPRECATION")
        runCatching {
            context.packageManager.getPackageInfo(
                context.packageName, PackageManager.GET_PERMISSIONS,
            )
        }.getOrNull()
    }
    val versionText = packageInfo?.versionName ?: "—"
    val permissions = packageInfo?.requestedPermissions.orEmpty()
        .filterNot { it.contains("DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION") }
    // Releer el estado al volver (p. ej. desde Ajustes, donde se pudo revocar).
    var resumeTick by remember { mutableStateOf(0) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) resumeTick++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    // Estado REAL de cada permiso: declarado no significa otorgado.
    val grants = remember(permissionTick, resumeTick) {
        permissions.associateWith {
            permissionGrant(context.checkSelfPermission(it))
        }
    }

    Text(strings.aboutTitle, fontSize = 22.sp, fontWeight = FontWeight.Bold)
    Text(strings.appTagline)
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("${strings.nameLabel}: BoardPilot")
            Text("${strings.authorLabel}: $APP_AUTHOR")
            Text("${strings.versionLabel}: $versionText")
            Text("${strings.packageLabel}: ${context.packageName}")
            Text("${strings.licenseLabel}: $APP_LICENSE")
        }
    }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(strings.permissionsTitle, fontWeight = FontWeight.Bold)
            if (permissions.isEmpty()) {
                Text(strings.permissionsNone)
            } else {
                permissions.forEach { permission ->
                    val granted = grants[permission] == PermissionGrant.GRANTED
                    Text(
                        "• " + when (permission) {
                            Manifest.permission.INTERNET -> strings.permissionInternet
                            Manifest.permission.CAMERA -> strings.permissionCamera
                            else -> permission
                        },
                    )
                    // La camara es tocable (sin subrayado): pide el permiso o
                    // va a Ajustes segun el estado; consume el toque, no cierra.
                    val tappable = permission == Manifest.permission.CAMERA
                    Text(
                        "    " + if (granted) strings.permissionGranted
                        else strings.permissionNotGranted,
                        fontWeight = FontWeight.Bold,
                        color = if (granted) MaterialTheme.colorScheme.secondary
                        else Color(0xFFB00020),
                        modifier = if (tappable) {
                            Modifier.clickable { onPermissionTap(permission) }
                        } else {
                            Modifier
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun SessionContent(
    engine: DiagnosisEngine,
    session: DiagnosisSession,
    strings: AppStrings,
    language: AppLanguage,
    refresh: () -> Unit,
    onRestart: () -> Unit,
    boardBitmap: Bitmap?,
    canLiveMap: Boolean,
    mappingBusy: Boolean,
    mappingError: String?,
    liveMapping: Boolean,
    onMapRequest: () -> Unit,
    sparkBusy: Boolean,
    onSparkRequest: () -> Unit,
    referenceBitmap: Bitmap?,
    referenceBusy: Boolean,
    referenceError: String?,
    onReferenceRequest: () -> Unit,
) {
    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
    val selected = session.components.firstOrNull { it.id == selectedId }
    var message by remember { mutableStateOf<String?>(null) }

    Text("${strings.stageLabel}: ${strings.stageName(session.stage)}",
        fontWeight = FontWeight.Bold)
    Text("${strings.symptomLabel}: ${session.symptom}")
    mappingError?.let { Text(it, color = Color(0xFFB00020)) }
    if (session.components.isNotEmpty()) {
        BoardMap(session.components, selected, boardBitmap) { selectedId = it.id }
        selected?.let { comp ->
            Text("${comp.designator} · ${comp.kind}" +
                (comp.visibleDamage?.let { " · ⚠ $it" } ?: ""))
        }
    }
    message?.let { Text(it, color = Color(0xFFB00020)) }

    when (session.stage) {
        Stage.INTAKE -> Button(
            onClick = onMapRequest,
            enabled = !mappingBusy,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                when {
                    mappingBusy -> strings.mappingBusy
                    canLiveMap -> strings.mapLiveSam
                    else -> strings.mapComponents
                },
            )
        }

        Stage.MAPPING -> if (liveMapping) {
            Text(strings.liveMappingNote)
            Button(
                onClick = onSparkRequest,
                enabled = !sparkBusy,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (sparkBusy) strings.sparkBusy else strings.sparkGenerate)
            }
            OutlinedButton(onClick = onRestart, modifier = Modifier.fillMaxWidth()) {
                Text(strings.restartDemo)
            }
        } else {
            Button(onClick = {
                engine.loadHypotheses(
                    DemoCaseSmps.hypotheses(language), DemoCaseSmps.requests(language))
                refresh()
            }) { Text(strings.generateHypotheses) }
        }

        Stage.HYPOTHESES -> {
            HypothesisList(session, strings)
            if (session.pendingRequests.isNotEmpty()) {
                Button(onClick = { engine.startMeasuring(); refresh() }) {
                    Text(strings.startMeasuring)
                }
            } else {
                Text(strings.needMoreEvidence)
                OutlinedButton(onClick = onRestart) { Text(strings.restartDemo) }
            }
        }

        Stage.MEASURING -> {
            HypothesisList(session, strings)
            session.currentRequest?.let { req ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("${strings.measurementTitle}: ${strings.kindName(req.kind)} " +
                            "· ${req.componentId.uppercase()}", fontWeight = FontWeight.Bold)
                        Text(req.instruction)
                        Text("${strings.normalDescLabel}: ${req.normalDescription}")
                        Text("${strings.abnormalDescLabel}: ${req.abnormalDescription}")
                        Text("⚠ ${strings.gateWarning(req.gate)}",
                            fontWeight = FontWeight.Bold)
                        if (!session.safetyConfirmedForCurrent) {
                            Button(onClick = { engine.confirmSafety(); refresh() }) {
                                Text(strings.confirmSafety)
                            }
                        } else {
                            // Botones a ancho completo, uno por fila: nunca se cortan,
                            // sirven con fuentes grandes de accesibilidad y con guantes.
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf(
                                    strings.normalLabel to MeasurementOutcome.NORMAL,
                                    strings.abnormalLabel to MeasurementOutcome.ABNORMAL,
                                    strings.unclearLabel to MeasurementOutcome.UNCLEAR,
                                ).forEach { (label, outcome) ->
                                    OutlinedButton(
                                        onClick = {
                                            val r = engine.submitResult(
                                                MeasurementResult(req.id, outcome))
                                            message = (r as? DiagnosisEngine
                                                .SubmitResult.Rejected)
                                                ?.let { strings.rejectMessage(it.reason) }
                                            refresh()
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                    ) { Text(label) }
                                }
                            }
                        }
                    }
                }
            }
        }

        Stage.VERDICT -> {
            val comp = session.components.firstOrNull { it.id == session.verdictComponentId }
            Text("${strings.verdictPrefix} ${comp?.designator ?: "?"} (${comp?.kind ?: ""})",
                fontWeight = FontWeight.Bold, fontSize = 18.sp)
            val replacementText = if (liveMapping) {
                strings.liveReplacement(comp?.designator ?: "?")
            } else {
                DemoCaseSmps.replacement(language)
            }
            Text(replacementText)
            if (liveMapping) {
                ReferenceCard(
                    strings, referenceBitmap, referenceBusy,
                    referenceError, onReferenceRequest,
                )
            }
            Button(onClick = {
                engine.markRepaired(replacementText); refresh()
            }) { Text(strings.alreadyReplaced) }
        }

        Stage.REPAIR -> {
            Text(session.replacement.orEmpty())
            if (referenceBitmap != null) {
                ReferenceCard(
                    strings, referenceBitmap, referenceBusy,
                    referenceError, onReferenceRequest,
                )
            }
            Text(strings.verifyQuestion)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { engine.markVerified(true); refresh() }) {
                    Text(strings.yesOn)
                }
                OutlinedButton(onClick = { engine.markVerified(false); refresh() }) {
                    Text(strings.notOn)
                }
            }
        }

        Stage.VERIFIED -> {
            Text(strings.verifiedOk, fontWeight = FontWeight.Bold)
            OutlinedButton(onClick = onRestart) { Text(strings.backHome) }
        }
    }
}

@Composable
private fun HypothesisList(session: DiagnosisSession, strings: AppStrings) {
    if (session.hypotheses.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(strings.hypothesesTitle, fontWeight = FontWeight.Bold)
        session.hypotheses.forEach { h ->
            Text("• ${h.title} — ${(h.confidence * 100).toInt()}%" +
                if (h.confirmedByMeasurement) strings.confirmedSuffix else "")
            if (h.rationale.isNotBlank()) {
                Text("    ${h.rationale}", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** Referencia generada con Muse Image: boton para pedirla y foto rotulada. */
@Composable
private fun ReferenceCard(
    strings: AppStrings,
    bitmap: Bitmap?,
    busy: Boolean,
    error: String?,
    onGenerate: () -> Unit,
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(strings.referenceTitle, fontWeight = FontWeight.Bold)
            error?.let { Text(it, color = Color(0xFFB00020)) }
            if (bitmap != null) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier.fillMaxWidth().height(280.dp),
                    contentScale = ContentScale.Crop,
                )
                Text(strings.referenceNote)
            }
            Button(
                onClick = onGenerate,
                enabled = !busy,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (busy) strings.referenceBusy else strings.referenceGenerate)
            }
        }
    }
}

@Composable
private fun BoardMap(
    components: List<BoardComponent>,
    selected: BoardComponent?,
    bitmap: Bitmap?,
    onSelect: (BoardComponent) -> Unit,
) {
    val ratio = bitmap?.let {
        (it.width.toFloat() / it.height.toFloat()).coerceIn(0.55f, 1.9f)
    }
    BoxWithConstraints(
        Modifier.fillMaxWidth()
            .background(Color(0xFF380B14), RoundedCornerShape(8.dp))
            .then(
                if (ratio != null) Modifier.aspectRatio(ratio)
                else Modifier.height(240.dp),
            ),
    ) {
        val w = maxWidth
        val h = maxHeight
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        }
        val accent = MaterialTheme.colorScheme.primary
        val selectedAccent = Color(0xFFFFB300)
        components.forEach { comp ->
            val isSel = comp.id == selected?.id
            val compW = w * comp.box.w
            val compH = h * comp.box.h
            // Con dano visible el marco va en rojo de error; seleccionado pisa.
            val frame = when {
                isSel -> selectedAccent
                comp.visibleDamage != null -> Color(0xFFB00020)
                else -> accent
            }
            Box(
                Modifier
                    .offset(x = w * comp.box.x, y = h * comp.box.y)
                    .size(width = compW, height = compH)
                    .background(
                        frame.copy(alpha = if (isSel) 0.34f else 0.16f),
                        RoundedCornerShape(3.dp),
                    )
                    .border(2.dp, frame, RoundedCornerShape(3.dp))
                    .clickable { onSelect(comp) },
            ) {
                if (compW > 54.dp && compH > 26.dp) {
                    Box(
                        Modifier.align(Alignment.TopStart)
                            .background(frame, RoundedCornerShape(3.dp))
                            .padding(horizontal = 4.dp, vertical = 1.dp),
                    ) {
                        Text(
                            comp.designator,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isSel) Color.Black else Color.White,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PhotoCard(
    strings: AppStrings,
    bitmap: Bitmap?,
    notice: PhotoNotice?,
    onGallery: () -> Unit,
    onCamera: () -> Unit,
    onOpenSettings: () -> Unit,
    onRemove: () -> Unit,
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(strings.photoTitle, fontWeight = FontWeight.Bold)
            notice?.let {
                Text(
                    when (it) {
                        PhotoNotice.CAMERA_DENIED -> strings.cameraPermissionDenied
                        PhotoNotice.CAMERA_PERMANENT -> strings.cameraPermissionPermanent
                        PhotoNotice.CAMERA_UNAVAILABLE -> strings.cameraUnavailable
                    },
                    color = Color(0xFFB00020),
                )
                if (it == PhotoNotice.CAMERA_PERMANENT) {
                    OutlinedButton(
                        onClick = onOpenSettings,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(strings.openAppSettings) }
                }
            }
            if (bitmap == null) {
                Text(strings.photoNone)
            } else {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier.fillMaxWidth().height(180.dp),
                    contentScale = ContentScale.Crop,
                )
                Text("${strings.photoReady}: ${bitmap.width}×${bitmap.height}")
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onGallery, modifier = Modifier.weight(1f)) {
                    Text(strings.photoGallery, maxLines = 1)
                }
                OutlinedButton(onClick = onCamera, modifier = Modifier.weight(1f)) {
                    Text(strings.photoCamera, maxLines = 1)
                }
            }
            if (bitmap != null) {
                OutlinedButton(onClick = onRemove, modifier = Modifier.fillMaxWidth()) {
                    Text(strings.photoRemove)
                }
            }
        }
    }
}

/**
 * Conceptos SAM validados contra una SMPS real (cargador 18650, 28 oct):
 * los terminos tecnicos (transformer, optocoupler, transistor) devolvieron
 * cero; los descriptores VISUALES si. SAM aporta candidatos por forma y la
 * clasificacion fina queda para Muse Spark sobre la foto anotada.
 */
private val SAM_CONCEPTS = listOf(
    Triple("yellow rectangular block", ComponentKind.TRANSFORMER, "TRF"),
    Triple("green rectangular block", ComponentKind.TRANSFORMER, "TRF"),
    Triple("rectangular electronic component", ComponentKind.OTHER, "RECT"),
    Triple("cylindrical component", ComponentKind.OTHER, "CYL"),
    Triple("electronic component", ComponentKind.OTHER, "PART"),
)

private suspend fun segmentBoardComponents(
    client: MetaSamClient,
    bitmap: Bitmap,
): List<BoardComponent> {
    data class Candidate(val kind: ComponentKind, val label: String, val samBox: SamBox)

    val candidates = mutableListOf<Candidate>()
    for ((phrase, kind, label) in SAM_CONCEPTS) {
        client.segment(bitmap, phrase)
            .filter { (it.confidence ?: 1.0) >= 0.35 }
            .forEach { samBox ->
                val duplicate = candidates.any {
                    SamOutputParser.iou(it.samBox.box, samBox.box) > 0.6f
                }
                if (!duplicate) candidates += Candidate(kind, label, samBox)
            }
    }
    val counters = mutableMapOf<String, Int>()
    return candidates.map { candidate ->
        val n = (counters[candidate.label] ?: 0) + 1
        counters[candidate.label] = n
        BoardComponent(
            id = "${candidate.label.lowercase()}$n-sam${candidate.samBox.objectId}",
            designator = "${candidate.label} $n",
            kind = candidate.kind,
            box = candidate.samBox.box,
        )
    }
}

/** Decodifica una Uri de galeria reducida a [maxDimension] px como maximo. */
private fun loadBitmapDownscaled(context: Context, uri: Uri, maxDimension: Int = 1600): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    context.contentResolver.openInputStream(uri)?.use {
        BitmapFactory.decodeStream(it, null, bounds)
    }
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    var sample = 1
    while (maxOf(bounds.outWidth, bounds.outHeight) / sample > maxDimension) sample *= 2
    val options = BitmapFactory.Options().apply { inSampleSize = sample }
    val decoded = context.contentResolver.openInputStream(uri)?.use {
        BitmapFactory.decodeStream(it, null, options)
    } ?: return null
    val longest = maxOf(decoded.width, decoded.height)
    if (longest <= maxDimension) return decoded
    val scale = maxDimension.toFloat() / longest.toFloat()
    return Bitmap.createScaledBitmap(
        decoded,
        (decoded.width * scale).toInt().coerceAtLeast(1),
        (decoded.height * scale).toInt().coerceAtLeast(1),
        true,
    )
}

private fun boardPhotoFile(context: Context): File = File(context.filesDir, BOARD_PHOTO_FILE)

private fun saveBoardPhoto(context: Context, bitmap: Bitmap) {
    runCatching {
        FileOutputStream(boardPhotoFile(context)).use { output ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, output)
        }
    }
}

private fun loadSavedBoardPhoto(context: Context): Bitmap? {
    val file = boardPhotoFile(context)
    return if (file.isFile) runCatching { BitmapFactory.decodeFile(file.absolutePath) }
        .getOrNull() else null
}

private fun deleteSavedBoardPhoto(context: Context) {
    runCatching { boardPhotoFile(context).delete() }
}

private fun saveReferencePhoto(context: Context, bitmap: Bitmap) {
    runCatching {
        FileOutputStream(File(context.filesDir, REFERENCE_PHOTO_FILE)).use { output ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, output)
        }
    }
}

private fun loadSavedReferencePhoto(context: Context): Bitmap? {
    val file = File(context.filesDir, REFERENCE_PHOTO_FILE)
    return if (file.isFile) runCatching { BitmapFactory.decodeFile(file.absolutePath) }
        .getOrNull() else null
}

/** Abre los ajustes de la app (salida cuando el permiso quedo denegado). */
private fun openAppSettings(context: Context) {
    runCatching {
        context.startActivity(
            Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.fromParts("package", context.packageName, null),
            ),
        )
    }
}
