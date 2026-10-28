package ar.com.mt0.boardpilot

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.AtomicFile
import ar.com.mt0.boardpilot.domain.DiagnosisSession
import ar.com.mt0.boardpilot.domain.ProjectCodec
import ar.com.mt0.boardpilot.domain.RepairProject
import ar.com.mt0.boardpilot.domain.legacyProject
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import java.util.concurrent.Callable
import java.util.concurrent.Executors

private const val PROJECTS_DIR = "projects"
private const val INDEX_FILE = "index.bpp"
private const val BOARD_FILE = "board.jpg"
private const val REFERENCE_FILE = "reference.jpg"

// Sesion unica anterior a los proyectos (hasta el 28 oct): se migra una vez.
private const val LEGACY_PREF_SESSION = "diagnosis_session"
private const val LEGACY_PREF_STARTED = "session_started"
private const val LEGACY_PREF_LIVE_MAPPING = "live_mapping"
private const val LEGACY_PREF_REFERENCE_FOR = "reference_component"
private const val LEGACY_BOARD_PHOTO = "board_photo.jpg"
private const val LEGACY_REFERENCE_PHOTO = "reference_photo.jpg"

/**
 * Reparaciones guardadas en archivos privados: un indice "BPP1" con todos
 * los proyectos y una carpeta por id con su foto y su referencia. Hay una
 * sola instancia por proceso: la rotacion reusa la lista en memoria, y toda
 * lectura o escritura de archivos pasa por un unico hilo, en orden, asi
 * nadie lee una foto o un indice a medio escribir.
 */
class ProjectStore private constructor(context: Context) {
    private val filesDir = context.applicationContext.filesDir
    private val root = File(filesDir, PROJECTS_DIR)
    private val io = Executors.newSingleThreadExecutor()
    private var cache: List<RepairProject>? = null

    /**
     * Lista guardada; la primera vez migra la sesion unica anterior
     * ([freshSession] y [fallbackTitle] sirven si solo quedaba la foto).
     */
    @Synchronized
    fun load(
        prefs: SharedPreferences,
        freshSession: DiagnosisSession,
        fallbackTitle: String,
        nowMillis: Long,
    ): List<RepairProject> {
        cache?.let { return it }
        val loaded = io.submit(Callable {
            val index = File(root, INDEX_FILE)
            if (index.exists() || File(root, "$INDEX_FILE.bak").exists()) {
                runCatching { ProjectCodec.decode(String(AtomicFile(index).readFully())) }
                    .getOrDefault(emptyList())
            } else {
                migrateLegacy(prefs, freshSession, fallbackTitle, nowMillis)
            }
        }).get()
        cache = loaded
        return loaded
    }

    @Synchronized
    fun save(projects: List<RepairProject>) {
        cache = projects
        val text = ProjectCodec.encode(projects)
        io.execute { writeIndex(text) }
    }

    fun newId(): String = UUID.randomUUID().toString()

    fun saveBoardPhoto(id: String, bitmap: Bitmap) = io.execute {
        writeJpeg(File(dir(id), BOARD_FILE), bitmap)
    }

    fun deleteBoardPhoto(id: String) = io.execute { File(dir(id), BOARD_FILE).delete() }

    fun hasBoardPhoto(id: String): Boolean = File(dir(id), BOARD_FILE).exists()

    fun saveReference(id: String, bitmap: Bitmap) = io.execute {
        writeJpeg(File(dir(id), REFERENCE_FILE), bitmap)
    }

    fun deleteReference(id: String) = io.execute { File(dir(id), REFERENCE_FILE).delete() }

    /** Bloquea hasta leer: llamar fuera del hilo principal. */
    fun loadBoardPhoto(id: String): Bitmap? = read { decode(File(dir(id), BOARD_FILE), 0) }

    fun loadReference(id: String): Bitmap? = read { decode(File(dir(id), REFERENCE_FILE), 0) }

    fun loadThumbnail(id: String, maxPx: Int): Bitmap? =
        read { decode(File(dir(id), BOARD_FILE), maxPx) }

    /** Borra la carpeta del proyecto (la lista ya se guardo sin el). */
    fun deleteFiles(id: String) = io.execute { dir(id).deleteRecursively() }

    private fun dir(id: String) = File(root, id)

    private fun <T> read(block: () -> T): T? =
        runCatching { io.submit(Callable { block() }).get() }.getOrNull()

    private fun writeIndex(text: String) {
        root.mkdirs()
        writeAtomically(File(root, INDEX_FILE)) { it.write(text.toByteArray()) }
    }

    private fun writeJpeg(file: File, bitmap: Bitmap) {
        file.parentFile?.mkdirs()
        writeAtomically(file) { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }
    }

    private fun writeAtomically(file: File, write: (FileOutputStream) -> Unit) {
        val atomic = AtomicFile(file)
        val out = runCatching { atomic.startWrite() }.getOrNull() ?: return
        try {
            write(out)
            atomic.finishWrite(out)
        } catch (e: Exception) {
            atomic.failWrite(out)
        }
    }

    /** Decodifica una foto guardada; con [maxPx] > 0, reducida para miniatura. */
    private fun decode(file: File, maxPx: Int): Bitmap? {
        val atomic = AtomicFile(file)
        if (!file.exists() && !File(file.path + ".bak").exists()) return null
        val bytes = runCatching { atomic.readFully() }.getOrNull() ?: return null
        if (maxPx <= 0) return BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxPx) sample *= 2
        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
    }

    /**
     * La sesion unica anterior pasa a ser el proyecto 1 con su foto y su
     * referencia. Se copia, se escribe el indice y recien entonces se borra
     * lo viejo: si algo se corta a mitad, la proxima apertura lo reintenta.
     */
    private fun migrateLegacy(
        prefs: SharedPreferences,
        freshSession: DiagnosisSession,
        fallbackTitle: String,
        nowMillis: Long,
    ): List<RepairProject> {
        val legacyBoard = File(filesDir, LEGACY_BOARD_PHOTO)
        val legacyReference = File(filesDir, LEGACY_REFERENCE_PHOTO)
        val project = legacyProject(
            sessionText = prefs.getString(LEGACY_PREF_SESSION, null),
            started = prefs.getBoolean(LEGACY_PREF_STARTED, false),
            liveMapping = prefs.getBoolean(LEGACY_PREF_LIVE_MAPPING, false),
            referenceComponentId = prefs.getString(LEGACY_PREF_REFERENCE_FOR, null)
                ?.takeIf { legacyReference.isFile },
            hasPhoto = legacyBoard.isFile,
            freshSession = freshSession,
            id = newId(),
            nowMillis = nowMillis,
            fallbackTitle = fallbackTitle,
        )
        val projects = listOfNotNull(project)
        project?.let { p ->
            dir(p.id).mkdirs()
            if (legacyBoard.isFile) legacyBoard.copyTo(File(dir(p.id), BOARD_FILE), true)
            if (p.referenceComponentId != null) {
                legacyReference.copyTo(File(dir(p.id), REFERENCE_FILE), true)
            }
        }
        writeIndex(ProjectCodec.encode(projects))
        prefs.edit()
            .remove(LEGACY_PREF_SESSION)
            .remove(LEGACY_PREF_STARTED)
            .remove(LEGACY_PREF_LIVE_MAPPING)
            .remove(LEGACY_PREF_REFERENCE_FOR)
            .apply()
        legacyBoard.delete()
        legacyReference.delete()
        return projects
    }

    companion object {
        @Volatile
        private var instance: ProjectStore? = null

        fun get(context: Context): ProjectStore =
            instance ?: synchronized(this) {
                instance ?: ProjectStore(context).also { instance = it }
            }
    }
}
