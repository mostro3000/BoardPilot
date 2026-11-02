package ar.com.mt0.boardpilot

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.ExifInterface
import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import android.provider.Settings
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColor
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateRotation
import androidx.compose.foundation.gestures.calculateZoom
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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import ar.com.mt0.boardpilot.api.MetaImageClient
import ar.com.mt0.boardpilot.api.MetaSamClient
import ar.com.mt0.boardpilot.api.MetaSparkClient
import ar.com.mt0.boardpilot.domain.BoardComponent
import ar.com.mt0.boardpilot.domain.BoardPlaybooks
import ar.com.mt0.boardpilot.domain.BoardType
import ar.com.mt0.boardpilot.domain.HazardZone
import ar.com.mt0.boardpilot.domain.BoardView
import ar.com.mt0.boardpilot.domain.FitRect
import ar.com.mt0.boardpilot.domain.CameraAction
import ar.com.mt0.boardpilot.domain.CameraAfterRequest
import ar.com.mt0.boardpilot.domain.ComponentKind
import ar.com.mt0.boardpilot.domain.CropBox
import ar.com.mt0.boardpilot.domain.RotatedPhoto
import ar.com.mt0.boardpilot.domain.fineOf
import ar.com.mt0.boardpilot.domain.normalizedAngle
import ar.com.mt0.boardpilot.domain.quarterOf
import ar.com.mt0.boardpilot.domain.PermissionGrant
import ar.com.mt0.boardpilot.domain.PhotoLoading
import ar.com.mt0.boardpilot.domain.ProbeGuide
import ar.com.mt0.boardpilot.domain.ProbePoint
import ar.com.mt0.boardpilot.domain.ProjectStatus
import ar.com.mt0.boardpilot.domain.ReasoningEffort
import ar.com.mt0.boardpilot.domain.RepairProject
import ar.com.mt0.boardpilot.domain.cameraAction
import ar.com.mt0.boardpilot.domain.cameraAfterRequest
import ar.com.mt0.boardpilot.domain.permissionGrant
import ar.com.mt0.boardpilot.domain.DiagnosisEngine
import ar.com.mt0.boardpilot.domain.DiagnosisSession
import ar.com.mt0.boardpilot.domain.MeasurementKind
import ar.com.mt0.boardpilot.domain.MeasurementRequest
import ar.com.mt0.boardpilot.domain.MeasurementOutcome
import ar.com.mt0.boardpilot.domain.MapZoom
import ar.com.mt0.boardpilot.domain.MeasurementResult
import ar.com.mt0.boardpilot.domain.SamBox
import ar.com.mt0.boardpilot.domain.SamOutputParser
import ar.com.mt0.boardpilot.domain.SparkDamageParser
import ar.com.mt0.boardpilot.domain.SparkMode
import ar.com.mt0.boardpilot.domain.SparkOutputParser
import ar.com.mt0.boardpilot.domain.SparkPlan
import ar.com.mt0.boardpilot.domain.Stage
import ar.com.mt0.boardpilot.domain.ThemeMode
import ar.com.mt0.boardpilot.domain.TitleTapDetector
import ar.com.mt0.boardpilot.domain.isUntouched
import ar.com.mt0.boardpilot.domain.MAX_EXTRA_PHOTOS
import ar.com.mt0.boardpilot.domain.newDiagnosisSession
import ar.com.mt0.boardpilot.domain.paused
import ar.com.mt0.boardpilot.domain.projectStatus
import ar.com.mt0.boardpilot.domain.renamed
import ar.com.mt0.boardpilot.domain.resolveDarkTheme
import ar.com.mt0.boardpilot.domain.resumed
import ar.com.mt0.boardpilot.domain.sortedProjects
import ar.com.mt0.boardpilot.domain.withAutoTitle
import ar.com.mt0.boardpilot.domain.withProgress
import ar.com.mt0.boardpilot.domain.withoutDemoSession
import ar.com.mt0.boardpilot.i18n.AppStrings
import ar.com.mt0.boardpilot.i18n.EnStrings
import ar.com.mt0.boardpilot.i18n.EsStrings
import ar.com.mt0.boardpilot.i18n.LanguageMode
import ar.com.mt0.boardpilot.i18n.appStrings
import ar.com.mt0.boardpilot.i18n.projectSubtitle
import ar.com.mt0.boardpilot.i18n.relativeTime
import ar.com.mt0.boardpilot.i18n.resolveLanguage
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.ZoneId
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

private const val PREFS = "boardpilot_prefs"
private const val PREF_API_KEY = "model_api_key"
private const val PREF_LANGUAGE = "language_mode"
private const val PREF_THEME = "theme_mode"
private const val PREF_EFFORT = "reasoning_effort"
private const val PREF_SPARK_MODE = "spark_mode"
private const val PREF_CONTRIBUTOR_ACCEPTED = "contributor_accepted"

/** Desde este valor se muestra la chance de otra causa en la lista de hipotesis. */
private const val OTHER_CAUSE_MIN_PERCENT = 20
/** Lado mayor de la miniatura de cada proyecto en la lista. */
private const val THUMB_PX = 192
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
    // Avisos en rojo vivo; en oscuro, uno claro que se lea sobre el fondo.
    error = Color(0xFFB00020),
    // Aviso de Deshacer: oscuro calido con la accion en ambar (no el violeta por defecto).
    inverseSurface = Color(0xFF342124),
    inverseOnSurface = Color(0xFFF7F3F2),
    inversePrimary = Color(0xFFFFBA7A),
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
    error = Color(0xFFFFB4AB),
    // Aviso de Deshacer: claro calido con la accion en bordo.
    inverseSurface = Color(0xFFF1E4E1),
    inverseOnSurface = Color(0xFF211114),
    inversePrimary = Color(0xFF7A1220),
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
            // El aviso de Deshacer flota abajo, sobre cualquier pantalla.
            val snackbar = remember { SnackbarHostState() }
            Box(Modifier.fillMaxSize()) {
                BoardPilotApp(themeMode, snackbar) { selected ->
                    themeMode = selected
                    prefs.edit().putString(PREF_THEME, selected.name).apply()
                }
                SnackbarHost(
                    snackbar,
                    Modifier.align(Alignment.BottomCenter)
                        .windowInsetsPadding(WindowInsets.safeDrawing)
                        .padding(16.dp),
                )
            }
        }
    }
}

@Composable
fun BoardPilotApp(
    themeMode: ThemeMode,
    snackbar: SnackbarHostState,
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
    var sparkMode by remember {
        mutableStateOf(SparkMode.fromStored(prefs.getString(PREF_SPARK_MODE, null)))
    }
    // Quien ya eligio Contributor en una version anterior acepto su aviso.
    var contributorAccepted by remember {
        mutableStateOf(
            prefs.getBoolean(PREF_CONTRIBUTOR_ACCEPTED, false) ||
                prefs.getString(PREF_SPARK_MODE, null) == SparkMode.CONTRIBUTOR.name,
        )
    }
    var contributorNoticeFor by rememberSaveable { mutableStateOf<String?>(null) }

    fun acceptContributor() {
        contributorAccepted = true
        prefs.edit().putBoolean(PREF_CONTRIBUTOR_ACCEPTED, true).apply()
    }

    fun selectSparkMode(selected: SparkMode) {
        sparkMode = selected
        prefs.edit().putString(PREF_SPARK_MODE, selected.name).apply()
    }

    fun sparkClient(key: String) = MetaSparkClient(
        key,
        reasoningEffort = reasoningEffort,
        mode = sparkMode.effectiveFor(contributorAccepted),
    )

    var hasApiKey by remember {
        mutableStateOf(!prefs.getString(PREF_API_KEY, null).isNullOrBlank())
    }
    // Sin clave la app no puede diagnosticar: abre directo en Configuracion,
    // con la clave primero.
    var showSettings by rememberSaveable { mutableStateOf(!hasApiKey) }
    var showAbout by rememberSaveable { mutableStateOf(false) }
    var showEasterEgg by rememberSaveable { mutableStateOf(false) }

    // Reparaciones guardadas, como los chats: la app abre en la lista y cada
    // proyecto conserva su sesion, su foto y su referencia (archivos por id).
    val store = remember { ProjectStore.get(context) }
    val defaultTitles = remember { setOf(EsStrings.newProjectTitle, EnStrings.newProjectTitle) }
    var projects by remember {
        val loaded = store.load(
            prefs, newDiagnosisSession(), strings.newProjectTitle,
            System.currentTimeMillis(),
        )
        // Los proyectos que se mapearon con el demo vuelven al ingreso.
        val cleaned = loaded.map { it.withoutDemoSession(newDiagnosisSession()) }
        if (cleaned != loaded) store.save(cleaned)
        mutableStateOf(cleaned)
    }
    var openProjectId by rememberSaveable { mutableStateOf<String?>(null) }
    val currentProject = projects.firstOrNull { it.id == openProjectId }
    // Un motor por proyecto: lo que devuelva SAM/Spark/Image en vuelo cae en
    // su propio proyecto aunque el tecnico ya este en otro o en la lista.
    val engines = remember { mutableMapOf<String, DiagnosisEngine>() }
    fun engineFor(project: RepairProject): DiagnosisEngine =
        engines.getOrPut(project.id) { DiagnosisEngine(project.session) }

    fun updateProject(id: String, change: (RepairProject) -> RepairProject) {
        if (projects.none { it.id == id }) return
        projects = projects.map { if (it.id == id) change(it) else it }
        store.save(projects)
    }

    /** Vuelca el motor del proyecto a la lista guardada (y a la pantalla). */
    fun refresh(id: String, liveMapping: Boolean? = null) {
        val engine = engines[id] ?: return
        updateProject(id) {
            it.withProgress(
                engine.session, it.started, liveMapping ?: it.liveMapping,
                System.currentTimeMillis(),
            )
        }
    }

    var boardBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var referenceBitmap by remember { mutableStateOf<Bitmap?>(null) }
    val thumbnails = remember { mutableStateMapOf<String, Bitmap>() }
    // Pedidos en vuelo por proyecto: el boton ocupado es solo el de ese proyecto.
    val mappingBusy = remember { mutableStateListOf<String>() }
    val sparkBusy = remember { mutableStateListOf<String>() }
    // Mediciones siguientes pedidas a Muse Spark, y lo que respondio si no propuso ninguna.
    val planBusy = remember { mutableStateListOf<String>() }
    val sparkNotes = remember { mutableStateMapOf<String, String>() }
    // Puntas sugeridas por Muse Spark (capa 2 de la guia visual), en segundo plano.
    val probeBusy = remember { mutableStateListOf<String>() }
    val referenceBusy = remember { mutableStateListOf<String>() }
    var symptomDraft by rememberSaveable { mutableStateOf("") }
    var boardTypeDraft by rememberSaveable { mutableStateOf(BoardType.UNKNOWN) }
    var mappingError by remember { mutableStateOf<String?>(null) }
    var referenceError by remember { mutableStateOf<String?>(null) }
    // Aviso de la tarjeta de foto (permiso de camara denegado, etc.).
    var photoNotice by remember { mutableStateOf<PhotoNotice?>(null) }
    // Foto elegida para sumar a la reparacion, a la espera de mandarla; sobrevive a la camara.
    var pickingExtraPhoto by rememberSaveable { mutableStateOf(false) }
    var pendingExtraPhoto by remember { mutableStateOf<Bitmap?>(null) }
    val extraThumbnails = remember { mutableStateMapOf<String, List<Bitmap>>() }
    var croppingPhoto by rememberSaveable { mutableStateOf(false) }
    val photoScope = rememberCoroutineScope()

    LaunchedEffect(openProjectId) {
        val id = openProjectId ?: return@LaunchedEffect
        if (boardBitmap == null) {
            boardBitmap = withContext(Dispatchers.IO) { store.loadBoardPhoto(id) }
        }
        if (referenceBitmap == null) {
            referenceBitmap = withContext(Dispatchers.IO) { store.loadReference(id) }
        }
    }
    // Con un pedido en vuelo la pantalla no se apaga: el diagnostico con
    // razonamiento tarda minutos y el telefono se bloqueaba esperando. Va por
    // la ventana (no por la vista) para no pisarse con el huevo de pascua.
    val requestInFlight = mappingBusy.isNotEmpty() || sparkBusy.isNotEmpty() ||
        planBusy.isNotEmpty() || probeBusy.isNotEmpty() || referenceBusy.isNotEmpty()
    DisposableEffect(requestInFlight) {
        val window = (context as? Activity)?.window
        if (requestInFlight) window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose { window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }
    val extraPhotoCount = currentProject?.session?.extraPhotos?.size ?: 0
    LaunchedEffect(openProjectId, extraPhotoCount) {
        val id = openProjectId ?: return@LaunchedEffect
        if (extraThumbnails[id].orEmpty().size != extraPhotoCount) {
            extraThumbnails[id] = withContext(Dispatchers.IO) {
                (0 until extraPhotoCount).mapNotNull { store.loadExtraThumbnail(id, it, THUMB_PX) }
            }
        }
    }
    LaunchedEffect(openProjectId == null, projects.size) {
        if (openProjectId != null) return@LaunchedEffect
        projects.filter { it.id !in thumbnails }.forEach { project ->
            withContext(Dispatchers.IO) { store.loadThumbnail(project.id, THUMB_PX) }
                ?.let { thumbnails[project.id] = it }
        }
    }

    fun apiKey(): String? = prefs.getString(PREF_API_KEY, null)?.takeIf { it.isNotBlank() }

    fun openProject(id: String) {
        boardBitmap = null
        referenceBitmap = null
        symptomDraft = ""
        boardTypeDraft = BoardType.UNKNOWN
        mappingError = null
        referenceError = null
        photoNotice = null
        croppingPhoto = false
        pickingExtraPhoto = false
        pendingExtraPhoto = null
        openProjectId = id
    }

    fun createProject() {
        val now = System.currentTimeMillis()
        val project = RepairProject(
            id = store.newId(),
            title = strings.newProjectTitle,
            createdAtMillis = now,
            updatedAtMillis = now,
            session = newDiagnosisSession(),
        )
        projects = projects + project
        store.save(projects)
        openProject(project.id)
    }

    /** Vuelve a la lista; un proyecto nuevo sin tocar no se guarda (como un chat vacio). */
    fun closeProject() {
        val project = currentProject
        openProjectId = null
        if (project != null && project.isUntouched(defaultTitles) &&
            boardBitmap == null && !store.hasBoardPhoto(project.id)
        ) {
            projects = projects.filterNot { it.id == project.id }
            engines.remove(project.id)
            store.save(projects)
            store.deleteFiles(project.id)
        }
    }

    /**
     * Borrado deslizando en la lista, como otras apps: el proyecto sale al
     * instante y un aviso ofrece Deshacer; su foto y su referencia se borran
     * recien cuando el aviso se va (o si la pantalla se destruye antes).
     */
    fun deleteProject(id: String) {
        val removed = projects.firstOrNull { it.id == id } ?: return
        projects = projects.filterNot { it.id == id }
        engines.remove(id)
        store.save(projects)
        // Un borrado nuevo cierra el aviso anterior, que se confirma.
        snackbar.currentSnackbarData?.dismiss()
        photoScope.launch {
            var restored = false
            try {
                val result = snackbar.showSnackbar(
                    strings.projectDeleted(removed.title), strings.undo,
                    duration = SnackbarDuration.Long,
                )
                if (result == SnackbarResult.ActionPerformed && projects.none { it.id == id }) {
                    projects = projects + removed
                    store.save(projects)
                    restored = true
                }
            } finally {
                if (!restored) {
                    thumbnails.remove(id)
                    store.deleteFiles(id)
                }
            }
        }
    }

    fun startDiagnosis(project: RepairProject, boardType: BoardType) {
        val engine = engineFor(project)
        engine.updateSymptom(symptomDraft)
        engine.updateBoardType(boardType)
        updateProject(project.id) {
            it.withProgress(engine.session, true, it.liveMapping, System.currentTimeMillis())
                .withAutoTitle(defaultTitles)
        }
    }

    /** Reinicia el diagnostico del proyecto; conserva nombre y foto. */
    fun restartProject(id: String) {
        val fresh = newDiagnosisSession()
        engines[id] = DiagnosisEngine(fresh)
        updateProject(id) {
            it.copy(
                session = fresh, started = false, liveMapping = false,
                referenceComponentId = null, waiting = false, waitingNote = null,
                updatedAtMillis = System.currentTimeMillis(),
            )
        }
        store.deleteReference(id)
        store.deleteExtraPhotos(id)
        extraThumbnails.remove(id)
        pickingExtraPhoto = false
        pendingExtraPhoto = null
        sparkNotes.remove(id)
        referenceBitmap = null
        mappingError = null
        referenceError = null
    }

    fun setBoardPhoto(id: String, bitmap: Bitmap) {
        store.saveBoardPhoto(id, bitmap)
        thumbnails[id] = thumbnailOf(bitmap)
        if (openProjectId == id) {
            boardBitmap = bitmap
            mappingError = null
        }
        updateProject(id) {
            it.copy(liveMapping = false, updatedAtMillis = System.currentTimeMillis())
        }
    }

    fun removeBoardPhoto(id: String) {
        croppingPhoto = false
        store.deleteBoardPhoto(id)
        thumbnails.remove(id)
        boardBitmap = null
        updateProject(id) {
            it.copy(liveMapping = false, updatedAtMillis = System.currentTimeMillis())
        }
    }

    /** La foto elegida va a la placa o, si se esta por sumar otra, queda a la espera. */
    fun receivePhoto(id: String, bitmap: Bitmap?) {
        val extra = pickingExtraPhoto
        pickingExtraPhoto = false
        if (bitmap == null) return
        if (extra) pendingExtraPhoto = bitmap else setBoardPhoto(id, bitmap)
    }

    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent(),
    ) { uri: Uri? ->
        val id = openProjectId
        if (uri == null || id == null) {
            pickingExtraPhoto = false
        } else {
            photoScope.launch {
                receivePhoto(id, withContext(Dispatchers.IO) { loadBitmapDownscaled(context, uri) })
            }
        }
    }
    // La camara deja la foto completa en un archivo propio (TakePicturePreview
    // devolvia una miniatura: poca resolucion para SAM y Muse Spark). Se carga
    // con el mismo cargador que la galeria (EXIF y 1600 px) y el archivo se borra.
    var captureProjectId by rememberSaveable { mutableStateOf<String?>(null) }
    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture(),
    ) { saved: Boolean ->
        val id = captureProjectId
        captureProjectId = null
        val file = cameraCaptureFile(context)
        photoScope.launch {
            val bmp = if (saved && id != null) {
                withContext(Dispatchers.IO) { loadBitmapDownscaled(context, Uri.fromFile(file)) }
            } else {
                null
            }
            withContext(Dispatchers.IO) { file.delete() }
            if (id != null) receivePhoto(id, bmp) else pickingExtraPhoto = false
        }
    }

    // La camara NUNCA se invoca sin permiso otorgado: sin el, la app se
    // colgaba en el S25. Sin permiso se pide primero; si el usuario lo
    // deniega, se avisa en la tarjeta de foto y queda Galeria como salida.
    var openCameraAfterGrant by remember { mutableStateOf(false) }
    var permissionFromAbout by remember { mutableStateOf(false) }
    var lastRequestedPermission by remember {
        mutableStateOf(Manifest.permission.CAMERA)
    }
    // Se incrementa al resolverse un pedido: About relee el estado real.
    var permissionTick by remember { mutableStateOf(0) }

    fun launchCamera() {
        photoNotice = null
        val id = openProjectId ?: return
        runCatching {
            captureProjectId = id
            cameraLauncher.launch(cameraCaptureUri(context))
        }.onFailure {
            captureProjectId = null
            photoNotice = PhotoNotice.CAMERA_UNAVAILABLE
        }
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

    fun startMapping(project: RepairProject) {
        val id = project.id
        val engine = engineFor(project)
        mappingError = null
        val bmp = boardBitmap
        val key = apiKey()
        if (bmp == null || key == null) {
            mappingError = strings.needPhotoAndKey
            return
        }
        mappingBusy += id
        photoScope.launch {
            try {
                val components = segmentBoardComponents(MetaSamClient(key), bmp)
                if (components.isEmpty()) {
                    if (openProjectId == id) mappingError = strings.samNoMatches
                } else {
                    engine.loadMapping(components)
                    refresh(id, liveMapping = true)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (openProjectId == id) mappingError = strings.samFailed
            } finally {
                mappingBusy -= id
            }
        }
    }

    /** Pide a Muse Spark las siguientes mediciones sobre las hipotesis del motor. */
    suspend fun planFor(id: String, client: MetaSparkClient, bmp: Bitmap, engine: DiagnosisEngine): SparkPlan {
        val s = engine.session
        val extraPhotos = withContext(Dispatchers.IO) {
            s.extraPhotos.mapIndexedNotNull { index, note ->
                store.loadExtraPhoto(id, index)?.let { it to note }
            }
        }
        val text = client.planMeasurements(
            bmp, s.components, s.symptom, s.hypotheses, s.results, language, s.boardType, extraPhotos,
        )
        return SparkOutputParser.parsePlan(
            text, s.components, s.hypotheses,
            takenIds = (s.pendingRequests.map { it.id } + s.results.map { it.requestId }).toSet(),
        )
    }

    /** Aplica el plan y deja la nota: lo que dice Muse Spark o por que no quedo nada para medir. */
    fun applyPlan(id: String, engine: DiagnosisEngine, plan: SparkPlan) {
        engine.applyPlan(plan)
        val note = when {
            engine.session.pendingRequests.isNotEmpty() -> plan.needMoreEvidence
            plan.requests.isNotEmpty() -> strings.liveBlocked
            else -> plan.needMoreEvidence ?: strings.noMeasurementsLive
        }
        if (note != null) sparkNotes[id] = note else sparkNotes.remove(id)
    }

    /**
     * Capa 2 de la guia visual: Muse Spark ubica las puntas de las mediciones
     * pendientes que todavia no las tienen, en segundo plano y todas juntas.
     * Si falla, la medicion se guia igual con la lupa y el multimetro.
     */
    fun requestProbes(id: String, engine: DiagnosisEngine, bmp: Bitmap, key: String) {
        if (id in probeBusy) return
        val missing = engine.session.pendingRequests.filter { it.probes == null }
        if (missing.isEmpty()) return
        probeBusy += id
        photoScope.launch {
            try {
                val components = engine.session.components
                val text = sparkClient(key)
                    .probeGuides(bmp, components, missing, language)
                // Reiniciado o borrado mientras tanto: la respuesta ya no aplica.
                if (engines[id] === engine) {
                    engine.attachProbes(SparkOutputParser.parseProbes(text, components, missing))
                    refresh(id)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Sin puntas sugeridas queda la guia fija y el boton para reintentar.
            } finally {
                probeBusy -= id
            }
        }
    }

    /** Boton de la tarjeta de medicion: reintentar la ubicacion de las puntas. */
    fun locateProbes(project: RepairProject) {
        val bmp = boardBitmap ?: return
        val key = apiKey() ?: return
        requestProbes(project.id, engineFor(project), bmp, key)
    }

    /** Hipotesis y mediciones en vivo con Muse Spark sobre el mapeo SAM. */
    fun startSpark(project: RepairProject) {
        val id = project.id
        val engine = engineFor(project)
        mappingError = null
        val bmp = boardBitmap
        val key = apiKey()
        if (bmp == null || key == null || engine.session.components.isEmpty()) {
            mappingError = strings.sparkFailed
            return
        }
        val symptomAtStart = engine.session.symptom
        sparkBusy += id
        photoScope.launch {
            try {
                val client = sparkClient(key)
                // 1) Pre-escaneo de dano sobre la foto limpia (pregunta
                //    directa); si falla, el diagnostico sigue sin el.
                val findings = runCatching { client.scanDamage(bmp, language) }
                    .getOrDefault(emptyList())
                if (findings.isNotEmpty()) {
                    val (assignDamages, extras) =
                        SparkDamageParser.assign(findings, engine.session.components)
                    if (extras.isNotEmpty()) engine.addComponents(extras)
                    if (assignDamages.isNotEmpty()) {
                        engine.applyVisibleDamage(assignDamages)
                    }
                    refresh(id)
                }
                // 2) Diagnostico con los candidatos ya aumentados/marcados.
                val componentsNow = engine.session.components
                val content = client.diagnose(
                    bmp, componentsNow, symptomAtStart, language, findings, engine.session.boardType)
                val diagnosis = SparkOutputParser.parse(content, componentsNow)
                // Sin tipo elegido, queda el que identifico Muse Spark.
                if (engine.session.boardType == BoardType.UNKNOWN) {
                    diagnosis.boardType?.let(engine::updateBoardType)
                }
                if (diagnosis.hypotheses.isEmpty()) {
                    if (openProjectId == id) {
                        mappingError = diagnosis.needMoreEvidence ?: strings.sparkNoData
                    }
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
                    refresh(id)
                    diagnosis.needMoreEvidence?.let { sparkNotes[id] = it } ?: sparkNotes.remove(id)
                    // Hipotesis sin mediciones utilizables (o solo con tension, que
                    // no se ofrecen sin un chequeo previo): se piden aparte, sobre
                    // estas hipotesis, para que el tecnico siempre sepa que medir.
                    if (engine.session.pendingRequests.isEmpty()) {
                        planBusy += id
                        try {
                            val plan = runCatching { planFor(id, client, bmp, engine) }.getOrNull()
                            // Reiniciado o borrado mientras tanto: la respuesta ya no aplica.
                            if (engines[id] === engine) {
                                if (plan != null && (plan.requests.isNotEmpty() || plan.hypotheses.isNotEmpty())) {
                                    applyPlan(id, engine, plan)
                                    refresh(id)
                                } else {
                                    (plan?.needMoreEvidence ?: diagnosis.needMoreEvidence)
                                        ?.let { sparkNotes[id] = it }
                                }
                            }
                        } finally {
                            planBusy -= id
                        }
                    }
                    // Donde van las puntas de estas mediciones, en segundo plano.
                    if (engines[id] === engine) requestProbes(id, engine, bmp, key)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (openProjectId == id) mappingError = strings.sparkFailed
            } finally {
                sparkBusy -= id
            }
        }
    }

    /** Sin mediciones pendientes ni veredicto: pide las siguientes con lo ya medido. */
    fun askMeasurements(project: RepairProject) {
        val id = project.id
        if (id in planBusy) return
        val engine = engineFor(project)
        val bmp = boardBitmap
        val key = apiKey()
        if (bmp == null || key == null) {
            sparkNotes[id] = strings.askFailed
            return
        }
        planBusy += id
        photoScope.launch {
            try {
                val plan = planFor(id, sparkClient(key), bmp, engine)
                // Reiniciado o borrado mientras tanto: la respuesta ya no aplica.
                if (engines[id] !== engine) return@launch
                applyPlan(id, engine, plan)
                refresh(id)
                requestProbes(id, engine, bmp, key)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (engines[id] === engine) sparkNotes[id] = strings.askFailed
            } finally {
                planBusy -= id
            }
        }
    }

    /** Suma la foto elegida y Muse Spark sigue analizando con ella y con todo lo medido. */
    fun sendExtraPhoto(project: RepairProject, bitmap: Bitmap, note: String) {
        val id = project.id
        val engine = engineFor(project)
        store.saveExtraPhoto(id, engine.session.extraPhotos.size, bitmap)
        engine.addExtraPhoto(note)
        extraThumbnails[id] = extraThumbnails[id].orEmpty() + thumbnailOf(bitmap)
        pendingExtraPhoto = null
        refresh(id)
        askMeasurements(project)
    }

    /** Referencia visual de la reparacion con Muse Image (solo orientativa). */
    fun startReference(project: RepairProject) {
        val id = project.id
        val engine = engineFor(project)
        referenceError = null
        val bmp = boardBitmap
        val key = apiKey()
        val session = engine.session
        val component = session.components
            .firstOrNull { it.id == session.verdictComponentId }
        if (bmp == null || key == null || component == null) {
            referenceError = strings.referenceFailed
            return
        }
        referenceBusy += id
        photoScope.launch {
            try {
                val generated = MetaImageClient(key).repairReference(bmp, component)
                // Reiniciado o borrado mientras tanto: no resucitar la referencia
                // sobre la sesion nueva ni recrear la carpeta del proyecto borrado.
                if (engines[id] !== engine) return@launch
                store.saveReference(id, generated)
                updateProject(id) {
                    it.copy(
                        referenceComponentId = component.id,
                        updatedAtMillis = System.currentTimeMillis(),
                    )
                }
                if (openProjectId == id) referenceBitmap = generated
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (openProjectId == id) referenceError = strings.referenceFailed
            } finally {
                referenceBusy -= id
            }
        }
    }

    fun onLanguageSelected(selected: LanguageMode) {
        mode = selected
        prefs.edit().putString(PREF_LANGUAGE, selected.name).apply()
        // Los proyectos sin empezar con el titulo por defecto pasan al idioma nuevo;
        // los iniciados conservan sus textos (cada placa en el idioma en que se diagnostico).
        val newTitle = appStrings(resolveLanguage(selected, systemLanguage)).newProjectTitle
        if (projects.any { !it.started && it.title in defaultTitles && it.title != newTitle }) {
            projects = projects.map { project ->
                if (!project.started && project.title in defaultTitles) project.copy(title = newTitle) else project
            }
            store.save(projects)
        }
    }

    fun onApiKeyChanged(nowHasKey: Boolean) {
        hasApiKey = nowHasKey
        // Con la clave guardada, de vuelta a la lista para empezar a diagnosticar.
        if (nowHasKey) showSettings = false
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
    // Atras cierra primero los paneles; dentro de un proyecto vuelve a la
    // lista (no sale de la app). Desde la lista, sale como siempre.
    val panelsOpen = showAbout || showSettings || showEasterEgg
    BackHandler(enabled = panelsOpen || openProjectId != null) {
        if (panelsOpen) closePanels() else closeProject()
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
            AboutContent(strings, permissionTick, sparkMode, ::onAboutPermissionTap)
        } else if (showSettings) {
            Text(strings.settingsTitle, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            // Sin clave, la clave primero: sin ella la app no diagnostica.
            if (!hasApiKey) {
                Text(strings.keyIntro)
                ApiKeyCard(strings, hasApiKey, ::onApiKeyChanged)
            }
            LanguageCard(strings, mode, ::onLanguageSelected)
            ThemeCard(strings, themeMode, onThemeModeSelected)
            ReasoningCard(strings, reasoningEffort) { selected ->
                reasoningEffort = selected
                prefs.edit().putString(PREF_EFFORT, selected.name).apply()
            }
            SparkModeCard(strings, sparkMode, contributorAccepted) { selected ->
                selectSparkMode(selected)
                if (selected == SparkMode.CONTRIBUTOR) acceptContributor()
            }
            if (hasApiKey) ApiKeyCard(strings, hasApiKey, ::onApiKeyChanged)
        } else if (currentProject == null) {
            Text(strings.appTagline, fontWeight = FontWeight.Medium)
            Text(strings.safetyIntro)
            ProjectList(
                strings = strings,
                projects = sortedProjects(projects),
                thumbnails = thumbnails,
                onOpen = ::openProject,
                onCreate = ::createProject,
                onDelete = ::deleteProject,
            )
        } else {
            val project = currentProject
            val session = project.session
            ProjectHeader(strings, project, onBack = ::closeProject) { name ->
                updateProject(project.id) { it.renamed(name, System.currentTimeMillis()) }
            }
            if (project.waiting) {
                WaitingCard(strings, project) {
                    updateProject(project.id) { it.resumed(System.currentTimeMillis()) }
                }
            }
            Text(strings.safetyIntro)
            if (!project.started) {
                val photo = boardBitmap
                if (croppingPhoto && photo != null) {
                    PhotoCropper(
                        strings, photo,
                        onApply = { cropped ->
                            setBoardPhoto(project.id, cropped)
                            croppingPhoto = false
                        },
                        onCancel = { croppingPhoto = false },
                    )
                } else {
                    PhotoCard(
                        strings = strings,
                        bitmap = boardBitmap,
                        notice = photoNotice,
                        onGallery = { photoNotice = null; galleryLauncher.launch("image/*") },
                        onCamera = ::openCamera,
                        onOpenSettings = ::openSettings,
                        onRemove = { removeBoardPhoto(project.id) },
                        onCrop = { croppingPhoto = true },
                    )
                }
                // El diagnostico necesita la foto, la clave y el sintoma (Muse Spark
                // lo usa y titula el proyecto); si falta algo, se dice que.
                val ready = boardBitmap != null && hasApiKey
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(strings.symptomTitle, fontWeight = FontWeight.Bold)
                        OutlinedTextField(
                            value = symptomDraft,
                            onValueChange = { symptomDraft = it },
                            label = { Text(strings.symptomLabel) },
                            placeholder = { Text(strings.symptomPlaceholder) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        BoardTypePicker(strings, boardTypeDraft) { boardTypeDraft = it }
                        if (!hasApiKey) {
                            Text(strings.needKey, color = MaterialTheme.colorScheme.error)
                            OutlinedButton(
                                onClick = { showSettings = true; showAbout = false },
                                modifier = Modifier.fillMaxWidth(),
                            ) { Text(strings.loadKey) }
                        } else if (boardBitmap == null) {
                            Text(strings.needPhoto, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        ContributorReminder(strings, sparkMode)
                        Button(
                            onClick = { startDiagnosis(project, boardTypeDraft) },
                            enabled = ready && symptomDraft.isNotBlank(),
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text(strings.diagnoseStart) }
                    }
                }
            } else {
                // key: la seleccion del mapa y los avisos no pasan de un proyecto a otro.
                key(project.id) {
                    SessionContent(
                        engineFor(project), session, strings,
                        refresh = { refresh(project.id) },
                        onRestart = { restartProject(project.id) },
                        onBackToProjects = ::closeProject,
                        boardBitmap = boardBitmap,
                        canLiveMap = boardBitmap != null && hasApiKey,
                        mappingBusy = project.id in mappingBusy,
                        mappingError = mappingError,
                        onMapRequest = { startMapping(project) },
                        sparkBusy = project.id in sparkBusy,
                        onSparkRequest = {
                            // Nada sale en Contributor sin aceptar antes su aviso.
                            if (sparkMode == SparkMode.CONTRIBUTOR && !contributorAccepted) {
                                contributorNoticeFor = project.id
                            } else {
                                startSpark(project)
                            }
                        },
                        sparkMode = sparkMode,
                        note = sparkNotes[project.id],
                        planBusy = project.id in planBusy,
                        onAskMeasurements = { askMeasurements(project) },
                        probesBusy = project.id in probeBusy,
                        onLocateProbes = { locateProbes(project) },
                        referenceBitmap = referenceBitmap
                            .takeIf { project.referenceComponentId != null &&
                                project.referenceComponentId == session.verdictComponentId },
                        referenceBusy = project.id in referenceBusy,
                        referenceError = referenceError,
                        onReferenceRequest = { startReference(project) },
                        extraThumbnails = extraThumbnails[project.id].orEmpty(),
                        addPhoto = {
                            AddPhotoCard(
                                strings = strings,
                                count = session.extraPhotos.size,
                                pending = pendingExtraPhoto,
                                busy = project.id in planBusy,
                                notice = photoNotice,
                                sparkMode = sparkMode,
                                onGallery = {
                                    photoNotice = null
                                    pickingExtraPhoto = true
                                    galleryLauncher.launch("image/*")
                                },
                                onCamera = {
                                    pickingExtraPhoto = true
                                    openCamera()
                                },
                                onOpenSettings = ::openSettings,
                                onSend = { photo, note -> sendExtraPhoto(project, photo, note) },
                                onCancel = { pendingExtraPhoto = null },
                            )
                        },
                    )
                }
                if (contributorNoticeFor == project.id) {
                    ContributorFirstNotice(
                        strings,
                        onAccept = {
                            contributorNoticeFor = null
                            acceptContributor()
                            startSpark(project)
                        },
                        onStandard = {
                            contributorNoticeFor = null
                            selectSparkMode(SparkMode.STANDARD)
                            startSpark(project)
                        },
                        onDismiss = { contributorNoticeFor = null },
                    )
                }
            }
            if (project.started && !project.waiting && session.stage != Stage.VERIFIED) {
                PauseCard(strings, project.id) { note ->
                    updateProject(project.id) { it.paused(note, System.currentTimeMillis()) }
                }
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

/**
 * Standard o Contributor. Contributor nunca se acepta de un toque: como Meta puede
 * entrenar con lo que se envia, pide aceptar el aviso. Volver a Standard es inmediato.
 */
@Composable
private fun SparkModeCard(
    strings: AppStrings,
    mode: SparkMode,
    accepted: Boolean,
    onSelect: (SparkMode) -> Unit,
) {
    var askConfirm by rememberSaveable { mutableStateOf(false) }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(strings.sparkModeTitle, fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    strings.sparkModeStandard to SparkMode.STANDARD,
                    strings.sparkModeContributor to SparkMode.CONTRIBUTOR,
                ).forEach { (label, value) ->
                    val onClick: () -> Unit = {
                        if (value == SparkMode.CONTRIBUTOR && (mode != value || !accepted)) {
                            askConfirm = true
                        } else {
                            onSelect(value)
                        }
                    }
                    val btnModifier = Modifier.weight(1f)
                    val btnPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)
                    if (value == mode) {
                        Button(onClick = onClick, modifier = btnModifier, contentPadding = btnPadding) {
                            Text(label, maxLines = 1)
                        }
                    } else {
                        OutlinedButton(onClick = onClick, modifier = btnModifier, contentPadding = btnPadding) {
                            Text(label, maxLines = 1)
                        }
                    }
                }
            }
            if (mode == SparkMode.CONTRIBUTOR) {
                Text(strings.sparkModeContributorHint, color = MaterialTheme.colorScheme.secondary)
            } else {
                Text(strings.sparkModeStandardHint)
            }
        }
    }
    if (askConfirm) {
        AlertDialog(
            onDismissRequest = { askConfirm = false },
            title = { Text(strings.contributorDialogTitle) },
            // Con desplazamiento: con la letra grande del sistema el aviso no entra.
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    Text(strings.contributorDialogText)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    askConfirm = false
                    onSelect(SparkMode.CONTRIBUTOR)
                }) { Text(strings.contributorAccept) }
            },
            dismissButton = {
                TextButton(onClick = { askConfirm = false }) { Text(strings.contributorCancel) }
            },
        )
    }
}

/**
 * Contributor viene por defecto, pero el primer analisis no sale sin este aviso: se
 * acepta o se pasa a Standard; al cerrarlo sin elegir, no se envia nada.
 */
@Composable
private fun ContributorFirstNotice(
    strings: AppStrings,
    onAccept: () -> Unit,
    onStandard: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(strings.contributorFirstTitle) },
        // Con desplazamiento: con la letra grande del sistema el aviso no entra.
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(strings.contributorDialogText)
                Spacer(Modifier.height(12.dp))
                Text(strings.contributorFirstNote)
            }
        },
        confirmButton = { TextButton(onClick = onAccept) { Text(strings.contributorFirstAccept) } },
        dismissButton = { TextButton(onClick = onStandard) { Text(strings.contributorFirstStandard) } },
    )
}

/** Recordatorio justo antes de enviar: en Contributor, Meta puede entrenar con esto. */
@Composable
private fun ContributorReminder(strings: AppStrings, mode: SparkMode) {
    if (mode == SparkMode.CONTRIBUTOR) {
        Text(strings.contributorReminder, fontSize = 14.sp, color = MaterialTheme.colorScheme.secondary)
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
    // Oculta por defecto; el ojito la muestra mientras se carga (es larga para revisarla
    // a ciegas). No se guarda: al guardarla o al salir de Configuracion vuelve a ocultarse.
    var keyVisible by remember { mutableStateOf(false) }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(strings.apiTitle, fontWeight = FontWeight.Bold)
            Text(if (hasKey) strings.apiLoaded else strings.apiMissing)
            if (!hasKey) {
                OutlinedTextField(
                    value = keyText,
                    onValueChange = { keyText = it },
                    label = { Text("MODEL_API_KEY") },
                    visualTransformation = if (keyVisible) VisualTransformation.None
                    else PasswordVisualTransformation(),
                    // Teclado de contrasena: no sugiere ni aprende la clave, aunque este a la vista.
                    keyboardOptions = KeyboardOptions(autoCorrect = false, keyboardType = KeyboardType.Password),
                    trailingIcon = {
                        IconButton(onClick = { keyVisible = !keyVisible }) {
                            EyeIcon(
                                crossed = !keyVisible,
                                description = if (keyVisible) strings.hideKey else strings.showKey,
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                Button(
                    onClick = {
                        prefs.edit().putString(PREF_API_KEY, keyText.trim()).apply()
                        keyText = ""
                        keyVisible = false
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
 * Ojo dibujado en Canvas, como la ruedita (sin dependencias de iconos): abierto con
 * la clave a la vista, tachado con la clave oculta.
 */
@Composable
private fun EyeIcon(crossed: Boolean, description: String) {
    val color = MaterialTheme.colorScheme.onSurfaceVariant
    Canvas(Modifier.size(24.dp).semantics { contentDescription = description }) {
        val w = size.width
        val h = size.height
        val line = w * 0.08f
        val eye = Path().apply {
            moveTo(w * 0.06f, h * 0.5f)
            quadraticBezierTo(w * 0.5f, h * 0.06f, w * 0.94f, h * 0.5f)
            quadraticBezierTo(w * 0.5f, h * 0.94f, w * 0.06f, h * 0.5f)
            close()
        }
        drawPath(eye, color, style = Stroke(width = line, join = StrokeJoin.Round))
        drawCircle(color, radius = w * 0.13f, center = center)
        if (crossed) {
            drawLine(color, Offset(w * 0.14f, h * 0.12f), Offset(w * 0.86f, h * 0.88f),
                strokeWidth = line, cap = StrokeCap.Round)
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
    sparkMode: SparkMode,
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
    // Con Contributor configurado, Meta puede entrenar con lo que se envia.
    if (sparkMode == SparkMode.CONTRIBUTOR) {
        Text(
            strings.sparkModeContributorHint,
            color = MaterialTheme.colorScheme.error,
            fontWeight = FontWeight.Bold,
        )
    }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("${strings.nameLabel}: BoardPilot")
            Text("${strings.authorLabel}: $APP_AUTHOR")
            Text("${strings.versionLabel}: $versionText")
            Text("${strings.packageLabel}: ${context.packageName}")
            Text("${strings.licenseLabel}: $APP_LICENSE")
            Text(
                "${strings.sparkModeTitle}: " + when (sparkMode) {
                    SparkMode.STANDARD -> strings.sparkModeStandard
                    SparkMode.CONTRIBUTOR -> strings.sparkModeContributor
                },
            )
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
                        else MaterialTheme.colorScheme.error,
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
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(strings.privacyTitle, fontWeight = FontWeight.Bold)
            Text(strings.privacyText)
        }
    }
}

/** "Mis reparaciones": lo ultimo tocado arriba, como una lista de chats. */
@Composable
private fun ProjectList(
    strings: AppStrings,
    projects: List<RepairProject>,
    thumbnails: Map<String, Bitmap>,
    onOpen: (String) -> Unit,
    onCreate: () -> Unit,
    onDelete: (String) -> Unit,
) {
    val now = System.currentTimeMillis()
    val zone = remember { ZoneId.systemDefault() }
    Text(strings.projectsTitle, fontSize = 22.sp, fontWeight = FontWeight.Bold)
    Button(onClick = onCreate, modifier = Modifier.fillMaxWidth()) {
        Text("+ ${strings.newProject}")
    }
    if (projects.isEmpty()) {
        Text(strings.projectsEmpty, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    projects.forEach { project ->
        val subtitle = projectSubtitle(
            project, strings, relativeTime(now, project.updatedAtMillis, zone, strings),
        )
        // key: el estado del gesto es de este proyecto y no pasa al siguiente al borrar.
        key(project.id) {
            SwipeToDelete(strings.swipeDelete, onDelete = { onDelete(project.id) }) {
                ProjectCard(project, subtitle, thumbnails[project.id]) { onOpen(project.id) }
            }
        }
    }
    if (projects.isNotEmpty()) {
        Text(
            strings.swipeHint,
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ProjectCard(
    project: RepairProject,
    subtitle: String,
    thumbnail: Bitmap?,
    onOpen: () -> Unit,
) {
    Card(onClick = onOpen, modifier = Modifier.fillMaxWidth()) {
        Row(
            Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ProjectThumbnail(thumbnail)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    project.title,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    subtitle,
                    fontSize = 14.sp,
                    color = if (projectStatus(project) == ProjectStatus.WAITING) {
                        MaterialTheme.colorScheme.secondary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
        }
    }
}

/**
 * Deslizar hacia cualquier costado revela el fondo rojo de borrar; pasado el
 * 40 % del ancho (no un roce al desplazar la lista) se confirma el borrado.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeToDelete(label: String, onDelete: () -> Unit, content: @Composable () -> Unit) {
    val state = rememberSwipeToDismissBoxState(positionalThreshold = { it * 0.4f })
    LaunchedEffect(state.currentValue) {
        if (state.currentValue != SwipeToDismissBoxValue.Settled) onDelete()
    }
    SwipeToDismissBox(
        state = state,
        backgroundContent = {
            val direction = state.dismissDirection
            if (direction != SwipeToDismissBoxValue.Settled) {
                Box(
                    Modifier.fillMaxSize()
                        .background(Color(0xFFB00020), CardDefaults.shape)
                        .padding(horizontal = 20.dp),
                    contentAlignment = if (direction == SwipeToDismissBoxValue.StartToEnd) {
                        Alignment.CenterStart
                    } else {
                        Alignment.CenterEnd
                    },
                ) {
                    Text(label, color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
        },
    ) { content() }
}

/** Foto de la placa en miniatura; sin foto, la marca de la app. */
@Composable
private fun ProjectThumbnail(bitmap: Bitmap?) {
    val shape = RoundedCornerShape(8.dp)
    Box(
        Modifier.size(56.dp).clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant, shape),
        contentAlignment = Alignment.Center,
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        } else {
            AppMark()
        }
    }
}

/** Volver a la lista y nombre del proyecto: tocarlo permite renombrarlo. */
@Composable
private fun ProjectHeader(
    strings: AppStrings,
    project: RepairProject,
    onBack: () -> Unit,
    onRename: (String) -> Unit,
) {
    var editing by rememberSaveable(project.id) { mutableStateOf(false) }
    var draft by rememberSaveable(project.id) { mutableStateOf(project.title) }
    fun save() {
        onRename(draft)
        editing = false
    }
    TextButton(onClick = onBack, contentPadding = PaddingValues(0.dp)) {
        Text(strings.backToProjects)
    }
    if (editing) {
        OutlinedTextField(
            value = draft,
            onValueChange = { draft = it },
            label = { Text(strings.projectNameLabel) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { save() }),
            modifier = Modifier.fillMaxWidth(),
        )
        Button(onClick = { save() }, modifier = Modifier.fillMaxWidth()) {
            Text(strings.renameSave)
        }
    } else {
        Text(
            "${project.title}  ✎",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.fillMaxWidth().clickable {
                draft = project.title
                editing = true
            },
        )
    }
}

/** Proyecto en espera de un repuesto: que falta y como retomarlo. */
@Composable
private fun WaitingCard(strings: AppStrings, project: RepairProject, onResume: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                strings.statusWaiting +
                    (project.waitingNote?.let { " · " + strings.missingPart(it) } ?: ""),
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.secondary,
            )
            Button(onClick = onResume, modifier = Modifier.fillMaxWidth()) {
                Text(strings.waitingResume)
            }
        }
    }
}

/** Pausa por repuesto: el primer toque pide (opcional) que falta; el segundo la confirma. */
@Composable
private fun PauseCard(strings: AppStrings, projectId: String, onPause: (String) -> Unit) {
    var askNote by rememberSaveable(projectId) { mutableStateOf(false) }
    var note by rememberSaveable(projectId) { mutableStateOf("") }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (askNote) {
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text(strings.waitingNoteLabel) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            OutlinedButton(
                onClick = {
                    if (askNote) {
                        onPause(note)
                        askNote = false
                        note = ""
                    } else {
                        askNote = true
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(strings.waitingMark) }
        }
    }
}

@Composable
private fun SessionContent(
    engine: DiagnosisEngine,
    session: DiagnosisSession,
    strings: AppStrings,
    refresh: () -> Unit,
    onRestart: () -> Unit,
    onBackToProjects: () -> Unit,
    boardBitmap: Bitmap?,
    canLiveMap: Boolean,
    mappingBusy: Boolean,
    mappingError: String?,
    onMapRequest: () -> Unit,
    sparkBusy: Boolean,
    onSparkRequest: () -> Unit,
    sparkMode: SparkMode,
    note: String?,
    planBusy: Boolean,
    onAskMeasurements: () -> Unit,
    probesBusy: Boolean,
    onLocateProbes: () -> Unit,
    referenceBitmap: Bitmap?,
    referenceBusy: Boolean,
    referenceError: String?,
    onReferenceRequest: () -> Unit,
    extraThumbnails: List<Bitmap>,
    addPhoto: @Composable () -> Unit,
) {
    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
    val selected = session.components.firstOrNull { it.id == selectedId }
    var message by remember { mutableStateOf<String?>(null) }

    Text("${strings.stageLabel}: ${strings.stageName(session.stage)}",
        fontWeight = FontWeight.Bold)
    Text("${strings.symptomLabel}: ${session.symptom}")
    Text("${strings.boardTypeLabel}: ${strings.boardTypeName(session.boardType)}")
    mappingError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    if (session.components.isNotEmpty()) {
        // En el mapa: que medir ahora (o el veredicto), lo ya medido y el % de cada hipotesis.
        val measuringStage = session.stage == Stage.HYPOTHESES || session.stage == Stage.MEASURING
        val (focusId, focusLabel) = when {
            measuringStage -> session.currentRequest?.componentId to strings.mapMeasureTag
            session.stage == Stage.VERDICT || session.stage == Stage.REPAIR ||
                session.stage == Stage.VERIFIED -> session.verdictComponentId to strings.mapVerdictTag
            else -> null to null
        }
        // Placas densas: solo las cajas principales (en juego, danadas y las mas
        // grandes), con un boton para ver todas.
        var showAllBoxes by rememberSaveable { mutableStateOf(false) }
        val inPlay = buildSet {
            session.hypotheses.forEach { add(it.componentId) }
            session.pendingRequests.forEach { request ->
                add(request.componentId)
                request.probes?.points?.forEach { add(it.componentId) }
            }
            session.results.forEach { add(it.componentId) }
            session.verdictComponentId?.let(::add)
            selectedId?.let(::add)
        }
        BoardMap(
            strings,
            if (showAllBoxes) session.components else BoardView.mapComponents(session.components, inPlay),
            selected, boardBitmap,
            focusId = focusId,
            focusLabel = focusLabel,
            evidence = BoardView.measuredEvidence(session.results),
            shares = if (measuringStage) BoardView.hypothesisShares(session.hypotheses) else emptyMap(),
        ) { selectedId = it.id }
        if (session.components.size > BoardView.MAP_LIMIT) {
            TextButton(onClick = { showAllBoxes = !showAllBoxes }) {
                Text(
                    if (showAllBoxes) strings.mapMainOnly(BoardView.MAP_LIMIT)
                    else strings.mapShowAll(session.components.size),
                )
            }
        }
        selected?.let { comp ->
            Text(listOfNotNull(
                comp.designator,
                strings.componentKindName(comp.kind),
                comp.visibleDamage?.let { "⚠ $it" },
            ).joinToString(" · "))
        }
    }
    message?.let { Text(it, color = MaterialTheme.colorScheme.error) }

    when (session.stage) {
        Stage.INTAKE -> {
            if (!canLiveMap) Text(strings.needPhotoAndKey, color = MaterialTheme.colorScheme.error)
            Button(
                onClick = onMapRequest,
                enabled = !mappingBusy && canLiveMap,
                modifier = Modifier.fillMaxWidth(),
            ) { if (mappingBusy) BusyLabel(strings.mappingBusy) else Text(strings.mapLiveSam) }
        }

        Stage.MAPPING -> {
            Text(strings.liveMappingNote)
            ContributorReminder(strings, sparkMode)
            Button(
                onClick = onSparkRequest,
                enabled = !sparkBusy,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (sparkBusy) BusyLabel(strings.sparkBusy) else Text(strings.sparkGenerate)
            }
            OutlinedButton(onClick = onRestart, modifier = Modifier.fillMaxWidth()) {
                Text(strings.startOver)
            }
        }

        Stage.HYPOTHESES -> {
            HypothesisList(session, strings)
            ExtraPhotosRow(strings, extraThumbnails)
            val next = session.currentRequest
            when {
                // Hay que medir: se explica el paso y se adelanta la primera medicion.
                next != null -> Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(strings.nextStepTitle, fontWeight = FontWeight.Bold)
                        note?.takeIf { it.isNotBlank() }?.let {
                            Text("Muse Spark: $it", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text(strings.nextStepHint)
                        Text(
                            strings.firstMeasurement(strings.kindName(next.kind),
                                designatorOf(session, next.componentId)),
                            fontWeight = FontWeight.Medium,
                        )
                        Button(
                            onClick = { engine.startMeasuring(); refresh() },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text(strings.startMeasuring) }
                    }
                }
                // Nunca es un callejon sin salida: se piden mediciones a Muse Spark.
                else -> Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            if (session.results.isEmpty()) strings.noMeasurementsLive
                            else strings.measuredNoVerdict,
                            fontWeight = FontWeight.Medium,
                        )
                        note?.takeIf { it.isNotBlank() }?.let {
                            Text("Muse Spark: $it", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Button(
                            onClick = onAskMeasurements,
                            enabled = !planBusy,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            if (planBusy) BusyLabel(strings.askingMeasurements)
                            else Text(strings.askMeasurements)
                        }
                        // Un capacitor seco mide normal: con el dano a la vista decide el tecnico.
                        session.hypotheses
                            .firstOrNull { h ->
                                session.components.any { it.id == h.componentId && !it.visibleDamage.isNullOrBlank() }
                            }
                            ?.let { h ->
                                OutlinedButton(
                                    onClick = { engine.acceptVisibleDamage(h.componentId); refresh() },
                                    modifier = Modifier.fillMaxWidth(),
                                ) { Text(strings.visualVerdict(designatorOf(session, h.componentId))) }
                            }
                        OutlinedButton(onClick = onRestart, modifier = Modifier.fillMaxWidth()) {
                            Text(strings.startOver)
                        }
                    }
                }
            }
            if (next == null) addPhoto()
        }

        Stage.MEASURING -> {
            HypothesisList(session, strings)
            ExtraPhotosRow(strings, extraThumbnails)
            session.currentRequest?.let { req ->
                val target = session.components.firstOrNull { it.id == req.componentId }
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        val done = session.results.size
                        // Solo la pieza: "resistencia · CYL 4" se leia como si CYL 4 fuera una
                        // resistencia. El modo del multimetro lo dice la guia de abajo.
                        Text("${strings.measurementOf(done + 1, done + session.pendingRequests.size)}: " +
                            designatorOf(session, req.componentId),
                            fontWeight = FontWeight.Bold)
                        // Guia visual: la lupa junto a las instrucciones.
                        if (target != null) {
                            ProbeLens(
                                strings, boardBitmap,
                                session.components, target, req.probes,
                            )
                            ProbeCaption(strings, req, probesBusy, onLocateProbes)
                        }
                        MeterGuide(strings, req.kind)
                        Text(req.instruction)
                        if (req.normalDescription.isNotBlank()) {
                            Text("${strings.normalDescLabel}: ${req.normalDescription}")
                        }
                        if (req.abnormalDescription.isNotBlank()) {
                            Text("${strings.abnormalDescLabel}: ${req.abnormalDescription}")
                        }
                        ZoneChip(strings, req.zone)
                        Text("⚠ ${strings.gateWarning(req.gate, req.zone)}",
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
            // "Reemplazar Q3 (MOSFET)"; sin tipo conocido, solo la pieza.
            val kindName = comp?.let { strings.componentKindName(it.kind) }
            Text("${strings.verdictPrefix} ${comp?.designator ?: "?"}" + (kindName?.let { " ($it)" } ?: ""),
                fontWeight = FontWeight.Bold, fontSize = 18.sp)
            // Lo que Muse Spark dijo reemplazar para la hipotesis confirmada.
            val replacementText = session.hypotheses
                .filter { it.componentId == session.verdictComponentId && it.fix.isNotBlank() }
                .maxByOrNull { it.confidence }?.fix
                ?: strings.liveReplacement(comp?.designator ?: "?")
            Text(replacementText)
            // Los valores los propone el modelo: manda lo impreso en la pieza original.
            Text(strings.replacementCheck, color = MaterialTheme.colorScheme.onSurfaceVariant)
            ReferenceCard(
                strings, referenceBitmap, referenceBusy,
                referenceError, onReferenceRequest,
            )
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
            OutlinedButton(onClick = onBackToProjects) { Text(strings.backHome) }
        }
    }
}

@Composable
private fun HypothesisList(session: DiagnosisSession, strings: AppStrings) {
    if (session.hypotheses.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(strings.hypothesesTitle, fontWeight = FontWeight.Bold)
        session.hypotheses.forEach { h ->
            // Descartada por una medicion: queda a la vista, sin el detalle.
            if (h.ruledOut) {
                Text(
                    "✗ ${h.title} — ${(h.confidence * 100).toInt()}%",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textDecoration = TextDecoration.LineThrough,
                )
                return@forEach
            }
            Text("• ${h.title} — ${(h.confidence * 100).toInt()}%" +
                if (h.confirmedByMeasurement) strings.confirmedSuffix else "")
            if (h.rationale.isNotBlank()) {
                Text("    ${h.rationale}", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (h.fix.isNotBlank()) {
                Text("    ${strings.fixLabel}: ${h.fix}", color = MaterialTheme.colorScheme.secondary)
            }
        }
        // Lo que falta para 100 %: crece a medida que las mediciones descartan hipotesis.
        val other = ((1.0 - session.hypotheses.sumOf { it.confidence }) * 100).toInt()
        if (other >= OTHER_CAUSE_MIN_PERCENT) {
            Text("• ${strings.otherCause} — $other%", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Las fotos que ya se sumaron a la reparacion, en miniatura. */
@Composable
private fun ExtraPhotosRow(strings: AppStrings, thumbnails: List<Bitmap>) {
    if (thumbnails.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(strings.extraPhotosTitle, fontWeight = FontWeight.Bold)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            thumbnails.forEach { thumbnail ->
                Image(
                    bitmap = thumbnail.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    contentScale = ContentScale.Crop,
                )
            }
        }
    }
}

/**
 * Sin falla a la vista, el tecnico suma una foto (mas cerca, del lado de soldadura) y
 * Muse Spark la analiza junto con la primera y con todo lo medido.
 */
@Composable
private fun AddPhotoCard(
    strings: AppStrings,
    count: Int,
    pending: Bitmap?,
    busy: Boolean,
    notice: PhotoNotice?,
    sparkMode: SparkMode,
    onGallery: () -> Unit,
    onCamera: () -> Unit,
    onOpenSettings: () -> Unit,
    onSend: (Bitmap, String) -> Unit,
    onCancel: () -> Unit,
) {
    var note by rememberSaveable { mutableStateOf("") }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(strings.addPhotoTitle, fontWeight = FontWeight.Bold)
            when {
                pending != null -> {
                    Image(
                        bitmap = pending.asImageBitmap(),
                        contentDescription = null,
                        modifier = Modifier.fillMaxWidth().height(180.dp),
                        contentScale = ContentScale.Crop,
                    )
                    OutlinedTextField(
                        value = note,
                        onValueChange = { note = it },
                        label = { Text(strings.addPhotoNote) },
                        placeholder = { Text(strings.addPhotoNoteExample) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    ContributorReminder(strings, sparkMode)
                    Button(
                        onClick = { onSend(pending, note); note = "" },
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(strings.addPhotoSend) }
                    OutlinedButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) {
                        Text(strings.contributorCancel)
                    }
                }
                count >= MAX_EXTRA_PHOTOS -> Text(strings.addPhotoLimit(MAX_EXTRA_PHOTOS))
                else -> {
                    Text(strings.addPhotoHint)
                    notice?.let {
                        Text(
                            when (it) {
                                PhotoNotice.CAMERA_DENIED -> strings.cameraPermissionDenied
                                PhotoNotice.CAMERA_PERMANENT -> strings.cameraPermissionPermanent
                                PhotoNotice.CAMERA_UNAVAILABLE -> strings.cameraUnavailable
                            },
                            color = MaterialTheme.colorScheme.error,
                        )
                        if (it == PhotoNotice.CAMERA_PERMANENT) {
                            OutlinedButton(
                                onClick = onOpenSettings,
                                modifier = Modifier.fillMaxWidth(),
                            ) { Text(strings.openAppSettings) }
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = onGallery, enabled = !busy, modifier = Modifier.weight(1f)) {
                            Text(strings.photoGallery, maxLines = 1)
                        }
                        OutlinedButton(onClick = onCamera, enabled = !busy, modifier = Modifier.weight(1f)) {
                            Text(strings.photoCamera, maxLines = 1)
                        }
                    }
                }
            }
        }
    }
}

/** Nombre legible del componente ("CYL 2") en vez de su id interno. */
private fun designatorOf(session: DiagnosisSession, componentId: String): String =
    session.components.firstOrNull { it.id == componentId }?.designator ?: componentId

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
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
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
                if (busy) BusyLabel(strings.referenceBusy) else Text(strings.referenceGenerate)
            }
        }
    }
}

/**
 * Mapa de la placa: foto con las cajas de componentes tocables. Se acerca
 * pellizcando con dos dedos (foto y cajas juntas); con zoom, un dedo lo
 * mueve y tocar una caja la sigue seleccionando.
 */
@Composable
private fun BoardMap(
    strings: AppStrings,
    components: List<BoardComponent>,
    selected: BoardComponent?,
    bitmap: Bitmap?,
    focusId: String? = null,
    focusLabel: String? = null,
    evidence: Map<String, MeasurementOutcome> = emptyMap(),
    shares: Map<String, Int> = emptyMap(),
    onSelect: (BoardComponent) -> Unit,
) {
    val ratio = bitmap?.let {
        (it.width.toFloat() / it.height.toFloat()).coerceIn(0.55f, 1.9f)
    }
    var view by remember(bitmap) { mutableStateOf(MapZoom()) }
    BoxWithConstraints(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF380B14))
            .then(
                if (ratio != null) Modifier.aspectRatio(ratio)
                else Modifier.height(240.dp),
            )
            .pointerInput(bitmap) { detectMapPinch({ view }) { view = it } },
    ) {
        // La foto entera centrada, sin recortar: las cajas van sobre su rectangulo
        // real (antes, con fotos muy altas o anchas, quedaban corridas).
        val fit = if (bitmap != null) {
            BoardView.fit(maxWidth.value, maxHeight.value, bitmap.width.toFloat(), bitmap.height.toFloat())
        } else {
            FitRect(0f, 0f, maxWidth.value, maxHeight.value)
        }
        val w = fit.width.dp
        val h = fit.height.dp
        val accent = MaterialTheme.colorScheme.primary
        val selectedAccent = Color(0xFFFFB300)
        Box(
            Modifier.fillMaxSize().graphicsLayer {
                scaleX = view.zoom
                scaleY = view.zoom
                translationX = view.panX
                translationY = view.panY
            },
        ) {
            Box(Modifier.offset(x = fit.left.dp, y = fit.top.dp).size(width = w, height = h)) {
                if (bitmap != null) {
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.FillBounds,
                    )
                }
                components.forEach { comp ->
                    val isSel = comp.id == selected?.id
                    val isFocus = comp.id == focusId
                    val compW = w * comp.box.w
                    val compH = h * comp.box.h
                    // Con dano visible el marco va en rojo de error; seleccionado o
                    // a medir (o el del veredicto) pisa en ambar.
                    val frame = when {
                        isSel || isFocus -> selectedAccent
                        comp.visibleDamage != null -> Color(0xFFB00020)
                        else -> accent
                    }
                    Box(
                        Modifier
                            .offset(x = w * comp.box.x, y = h * comp.box.y)
                            .size(width = compW, height = compH)
                            .background(
                                frame.copy(alpha = if (isSel || isFocus) 0.34f else 0.16f),
                                RoundedCornerShape(3.dp),
                            )
                            .border(if (isFocus) 3.dp else 2.dp, frame, RoundedCornerShape(3.dp))
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
                                    color = if (isSel || isFocus) Color.Black else Color.White,
                                    maxLines = 1,
                                )
                            }
                        }
                    }
                }
                // Mapa de evidencia, encima de las cajas: lo ya medido (✓ ✗ ?), el %
                // de cada hipotesis y la medicion en curso o el veredicto.
                components.forEach { comp ->
                    val left = w * comp.box.x
                    val top = h * comp.box.y
                    val bottom = top + h * comp.box.h
                    val share = shares[comp.id]?.let { "$it%" }
                    if (comp.id == focusId && focusLabel != null) {
                        // Arriba si hay lugar (si no, debajo), con el % en la misma etiqueta.
                        MapTag(
                            listOfNotNull("▶ $focusLabel", share).joinToString(" · "),
                            selectedAccent, Color.Black,
                            if (top >= 28.dp) Modifier.offset(x = left, y = top - 2.dp).placedAbove()
                            else Modifier.offset(x = left, y = bottom + 2.dp),
                        )
                    } else if (share != null) {
                        MapTag(share, Color.Black.copy(alpha = 0.75f), Color.White,
                            Modifier.offset(x = left, y = bottom + 2.dp))
                    }
                }
                // Lo medido va encima de todo: ninguna etiqueta tapa un ✗.
                components.forEach { comp ->
                    evidence[comp.id]?.let { outcome ->
                        EvidenceChip(
                            outcome,
                            Modifier.offset(x = w * (comp.box.x + comp.box.w) - 9.dp, y = h * comp.box.y - 9.dp),
                        )
                    }
                }
            }
        }
        if (view.zoomed) {
            Text(
                strings.mapFit,
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.TopEnd)
                    .padding(8.dp)
                    .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
                    .clickable { view = MapZoom() }
                    .padding(horizontal = 10.dp, vertical = 4.dp),
            )
        }
    }
}

/**
 * El contenido sube su propio alto: queda con el borde inferior en la posicion
 * dada (encima de una caja), mida lo que mida con la letra grande del sistema.
 */
private fun Modifier.placedAbove(): Modifier = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints)
    layout(placeable.width, placeable.height) { placeable.place(0, -placeable.height) }
}

/** Tipo de placa al ingreso: detectar con Muse Spark (por defecto) o elegirlo. */
@Composable
private fun BoardTypePicker(strings: AppStrings, value: BoardType, onChange: (BoardType) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Box {
            OutlinedButton(onClick = { open = true }, modifier = Modifier.fillMaxWidth()) {
                Text("${strings.boardTypeLabel}: ${strings.boardTypeName(value)} ▾")
            }
            DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                (listOf(BoardType.UNKNOWN) + BoardPlaybooks.detectable).forEach { type ->
                    DropdownMenuItem(
                        text = { Text(strings.boardTypeName(type)) },
                        onClick = { onChange(type); open = false },
                    )
                }
            }
        }
        if (value == BoardType.UNKNOWN) {
            Text(strings.boardTypeHint, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Donde se mide, con el color del riesgo: rojo en el lado de red, negro y ambar en alta tension. */
@Composable
private fun ZoneChip(strings: AppStrings, zone: HazardZone) {
    val (background, color) = when (zone) {
        HazardZone.PRIMARY -> Color(0xFFB00020) to Color.White
        HazardZone.SECONDARY -> Color(0xFF2E7D32) to Color.White
        HazardZone.HIGH_VOLTAGE -> Color.Black to Color(0xFFFFB300)
    }
    Text(
        "${strings.zoneLabel}: ${strings.zoneName(zone)}",
        color = color,
        fontWeight = FontWeight.Bold,
        fontSize = 14.sp,
        modifier = Modifier.background(background, RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 3.dp),
    )
}

/** Etiqueta chica sobre el mapa (% de hipotesis, "medir", "veredicto"). */
@Composable
private fun MapTag(text: String, background: Color, textColor: Color, modifier: Modifier) {
    Text(
        text,
        fontSize = 10.sp,
        fontWeight = FontWeight.Bold,
        color = textColor,
        maxLines = 1,
        modifier = modifier
            .background(background, RoundedCornerShape(3.dp))
            .padding(horizontal = 4.dp, vertical = 1.dp),
    )
}

/**
 * Lo que dio la medicion sobre un componente: ✓ normal, ✗ anormal, ? no claro.
 * Dibujado con trazos: con la letra grande del sistema un texto no entra.
 */
@Composable
private fun EvidenceChip(outcome: MeasurementOutcome, modifier: Modifier) {
    val fill = when (outcome) {
        MeasurementOutcome.NORMAL -> Color(0xFF2E7D32)
        MeasurementOutcome.ABNORMAL -> Color(0xFFB00020)
        MeasurementOutcome.UNCLEAR -> Color(0xFFFFB300)
    }
    val ink = if (outcome == MeasurementOutcome.UNCLEAR) Color.Black else Color.White
    Canvas(modifier.size(18.dp)) {
        val r = size.minDimension / 2f
        val c = center
        val s = r * 0.48f
        val w = 2.dp.toPx()
        drawCircle(fill, r, c)
        drawCircle(Color.White, r - 0.75.dp.toPx(), c, style = Stroke(1.5.dp.toPx()))
        when (outcome) {
            MeasurementOutcome.NORMAL -> {
                val mid = Offset(c.x - s * 0.25f, c.y + s * 0.65f)
                drawLine(ink, Offset(c.x - s, c.y), mid, w, StrokeCap.Round)
                drawLine(ink, mid, Offset(c.x + s, c.y - s * 0.65f), w, StrokeCap.Round)
            }
            MeasurementOutcome.ABNORMAL -> {
                val d = s * 0.8f
                drawLine(ink, Offset(c.x - d, c.y - d), Offset(c.x + d, c.y + d), w, StrokeCap.Round)
                drawLine(ink, Offset(c.x - d, c.y + d), Offset(c.x + d, c.y - d), w, StrokeCap.Round)
            }
            MeasurementOutcome.UNCLEAR -> {
                // Signo de pregunta: gancho, tallo y punto.
                drawArc(
                    ink, startAngle = 180f, sweepAngle = 250f, useCenter = false,
                    topLeft = Offset(c.x - s * 0.55f, c.y - s), size = Size(s * 1.1f, s * 1.0f),
                    style = Stroke(w, cap = StrokeCap.Round),
                )
                drawLine(ink, Offset(c.x, c.y + s * 0.05f), Offset(c.x, c.y + s * 0.35f), w, StrokeCap.Round)
                drawCircle(ink, w * 0.65f, Offset(c.x, c.y + s * 0.85f))
            }
        }
    }
}

/** Proporcion (ancho/alto) del mapa cuando no hay foto. */
private const val NO_PHOTO_MAP_ASPECT = 1.5f

/**
 * Lupa de la medicion: la zona de la foto alrededor del componente medido,
 * ampliada junto a las instrucciones, con su caja y donde va cada punta (roja
 * y negra, con su terminal). Sin foto dibuja las cajas sobre el fondo.
 */
@Composable
private fun ProbeLens(
    strings: AppStrings,
    bitmap: Bitmap?,
    components: List<BoardComponent>,
    target: BoardComponent,
    guide: ProbeGuide?,
) {
    val image = remember(bitmap) { bitmap?.asImageBitmap() }
    val measurer = rememberTextMeasurer()
    val amber = Color(0xFFFFB300)
    val points = guide?.points.orEmpty()
    val photoAspect = bitmap?.let { it.width.toFloat() / it.height.toFloat() } ?: NO_PHOTO_MAP_ASPECT
    val region = remember(target, guide, photoAspect) {
        BoardView.lensRegion(target.box, points.map { it.x to it.y }, photoAspect)
    }
    // La punta negra puede ir en otro componente (p. ej. la masa del primario).
    val others = points.mapNotNull { p ->
        components.firstOrNull { it.id == p.componentId && it.id != target.id }
    }.distinct()
    // Cuadrada para piezas altas, apaisada para anchas.
    Canvas(
        Modifier.fillMaxWidth()
            .aspectRatio(BoardView.lensAspect(region, photoAspect))
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF380B14)),
    ) {
        // Zona de la foto en px y donde cae dentro de la lupa, sin deformar.
        val dst = if (image != null) {
            val sx = (region.x * image.width).toInt().coerceIn(0, image.width - 1)
            val sy = (region.y * image.height).toInt().coerceIn(0, image.height - 1)
            val sw = (region.w * image.width).toInt().coerceIn(1, image.width - sx)
            val sh = (region.h * image.height).toInt().coerceIn(1, image.height - sy)
            val fit = BoardView.fit(size.width, size.height, sw.toFloat(), sh.toFloat())
            drawImage(
                image,
                srcOffset = IntOffset(sx, sy),
                srcSize = IntSize(sw, sh),
                dstOffset = IntOffset(fit.left.toInt(), fit.top.toInt()),
                dstSize = IntSize(fit.width.toInt(), fit.height.toInt()),
                filterQuality = FilterQuality.High,
            )
            fit
        } else {
            BoardView.fit(size.width, size.height, region.w * NO_PHOTO_MAP_ASPECT, region.h)
        }
        fun px(x: Float) = dst.left + (x - region.x) / region.w * dst.width
        fun py(y: Float) = dst.top + (y - region.y) / region.h * dst.height
        fun label(text: String, at: Offset, background: Color, color: Color) {
            val layout = measurer.measure(
                text, TextStyle(color = color, fontSize = 11.sp, fontWeight = FontWeight.Bold),
            )
            val pad = 3.dp.toPx()
            val lw = layout.size.width + 2 * pad
            val lh = layout.size.height + pad
            val x = at.x.coerceIn(0f, (size.width - lw).coerceAtLeast(0f))
            val y = at.y.coerceIn(0f, (size.height - lh).coerceAtLeast(0f))
            drawRoundRect(background, Offset(x, y), Size(lw, lh), CornerRadius(4.dp.toPx()))
            drawText(layout, topLeft = Offset(x + pad, y + pad / 2))
        }
        fun outline(c: BoardComponent, color: Color, width: Float) {
            val topLeft = Offset(px(c.box.x), py(c.box.y))
            drawRect(
                color, topLeft,
                Size(c.box.w / region.w * dst.width, c.box.h / region.h * dst.height),
                style = Stroke(width),
            )
            label(c.designator, topLeft - Offset(0f, 20.dp.toPx()), color, Color.Black)
        }
        others.forEach { outline(it, amber.copy(alpha = 0.7f), 2.dp.toPx()) }
        outline(target, amber, 3.dp.toPx())
        // Solo los marcadores: el terminal de cada punta va en la leyenda de abajo,
        // asi nada tapa la caja ni las etiquetas en piezas chicas o placas densas.
        val r = 11.dp.toPx()
        fun marker(p: ProbePoint, fill: Color, tag: String) {
            val c = Offset(px(p.x), py(p.y))
            drawCircle(fill, r, c)
            drawCircle(Color.White, r, c, style = Stroke(2.dp.toPx()))
            val t = measurer.measure(
                tag, TextStyle(color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold),
            )
            drawText(t, topLeft = c - Offset(t.size.width / 2f, t.size.height / 2f))
        }
        guide?.red?.let { marker(it, Color(0xFFE53935), strings.redTag) }
        guide?.black?.let { marker(it, Color(0xFF111111), strings.blackTag) }
    }
}

/** Una linea de la leyenda de la lupa: el marcador y el terminal donde va. */
@Composable
private fun ProbeLegend(color: Color, tag: String, terminal: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        JackDot(color)
        Text(if (terminal.isBlank()) tag else "$tag: $terminal", fontSize = 14.sp)
    }
}

/**
 * Texto de un pedido a la IA en curso con un circulo que gira y pasa del color
 * principal al ambar y vuelta: que se vea que la app esta trabajando.
 */
@Composable
private fun BusyLabel(
    text: String,
    color: Color = Color.Unspecified,
    fontSize: TextUnit = TextUnit.Unspecified,
) {
    val colors = MaterialTheme.colorScheme
    val spinnerColor by rememberInfiniteTransition(label = "busy").animateColor(
        initialValue = colors.primary,
        targetValue = colors.secondary,
        animationSpec = infiniteRepeatable(tween(durationMillis = 900), RepeatMode.Reverse),
        label = "busyColor",
    )
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        CircularProgressIndicator(Modifier.size(18.dp), color = spinnerColor, strokeWidth = 2.5.dp)
        Text(text, color = color, fontSize = fontSize)
    }
}

/**
 * Debajo de la lupa: que las puntas son sugerencia de IA, si se estan pidiendo,
 * si las patas no se ven, y en diodos sin polaridad visible, que se pruebe en los
 * dos sentidos.
 */
@Composable
private fun ProbeCaption(
    strings: AppStrings,
    request: MeasurementRequest,
    busy: Boolean,
    onLocate: () -> Unit,
) {
    val guide = request.probes
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    guide?.red?.let { ProbeLegend(Color(0xFFE53935), strings.redTag, it.terminal) }
    guide?.black?.let { ProbeLegend(Color(0xFF111111), strings.blackTag, it.terminal) }
    when {
        guide == null && busy -> BusyLabel(strings.probesLoading, color = muted, fontSize = 14.sp)
        guide == null -> TextButton(onClick = onLocate) { Text(strings.probesLocate) }
        guide.points.isEmpty() ->
            Text("⚠ ${strings.probesHidden} ${guide.note}".trim(), fontSize = 14.sp)
        else -> Text(
            strings.probesAiNote +
                guide.note.takeIf { it.isNotBlank() }?.let { " $it" }.orEmpty(),
            color = muted,
            fontSize = 14.sp,
        )
    }
    if (request.kind == MeasurementKind.DIODE &&
        (guide == null || guide.points.isEmpty() || guide.uncertainPolarity)
    ) {
        Text(strings.diodeBothWays, color = muted, fontSize = 14.sp)
    }
}

/** Como preparar el multimetro: el selector segun el tipo y la entrada de cada punta. */
@Composable
private fun MeterGuide(strings: AppStrings, kind: MeasurementKind) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        MeterDial(kind, Modifier.size(52.dp))
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(strings.meterMode(kind), fontWeight = FontWeight.Medium)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                JackDot(Color(0xFFE53935))
                Text(strings.redJack, fontSize = 14.sp)
                Spacer(Modifier.width(6.dp))
                JackDot(Color(0xFF111111))
                Text(strings.blackJack, fontSize = 14.sp)
            }
        }
    }
}

@Composable
private fun JackDot(color: Color) {
    Box(
        Modifier.size(12.dp)
            .background(color, CircleShape)
            .border(1.5.dp, MaterialTheme.colorScheme.outline, CircleShape),
    )
}

/** El simbolo del selector del multimetro: Ω, pitido, diodo o V de continua. */
@Composable
private fun MeterDial(kind: MeasurementKind, modifier: Modifier) {
    val ink = MaterialTheme.colorScheme.onSurface
    val ring = MaterialTheme.colorScheme.primary
    val measurer = rememberTextMeasurer()
    Canvas(modifier) {
        val c = center
        val r = size.minDimension / 2f
        drawCircle(ring, r - 1.5.dp.toPx(), c, style = Stroke(3.dp.toPx()))
        val s = r * 0.45f
        val stroke = 2.4.dp.toPx()
        fun symbol(text: String, shift: Offset = Offset.Zero) {
            val layout = measurer.measure(
                text, TextStyle(color = ink, fontSize = 22.sp, fontWeight = FontWeight.Bold),
            )
            drawText(layout, topLeft = c + shift - Offset(layout.size.width / 2f, layout.size.height / 2f))
        }
        when (kind) {
            MeasurementKind.RESISTANCE -> symbol("Ω")
            MeasurementKind.CONTINUITY -> {
                // Un punto y tres ondas: el pitido.
                val dot = Offset(c.x - s * 0.8f, c.y)
                drawCircle(ink, stroke * 0.9f, dot)
                for (i in 1..3) {
                    val rr = s * 0.42f * i
                    drawArc(
                        ink, startAngle = -45f, sweepAngle = 90f, useCenter = false,
                        topLeft = Offset(dot.x - rr, dot.y - rr), size = Size(rr * 2, rr * 2),
                        style = Stroke(stroke, cap = StrokeCap.Round),
                    )
                }
            }
            MeasurementKind.DIODE -> {
                // Triangulo hacia el catodo y su barra, con las dos patas.
                val path = Path().apply {
                    moveTo(c.x - s * 0.7f, c.y - s * 0.7f)
                    lineTo(c.x + s * 0.4f, c.y)
                    lineTo(c.x - s * 0.7f, c.y + s * 0.7f)
                    close()
                }
                drawPath(path, ink)
                drawLine(ink, Offset(c.x + s * 0.55f, c.y - s * 0.75f),
                    Offset(c.x + s * 0.55f, c.y + s * 0.75f), stroke * 1.2f)
                drawLine(ink, Offset(c.x - s * 1.15f, c.y), Offset(c.x - s * 0.7f, c.y), stroke)
                drawLine(ink, Offset(c.x + s * 0.55f, c.y), Offset(c.x + s * 1.05f, c.y), stroke)
            }
            MeasurementKind.VOLTAGE_DC -> {
                // "V" y el simbolo de continua: linea llena sobre linea punteada.
                symbol("V", Offset(-s * 0.5f, 0f))
                val x0 = c.x + s * 0.05f
                val x1 = c.x + s * 1.05f
                drawLine(ink, Offset(x0, c.y - s * 0.25f), Offset(x1, c.y - s * 0.25f), stroke)
                val dash = (x1 - x0) / 5f
                for (i in 0 until 3) {
                    val dx = x0 + i * dash * 2
                    drawLine(ink, Offset(dx, c.y + s * 0.2f), Offset(dx + dash, c.y + s * 0.2f), stroke)
                }
            }
        }
    }
}

/**
 * Pellizco del mapa: dos dedos acercan y alejan; con zoom, un dedo mueve
 * (pasado el umbral de arrastre, para no robarle el toque a las cajas). Sin
 * zoom, un dedo sigue desplazando la pantalla como siempre.
 */
private suspend fun PointerInputScope.detectMapPinch(
    current: () -> MapZoom,
    onChange: (MapZoom) -> Unit,
) {
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false)
        var dragged = Offset.Zero
        var panning = false
        do {
            val event = awaitPointerEvent()
            val fingers = event.changes.count { it.pressed }
            val zoom = current()
            if (fingers >= 2 || (fingers == 1 && zoom.zoomed)) {
                val zoomChange = event.calculateZoom()
                val pan = event.calculatePan()
                if (!panning) {
                    dragged += pan
                    panning = fingers >= 2 || dragged.getDistance() > viewConfiguration.touchSlop
                }
                if (panning && (zoomChange != 1f || pan != Offset.Zero)) {
                    val c = event.calculateCentroid(useCurrent = false)
                    onChange(zoom.afterPinch(c.x, c.y, pan.x, pan.y, zoomChange,
                        size.width.toFloat(), size.height.toFloat()))
                    event.changes.forEach { if (it.positionChanged()) it.consume() }
                }
            }
        } while (event.changes.any { it.pressed })
    }
}

/**
 * Recorte como en las apps de imagenes: la foto entera con un recuadro libre
 * encima. Las esquinas y los bordes lo ajustan y arrastrando por dentro se
 * mueve; la foto se gira libre con dos dedos o con la barra (y de a 90 grados
 * con el boton). Al girar, el recuadro pasa a ser el mas grande sin esquinas
 * vacias, y nunca se puede sacar de la foto. Lo de adentro es la foto nueva.
 */
@Composable
private fun PhotoCropper(
    strings: AppStrings,
    source: Bitmap,
    onApply: (Bitmap) -> Unit,
    onCancel: () -> Unit,
) {
    val image = remember(source) { source.asImageBitmap() }
    var angle by rememberSaveable { mutableStateOf(0f) }
    val photo = remember(source, angle) {
        RotatedPhoto(source.width.toFloat(), source.height.toFloat(), angle)
    }
    var box by rememberSaveable(angle, stateSaver = CropBoxSaver) {
        mutableStateOf(photo.largestInside())
    }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(strings.cropTitle, fontWeight = FontWeight.Bold)
            Text(strings.cropHint, fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            // El area sigue la forma de la foto: apaisada o vertical, que se vea grande.
            Box(
                Modifier.fillMaxWidth()
                    .aspectRatio((photo.boundsW / photo.boundsH).coerceIn(0.75f, 1.6f))
                    .background(Color(0xFF15080B), RoundedCornerShape(8.dp))
                    // Dos dedos giran la foto (libre); el recuadro se reacomoda solo.
                    .pointerInput(Unit) {
                        awaitEachGesture {
                            awaitFirstDown(requireUnconsumed = false)
                            do {
                                val event = awaitPointerEvent()
                                if (event.changes.count { it.pressed } >= 2) {
                                    val turn = event.calculateRotation()
                                    if (turn != 0f) angle = normalizedAngle(angle + turn)
                                    event.changes.forEach { it.consume() }
                                }
                            } while (event.changes.any { it.pressed })
                        }
                    }
                    // Un dedo: esquinas, bordes o mover el recuadro.
                    .pointerInput(photo) {
                        awaitEachGesture {
                            val down = awaitFirstDown()
                            val view = CropView(size.width.toFloat(), size.height.toFloat(),
                                photo.boundsW, photo.boundsH, CROP_INSET.toPx())
                            val at = view.toPhoto(down.position)
                            // Fuera del recuadro el dedo sigue desplazando la pantalla. El
                            // agarre de esquinas y bordes no tapa el centro de un recuadro
                            // chico: ahi siempre se puede tocar para moverlo.
                            val reach = min(28.dp.toPx() / view.scale, min(box.width, box.height) / 4f)
                            val handle = box.hit(at.x, at.y, reach) ?: return@awaitEachGesture
                            down.consume()
                            val minSize = 56.dp.toPx() / view.scale
                            while (true) {
                                val event = awaitPointerEvent()
                                // Si se apoya un segundo dedo, es un giro: se suelta el recuadro.
                                if (event.changes.count { it.pressed } != 1) break
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                if (!change.pressed) break
                                val d = change.positionChange()
                                if (d != Offset.Zero) {
                                    box = box.dragged(handle, d.x / view.scale, d.y / view.scale,
                                        photo, minSize)
                                    change.consume()
                                }
                            }
                        }
                    },
            ) {
                Canvas(Modifier.fillMaxSize()) {
                    val view = CropView(size.width, size.height, photo.boundsW, photo.boundsH,
                        CROP_INSET.toPx())
                    withTransform({
                        translate(view.left + view.width / 2f, view.top + view.height / 2f)
                        rotate(angle, Offset.Zero)
                        scale(view.scale, view.scale, Offset.Zero)
                    }) {
                        drawImage(
                            image,
                            dstOffset = IntOffset(-image.width / 2, -image.height / 2),
                            dstSize = IntSize(image.width, image.height),
                            filterQuality = FilterQuality.High,
                        )
                    }
                    drawCropOverlay(view, box)
                }
            }
            Text(
                "${box.width.roundToInt()} × ${box.height.roundToInt()} px · " +
                    "${angle.roundToInt()}°",
                fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            // Ajuste fino (enderezar) dentro del cuarto de vuelta actual.
            Row(verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(strings.cropTurn, fontSize = 14.sp)
                Slider(
                    value = fineOf(angle),
                    onValueChange = { angle = normalizedAngle(quarterOf(angle) + it) },
                    valueRange = -45f..45f,
                    colors = SliderDefaults.colors(
                        inactiveTrackColor = MaterialTheme.colorScheme.outline,
                    ),
                    modifier = Modifier.weight(1f),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { angle = normalizedAngle(angle + 90f) },
                    modifier = Modifier.weight(1f),
                ) { Text(strings.cropRotate, maxLines = 1) }
                OutlinedButton(
                    onClick = {
                        angle = 0f
                        box = CropBox(0f, 0f, source.width.toFloat(), source.height.toFloat())
                    },
                    modifier = Modifier.weight(1f),
                ) { Text(strings.cropReset, maxLines = 1) }
            }
            Button(
                onClick = { onApply(renderCrop(source, photo, box)) },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(strings.cropApply) }
            OutlinedButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) {
                Text(strings.cropCancel)
            }
        }
    }
}

/** Margen alrededor de la foto en el editor, para poder agarrar las esquinas. */
private val CROP_INSET = 20.dp

private val CropBoxSaver = Saver<CropBox, List<Float>>(
    save = { listOf(it.left, it.top, it.right, it.bottom) },
    restore = { CropBox(it[0], it[1], it[2], it[3]) },
)

/** Donde se dibuja la foto girada dentro del editor: encajada y centrada, con margen. */
private class CropView(boxW: Float, boxH: Float, photoW: Float, photoH: Float, inset: Float) {
    val scale = min((boxW - 2f * inset) / photoW, (boxH - 2f * inset) / photoH)
    val width = photoW * scale
    val height = photoH * scale
    val left = (boxW - width) / 2f
    val top = (boxH - height) / 2f

    fun toPhoto(p: Offset) = Offset((p.x - left) / scale, (p.y - top) / scale)

    fun toView(x: Float, y: Float) = Offset(left + x * scale, top + y * scale)
}

/** Afuera oscurecido, borde, reticula de tercios, esquinas en L y marcas en los bordes. */
private fun DrawScope.drawCropOverlay(view: CropView, box: CropBox) {
    val tl = view.toView(box.left, box.top)
    val br = view.toView(box.right, box.bottom)
    val w = br.x - tl.x
    val h = br.y - tl.y
    val dim = Color.Black.copy(alpha = 0.6f)
    drawRect(dim, Offset(view.left, view.top), Size(view.width, tl.y - view.top))
    drawRect(dim, Offset(view.left, br.y), Size(view.width, view.top + view.height - br.y))
    drawRect(dim, Offset(view.left, tl.y), Size(tl.x - view.left, h))
    drawRect(dim, Offset(br.x, tl.y), Size(view.left + view.width - br.x, h))
    val grid = Color.White.copy(alpha = 0.45f)
    for (k in 1..2) {
        drawLine(grid, Offset(tl.x + w * k / 3f, tl.y), Offset(tl.x + w * k / 3f, br.y), 1.dp.toPx())
        drawLine(grid, Offset(tl.x, tl.y + h * k / 3f), Offset(br.x, tl.y + h * k / 3f), 1.dp.toPx())
    }
    drawRect(Color.White, tl, Size(w, h), style = Stroke(1.5.dp.toPx()))
    val len = min(22.dp.toPx(), min(w, h) / 2f)
    val thick = 4.dp.toPx()
    fun corner(c: Offset, sx: Float, sy: Float) {
        drawLine(Color.White, c, Offset(c.x + sx * len, c.y), thick, StrokeCap.Square)
        drawLine(Color.White, c, Offset(c.x, c.y + sy * len), thick, StrokeCap.Square)
    }
    corner(tl, 1f, 1f)
    corner(Offset(br.x, tl.y), -1f, 1f)
    corner(Offset(tl.x, br.y), 1f, -1f)
    corner(br, -1f, -1f)
    val bar = min(18.dp.toPx(), min(w, h) / 3f) / 2f
    val midX = (tl.x + br.x) / 2f
    val midY = (tl.y + br.y) / 2f
    drawLine(Color.White, Offset(midX - bar, tl.y), Offset(midX + bar, tl.y), thick)
    drawLine(Color.White, Offset(midX - bar, br.y), Offset(midX + bar, br.y), thick)
    drawLine(Color.White, Offset(tl.x, midY - bar), Offset(tl.x, midY + bar), thick)
    drawLine(Color.White, Offset(br.x, midY - bar), Offset(br.x, midY + bar), thick)
}

/**
 * La foto nueva: lo que quedo dentro del recuadro, girada y recortada en un
 * solo paso a la resolucion real de la original (sin estirar).
 */
private fun renderCrop(source: Bitmap, photo: RotatedPhoto, box: CropBox): Bitmap {
    val out = Bitmap.createBitmap(
        box.width.roundToInt().coerceAtLeast(1),
        box.height.roundToInt().coerceAtLeast(1),
        Bitmap.Config.ARGB_8888,
    )
    val canvas = android.graphics.Canvas(out)
    canvas.drawColor(0xFF15080B.toInt())
    val matrix = android.graphics.Matrix().apply {
        postTranslate(-source.width / 2f, -source.height / 2f)
        postRotate(photo.angle)
        postTranslate(photo.boundsW / 2f - box.left, photo.boundsH / 2f - box.top)
    }
    canvas.drawBitmap(source, matrix, android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG))
    return out
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
    onCrop: () -> Unit,
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
                    color = MaterialTheme.colorScheme.error,
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
                OutlinedButton(onClick = onCrop, modifier = Modifier.fillMaxWidth()) {
                    Text(strings.photoCrop)
                }
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
 * Mismos conceptos y orden que tools/smoke_map.py (mantener en sync).
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

/**
 * Foto de galeria o camara lista para el diagnostico: derecha segun su EXIF
 * (la camara suele guardar los pixeles acostados con la etiqueta Orientation)
 * y con el lado mayor en [maxDimension] px, sin agrandar las chicas.
 */
private fun loadBitmapDownscaled(context: Context, uri: Uri, maxDimension: Int = 1600): Bitmap? {
    val resolver = context.contentResolver
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    resolver.openInputStream(uri)?.use {
        BitmapFactory.decodeStream(it, null, bounds)
    }
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    val options = BitmapFactory.Options().apply {
        inSampleSize = PhotoLoading.sampleSize(maxOf(bounds.outWidth, bounds.outHeight), maxDimension)
    }
    val decoded = resolver.openInputStream(uri)?.use {
        BitmapFactory.decodeStream(it, null, options)
    } ?: return null
    val orientation = runCatching {
        resolver.openInputStream(uri)?.use {
            ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, PhotoLoading.NORMAL)
        }
    }.getOrNull() ?: PhotoLoading.NORMAL
    val fix = PhotoLoading.exifFix(orientation)
    val scale = PhotoLoading.scaleFor(maxOf(decoded.width, decoded.height), maxDimension)
    if (fix.isIdentity && scale == 1f) return decoded
    // Escala, giro y espejo en una sola pasada.
    val matrix = android.graphics.Matrix().apply {
        setScale(scale, scale)
        postRotate(fix.degrees)
        if (fix.mirror) postScale(-1f, 1f)
    }
    return Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
}

/** Archivo temporal donde la app de camara deja la foto completa. */
private fun cameraCaptureFile(context: Context): File =
    File(context.cacheDir, "camera/capture.jpg").apply { parentFile?.mkdirs() }

/** El archivo temporal compartido con la app de camara (FileProvider, solo esa Uri). */
private fun cameraCaptureUri(context: Context): Uri = FileProvider.getUriForFile(
    context, "${context.packageName}.fileprovider", cameraCaptureFile(context),
)

/** Miniatura de la lista a partir de la foto recien elegida. */
private fun thumbnailOf(bitmap: Bitmap): Bitmap {
    val longest = maxOf(bitmap.width, bitmap.height)
    if (longest <= THUMB_PX) return bitmap
    val scale = THUMB_PX.toFloat() / longest
    return Bitmap.createScaledBitmap(
        bitmap,
        (bitmap.width * scale).toInt().coerceAtLeast(1),
        (bitmap.height * scale).toInt().coerceAtLeast(1),
        true,
    )
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
