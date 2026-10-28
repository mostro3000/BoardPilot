package ar.com.mt0.boardpilot.domain

import java.util.Base64

/** Estado visible de un proyecto en la lista, derivado de la sesión y la pausa. */
enum class ProjectStatus { IN_PROGRESS, WAITING, REPAIRED }

/**
 * Una reparación guardada, como los chats: su propia sesión de diagnóstico,
 * foto y referencia (archivos por id fuera de este modelo). `waiting` la
 * marca el técnico cuando falta un repuesto, con la nota de qué falta.
 */
data class RepairProject(
    val id: String,
    val title: String,
    val createdAtMillis: Long,
    val updatedAtMillis: Long,
    val waiting: Boolean = false,
    val waitingNote: String? = null,
    val started: Boolean = false,
    val liveMapping: Boolean = false,
    val referenceComponentId: String? = null,
    val session: DiagnosisSession,
)

fun projectStatus(project: RepairProject): ProjectStatus = when {
    project.session.stage == Stage.VERIFIED -> ProjectStatus.REPAIRED
    project.waiting -> ProjectStatus.WAITING
    else -> ProjectStatus.IN_PROGRESS
}

/** Título automático a partir del síntoma (editable después por el usuario). */
fun autoProjectTitle(symptom: String, fallback: String): String {
    val clean = symptom.trim().replace(Regex("\\s+"), " ")
    if (clean.isEmpty()) return fallback
    return if (clean.length <= 48) clean else clean.take(47).trimEnd() + "…"
}

/** Orden de la lista: lo último tocado, primero; desempate por creación. */
fun sortedProjects(projects: List<RepairProject>): List<RepairProject> =
    projects.sortedWith(
        compareByDescending<RepairProject> { it.updatedAtMillis }
            .thenByDescending { it.createdAtMillis },
    )

/**
 * Vuelca el avance del diagnóstico al proyecto. Si la sesión cambió, el
 * técnico volvió a trabajar sobre la placa: la pausa por repuesto se levanta.
 */
fun RepairProject.withProgress(
    session: DiagnosisSession,
    started: Boolean,
    liveMapping: Boolean,
    nowMillis: Long,
): RepairProject {
    val resumed = waiting && session != this.session
    return copy(
        session = session,
        started = started,
        liveMapping = liveMapping,
        updatedAtMillis = nowMillis,
        waiting = waiting && !resumed,
        waitingNote = if (resumed) null else waitingNote,
    )
}

/** Pausa por repuesto: la nota (qué falta) es opcional. */
fun RepairProject.paused(note: String, nowMillis: Long): RepairProject = copy(
    waiting = true,
    waitingNote = note.trim().replace(Regex("\\s+"), " ").ifEmpty { null },
    updatedAtMillis = nowMillis,
)

fun RepairProject.resumed(nowMillis: Long): RepairProject =
    copy(waiting = false, waitingNote = null, updatedAtMillis = nowMillis)

/** Renombrar: un nombre vacío no pisa el anterior. */
fun RepairProject.renamed(name: String, nowMillis: Long): RepairProject {
    val clean = name.trim().replace(Regex("\\s+"), " ")
    return if (clean.isEmpty() || clean == title) this
    else copy(title = clean, updatedAtMillis = nowMillis)
}

/**
 * Proyecto recién creado y sin tocar (sin iniciar, sin renombrar, sin foto
 * nueva): como un chat vacío, no vale la pena guardarlo al volver a la lista.
 */
fun RepairProject.isUntouched(defaultTitles: Set<String>): Boolean =
    !started && !waiting && updatedAtMillis == createdAtMillis && title in defaultTitles

/**
 * Al iniciar el diagnóstico, un proyecto que conserva el título por defecto
 * (en cualquier idioma) pasa a llamarse como su síntoma; uno renombrado no.
 */
fun RepairProject.withAutoTitle(defaultTitles: Set<String>): RepairProject =
    if (title in defaultTitles) copy(title = autoProjectTitle(session.symptom, title))
    else this

/**
 * Migración de la sesión única anterior a los proyectos: un diagnóstico
 * iniciado y legible pasa a ser el proyecto 1 tal cual (con su referencia);
 * si solo quedaba la foto, el proyecto 1 la conserva sin iniciar, con
 * [freshSession]. Sin diagnóstico ni foto, no se crea ningún proyecto.
 */
fun legacyProject(
    sessionText: String?,
    started: Boolean,
    liveMapping: Boolean,
    referenceComponentId: String?,
    hasPhoto: Boolean,
    freshSession: DiagnosisSession,
    id: String,
    nowMillis: Long,
    fallbackTitle: String,
): RepairProject? {
    val resumable = sessionText?.takeIf { started }
        ?.let { runCatching { SessionCodec.decode(it) }.getOrNull() }
    if (resumable == null && !hasPhoto) return null
    return RepairProject(
        id = id,
        title = resumable?.let { autoProjectTitle(it.symptom, fallbackTitle) } ?: fallbackTitle,
        createdAtMillis = nowMillis,
        updatedAtMillis = nowMillis,
        started = resumable != null,
        liveMapping = resumable != null && liveMapping,
        referenceComponentId = referenceComponentId?.takeIf { resumable != null },
        session = resumable ?: freshSession,
    )
}

/**
 * Serializa la lista completa de proyectos en un texto versionado ("BPP1").
 * Cada proyecto va en una línea de campos separados por `|`, con los textos
 * en Base64 URL sin padding (`~` = nulo) y la sesión anidada ya codificada
 * por [SessionCodec] y vuelta a codificar como un solo campo. Al decodificar
 * se omiten las entradas dañadas; ante un encabezado desconocido, lista vacía.
 */
object ProjectCodec {
    private const val HEADER = "BPP1"

    fun encode(projects: List<RepairProject>): String = buildString {
        appendLine(HEADER)
        appendLine(projects.size.toString())
        projects.forEach { p ->
            appendLine(
                listOf(
                    enc(p.id), enc(p.title),
                    p.createdAtMillis.toString(), p.updatedAtMillis.toString(),
                    if (p.waiting) "1" else "0", encNullable(p.waitingNote),
                    if (p.started) "1" else "0", if (p.liveMapping) "1" else "0",
                    encNullable(p.referenceComponentId),
                    enc(SessionCodec.encode(p.session)),
                ).joinToString("|"),
            )
        }
    }.trimEnd()

    fun decode(text: String): List<RepairProject> {
        val lines = text.lineSequence().iterator()
        if (!lines.hasNext() || lines.next() != HEADER) return emptyList()
        if (!lines.hasNext()) return emptyList()
        val count = lines.next().toIntOrNull() ?: return emptyList()
        val out = mutableListOf<RepairProject>()
        repeat(count) {
            if (!lines.hasNext()) return out
            runCatching { out += decodeProject(lines.next()) }
        }
        return out
    }

    private fun decodeProject(line: String): RepairProject {
        val f = line.split("|")
        require(f.size == 10) { "proyecto con ${f.size} campos" }
        return RepairProject(
            id = dec(f[0]),
            title = dec(f[1]),
            createdAtMillis = f[2].toLong(),
            updatedAtMillis = f[3].toLong(),
            waiting = f[4] == "1",
            waitingNote = decNullable(f[5]),
            started = f[6] == "1",
            liveMapping = f[7] == "1",
            referenceComponentId = decNullable(f[8]),
            session = SessionCodec.decode(dec(f[9])),
        )
    }

    private fun enc(value: String): String =
        Base64.getUrlEncoder().withoutPadding().encodeToString(value.toByteArray())

    private fun encNullable(value: String?): String = value?.let { enc(it) } ?: "~"

    private fun dec(value: String): String =
        String(Base64.getUrlDecoder().decode(value))

    private fun decNullable(value: String): String? = if (value == "~") null else dec(value)
}
