package ar.com.mt0.boardpilot.domain

import kotlin.math.abs
import kotlin.math.max

/** Resultado util de Muse Spark ya validado contra los candidatos SAM. */
data class SparkDiagnosis(
    val hypotheses: List<Hypothesis> = emptyList(),
    val requests: List<MeasurementRequest> = emptyList(),
    val needMoreEvidence: String? = null,
    /** Dano visible por componente ya mapeado: id -> descripcion localizada. */
    val damages: Map<String, String> = emptyMap(),
    /** Componentes sinteticos por dano visible SIN caja SAM (p. ej. DMG 1). */
    val extraComponents: List<BoardComponent> = emptyList(),
    /** Tipo de placa que identifico Muse Spark (null si no lo dijo o no se entiende). */
    val boardType: BoardType? = null,
)

/** Como seguir segun Muse Spark: mediciones siguientes y lo nuevo que encontro. */
data class SparkPlan(
    val requests: List<MeasurementRequest> = emptyList(),
    val needMoreEvidence: String? = null,
    /** Causas nuevas, si las anteriores quedaron descartadas. */
    val hypotheses: List<Hypothesis> = emptyList(),
    /** Dano visible hallado al seguir analizando: id -> descripcion localizada. */
    val damages: Map<String, String> = emptyMap(),
)

/**
 * Interpreta la salida JSON de Muse Spark de forma defensiva:
 * - Tolera cercos de codigo Markdown y texto alrededor del objeto JSON.
 * - Es tolerante con lo que el modelo suele variar sin cambiar el sentido:
 *   el componente por su designador ("CYL 2") en vez del id, el tipo de
 *   medicion con otro nombre ("Continuidad", "VOLTAGE") o una hipotesis
 *   destino que no existe (se usa la del mismo componente).
 * - Descarta lo que no se puede usar con seguridad: componentes inexistentes,
 *   tipos desconocidos, mediciones en alterna (red electrica) y textos vacios.
 * - Los textos que lee el tecnico no muestran ids internos ([humanize]) y el
 *   arreglo sugerido no repite la condicion ([cleanFix]).
 * - La compuerta de seguridad la fija el dominio segun el tipo de medicion
 *   ([safetyGateFor]), nunca el modelo.
 * - Limita cantidades (4 hipotesis, 5 mediciones) y normaliza ids unicos.
 * Ante JSON invalido devuelve resultados vacios.
 */
object SparkOutputParser {
    private const val MAX_HYPOTHESES = 4
    private const val MAX_MEASUREMENTS = 5
    private const val MAX_PLAN = 3
    private const val MAX_NEW_HYPOTHESES = 2

    /** Id reservado para el dano visible que SAM no llego a encuadrar. */
    const val UNMAPPED_DAMAGE_ID = "dmg1-spark"

    fun parse(text: String, components: List<BoardComponent>): SparkDiagnosis {
        val root = parseRoot(text) ?: return SparkDiagnosis()

        // Dano visible sin caja: Spark estima la caja y pasa a ser candidato
        // sintetico DMG (id libre siguiente si el pre-escaneo ya creo alguno),
        // o se asigna al componente que ya lo contiene.
        val damages = LinkedHashMap<String, String>()
        val extraComponents = mutableListOf<BoardComponent>()
        (root["unmappedDamage"] as? Map<*, *>)?.let { unmapped ->
            val description = unmapped.str("description")?.takeIf { it.isNotBlank() }
            val x = unmapped.num("x")
            val y = unmapped.num("y")
            val w = unmapped.num("w")
            val h = unmapped.num("h")
            val box = if (x != null && y != null && w != null && h != null) {
                SparkDamageParser.validBox(x, y, w, h)
            } else {
                null
            }
            if (description != null && box != null) {
                val assigned = SparkDamageParser.assign(
                    listOf(DamageFinding(description, box)),
                    components,
                )
                assigned.first.forEach { (id, damage) -> damages.putIfAbsent(id, damage) }
                extraComponents += assigned.second
            }
        }
        val known = components + extraComponents
        // El modelo puede referirse a la zona sin caja por el id reservado.
        val reserved = extraComponents.firstOrNull()?.id

        // Dano visible sobre componentes ya mapeados por SAM.
        damages.putAll(parseDamages(root["damages"], known, reserved).filterKeys { it !in damages })
        val hypotheses = parseHypotheses(root["hypotheses"], known, reserved, emptySet(), MAX_HYPOTHESES)

        return SparkDiagnosis(
            hypotheses = hypotheses,
            requests = requests(root["measurements"], known, reserved, hypotheses, emptySet())
                .take(MAX_MEASUREMENTS),
            needMoreEvidence = root.str("needMoreEvidence")?.takeIf { it.isNotBlank() }
                ?.let { humanize(it, hypotheses, known) },
            damages = damages,
            extraComponents = extraComponents,
            boardType = parseBoardType(root.str("boardType")),
        )
    }

    private fun parseDamages(
        raw: Any?,
        components: List<BoardComponent>,
        reserved: String?,
    ): Map<String, String> {
        val damages = LinkedHashMap<String, String>()
        (raw as? List<*>).orEmpty().forEach { item ->
            val m = item as? Map<*, *> ?: return@forEach
            val id = resolve(m.str("componentId"), components, reserved) ?: return@forEach
            val damage = m.str("damage")?.takeIf { it.isNotBlank() } ?: return@forEach
            damages.putIfAbsent(id, damage)
        }
        return damages
    }

    /** Hipotesis validadas, con ids que no chocan con [takenIds] y textos sin ids internos. */
    private fun parseHypotheses(
        raw: Any?,
        components: List<BoardComponent>,
        reserved: String?,
        takenIds: Set<String>,
        limit: Int,
    ): List<Hypothesis> {
        val usedIds = takenIds.toMutableSet()
        val parsed = (raw as? List<*>).orEmpty()
            .mapIndexedNotNull { index, item ->
                val m = item as? Map<*, *> ?: return@mapIndexedNotNull null
                val componentId = resolve(m.str("componentId"), components, reserved)
                    ?: return@mapIndexedNotNull null
                val title = m.str("title")?.takeIf { it.isNotBlank() }
                    ?: return@mapIndexedNotNull null
                // Confianza: un numero fuera de escala no se "arregla" a 0.99,
                // porque eso empata todas las hipotesis y arruina el orden.
                // 85 se lee como 85 %; negativo, no finito (1e999) o mayor que
                // 100 descarta la hipotesis.
                val rawConfidence = (m["confidence"] as? Number)?.toDouble()
                val confidence = when {
                    rawConfidence == null -> 0.25
                    !rawConfidence.isFinite() || rawConfidence < 0.0 || rawConfidence > 100.0 ->
                        return@mapIndexedNotNull null
                    rawConfidence > 1.0 -> rawConfidence / 100.0
                    else -> rawConfidence
                }.coerceIn(0.01, 0.99)
                var id = m.str("id")?.takeIf { it.isNotBlank() } ?: "h${index + 1}"
                while (id in usedIds) id += "b"
                usedIds += id
                Hypothesis(
                    id = id,
                    title = title,
                    componentId = componentId,
                    confidence = confidence,
                    rationale = m.str("rationale").orEmpty(),
                    fix = m.firstStr("fix", "replacement", "repair").orEmpty(),
                )
            }
            .take(limit)
            .map { it.copy(title = humanize(it.title, emptyList(), components)) }
        // Textos para el tecnico: sin ids internos y el arreglo como accion directa.
        return parsed.map {
            it.copy(
                rationale = humanize(it.rationale, parsed, components),
                fix = cleanFix(humanize(it.fix, parsed, components)),
            )
        }
    }

    /** Tipo de placa como lo escribe el modelo: el nombre exacto o un sinonimo (ES/EN). */
    fun parseBoardType(raw: String?): BoardType? {
        val key = raw?.trim()?.uppercase()?.takeIf { it.isNotEmpty() } ?: return null
        BoardType.entries.firstOrNull { it.name == key && it != BoardType.UNKNOWN }?.let { return it }
        return when {
            "SMPS" in key || "POWER SUPPLY" in key || "FUENTE" in key || "CHARGER" in key ->
                BoardType.SMPS
            "AUDIO" in key || "AMPLIF" in key -> BoardType.AUDIO_AMP
            "TV" in key || "MONITOR" in key || "T-CON" in key || "TCON" in key ||
                "MAIN BOARD" in key || "MAINBOARD" in key -> BoardType.TV_MONITOR
            "INVERTER" in key || "MOTOR" in key || "COMPRESSOR" in key -> BoardType.INVERTER_APPLIANCE
            "CONTROL" in key || "APPLIANCE" in key -> BoardType.CONTROL_BOARD
            "OTHER" in key || "OTRA" in key -> BoardType.OTHER
            else -> null
        }
    }

    /**
     * Zona donde se mide, como la escribe el modelo; si falta o no se entiende,
     * la mas estricta (lado de red).
     */
    fun parseZone(raw: String?): HazardZone {
        // Por palabras enteras, nunca por subcadena: "below_transformer" contiene
        // "low" y no es el lado frio. "Low voltage" solo tampoco alcanza: una
        // salida de baja tension no aislada sigue referida a la red; ante la
        // duda, el lado de red.
        val tokens = raw?.trim()?.uppercase()
            ?.replace(Regex("[^A-ZÁÉÍÓÚÑ]+"), "_")?.trim('_')
            ?.split("_")?.filter { it.isNotEmpty() }?.toSet().orEmpty()
        return when {
            tokens.isEmpty() -> HazardZone.PRIMARY
            "HIGH" in tokens || "HV" in tokens || "ALTA" in tokens -> HazardZone.HIGH_VOLTAGE
            tokens.any {
                it.startsWith("SECOND") || it.startsWith("SECUND") ||
                    it == "COLD" || it == "FRÍO" || it == "FRIO" ||
                    it == "SELV" || it == "ISOLATED" || it.startsWith("AISLAD")
            } -> HazardZone.SECONDARY
            else -> HazardZone.PRIMARY
        }
    }

    /**
     * Como seguir: causas nuevas ademas de las [hypotheses] ya cargadas, dano visible
     * y las siguientes mediciones sobre unas u otras; los ids de medicion no repiten
     * ninguno de [takenIds] (mediciones pendientes o ya hechas).
     */
    fun parsePlan(
        text: String,
        components: List<BoardComponent>,
        hypotheses: List<Hypothesis>,
        takenIds: Set<String>,
    ): SparkPlan {
        val root = parseRoot(text) ?: return SparkPlan()
        val fresh = parseHypotheses(
            root["newHypotheses"], components, null, hypotheses.map { it.id }.toSet(), MAX_NEW_HYPOTHESES,
        )
        val all = hypotheses + fresh
        return SparkPlan(
            requests = requests(root["measurements"], components, null, all, takenIds)
                .take(MAX_PLAN),
            needMoreEvidence = root.str("needMoreEvidence")?.takeIf { it.isNotBlank() }
                ?.let { humanize(it, all, components) },
            hypotheses = fresh,
            damages = parseDamages(root["damages"], components, null),
        )
    }

    /**
     * Texto para el tecnico sin ids internos: el id de un candidato pasa a su
     * designador ("cyl2-sam1" -> "CYL 2") y el de una hipotesis a su titulo
     * entre comillas ("confirma h1" -> "confirma “Fusible abierto”"). Solo se
     * reemplazan ids de hipotesis con forma de id, nunca palabras comunes.
     */
    fun humanize(text: String, hypotheses: List<Hypothesis>, components: List<BoardComponent>): String {
        if (text.isBlank()) return text
        var out = text
        components.forEach { c ->
            out = out.replace(token(c.id, ignoreCase = true), Regex.escapeReplacement(c.designator))
        }
        hypotheses.filter { ID_LIKE.matches(it.id) }.forEach { h ->
            out = out.replace(token(h.id, ignoreCase = false), Regex.escapeReplacement("“${h.title}”"))
        }
        return out
    }

    /**
     * El arreglo como accion directa: la app ya lo rotula "Si se confirma", asi
     * que "Si se confirma abierto, reemplazar..." queda "Reemplazar...".
     */
    fun cleanFix(fix: String): String {
        val action = fix.replaceFirst(FIX_CONDITION, "").trim()
        return if (action.isEmpty()) fix.trim() else action.replaceFirstChar { it.uppercaseChar() }
    }

    private val ID_LIKE = Regex("[A-Za-z]{1,4}\\d+[A-Za-z]?")
    private val FIX_CONDITION = Regex(
        "^\\s*(si se confirma|if confirmed)\\b[^,.:;]{0,40}[,.:;]\\s*", RegexOption.IGNORE_CASE)

    /** El id como palabra suelta: ni dentro de otra palabra ni de otro id ("h1" no toca "h10"). */
    private fun token(id: String, ignoreCase: Boolean): Regex = Regex(
        "(?<![\\w-])" + Regex.escape(id) + "(?![\\w-])",
        if (ignoreCase) setOf(RegexOption.IGNORE_CASE) else emptySet(),
    )

    /**
     * Donde van las puntas de cada medicion de [requests], validado contra la
     * foto: cada punta sobre un candidato real (id o designador; si no dice,
     * el medido) y dentro de su caja con margen para las patas; las dos puntas
     * separadas; sin puntos si el modelo dice que los terminales no se ven
     * (queda su nota). Una punta invalida se descarta sola.
     */
    fun parseProbes(
        text: String,
        components: List<BoardComponent>,
        requests: List<MeasurementRequest>,
    ): Map<String, ProbeGuide> {
        val root = parseRoot(text) ?: return emptyMap()
        val byId = requests.associateBy { it.id }
        val guides = LinkedHashMap<String, ProbeGuide>()
        (root["probes"] as? List<*>).orEmpty().forEach { raw ->
            val m = raw as? Map<*, *> ?: return@forEach
            val request = byId[m.str("measurementId")] ?: return@forEach
            if (request.id in guides) return@forEach
            val note = humanize(m.str("note").orEmpty().trim(), emptyList(), components)
            val visible = m["visible"] as? Boolean ?: true
            var red = if (visible) probePoint(m["red"], request, components) else null
            var black = if (visible) probePoint(m["black"], request, components) else null
            if (red != null && black != null &&
                abs(red.x - black.x) < MIN_PROBE_GAP && abs(red.y - black.y) < MIN_PROBE_GAP
            ) {
                red = null
                black = null
            }
            if (red == null && black == null && note.isBlank()) return@forEach
            guides[request.id] = ProbeGuide(red, black, note)
        }
        return guides
    }

    private fun probePoint(
        raw: Any?,
        request: MeasurementRequest,
        components: List<BoardComponent>,
    ): ProbePoint? {
        val m = raw as? Map<*, *> ?: return null
        val componentId = resolve(m.str("componentId"), components, null) ?: request.componentId
        val box = components.firstOrNull { it.id == componentId }?.box ?: return null
        val x = m.num("x")?.toFloat()?.takeIf { it in 0f..1f } ?: return null
        val y = m.num("y")?.toFloat()?.takeIf { it in 0f..1f } ?: return null
        val margin = max(PROBE_MARGIN_MIN, PROBE_MARGIN_FACTOR * max(box.w, box.h))
        if (x < box.x - margin || x > box.x + box.w + margin ||
            y < box.y - margin || y > box.y + box.h + margin
        ) {
            return null
        }
        val terminal = humanize(m.str("terminal").orEmpty().trim(), emptyList(), components)
        return ProbePoint(componentId, x, y, terminal.take(MAX_TERMINAL))
    }

    /** Las patas asoman fuera de la caja de SAM: margen relativo al componente. */
    private const val PROBE_MARGIN_FACTOR = 0.25f
    private const val PROBE_MARGIN_MIN = 0.02f
    private const val MIN_PROBE_GAP = 0.004f
    private const val MAX_TERMINAL = 40

    /**
     * Tipo de medicion tal como lo escribe el modelo, con sinonimos en ES/EN.
     * Las mediciones en alterna se rechazan: BoardPilot no guia mediciones
     * sobre la red electrica.
     */
    fun parseKind(raw: String?): MeasurementKind? {
        val key = raw?.trim()?.uppercase()
            ?.replace(Regex("[^A-Z0-9ÁÉÍÓÚÑ]+"), "_")?.trim('_')
            ?.takeIf { it.isNotEmpty() } ?: return null
        MeasurementKind.entries.firstOrNull { it.name == key }?.let { return it }
        val tokens = key.split("_").toSet()
        // La red tambien se nombra sin decir "alterna": mains/line/grid voltage,
        // voltaje o tension "de red" (el token RED en un tipo de medicion solo
        // puede ser eso). Sin este rechazo caian en la rama VOLT/TENSI y una
        // medicion de red se guiaba como continua.
        val mains = tokens.any { it in MAINS_TOKENS } ||
            (tokens.any { it == "LINE" || it == "LÍNEA" || it == "LINEA" } &&
                ("VOLT" in key || "TENSI" in key))
        return when {
            mains || tokens.any { it in AC_TOKENS } -> null
            "VOLT" in key || "TENSI" in key || "VDC" in tokens || "DCV" in tokens ->
                MeasurementKind.VOLTAGE_DC
            "DIOD" in key -> MeasurementKind.DIODE
            "CONTIN" in key || "BEEP" in key -> MeasurementKind.CONTINUITY
            "RESIST" in key || "OHM" in key -> MeasurementKind.RESISTANCE
            else -> null
        }
    }

    private val AC_TOKENS = setOf(
        "AC", "CA", "VAC", "ACV", "VCA", "ALTERNA", "ALTERNO", "ALTERNATING",
    )
    private val MAINS_TOKENS = setOf("MAINS", "GRID", "RED")

    /**
     * La instruccion manda sobre la placa real: si pide medir alterna o la red
     * ("medir 220 VAC en la entrada") la medicion se descarta aunque el tipo
     * declarado parezca inocente. Patrones puntuales para no rechazar la
     * medicion en continua que solo menciona la red al pasar ("desenchufado
     * de la red", "enchufar y medir 12 V").
     */
    private val AC_INSTRUCTION = listOf(
        Regex("""\b\d+(?:[.,]\d+)?\s*V\s*(?:AC|CA|ALTERNA|ALTERNO|~)\b""", RegexOption.IGNORE_CASE),
        Regex("""\b\d+(?:[.,]\d+)?\s*V\s+(?:DE\s+(?:LA\s+)?RED|MAINS|LINE|GRID)\b""",
            RegexOption.IGNORE_CASE),
        Regex("""\b(?:VAC|VCA|AC\s+VOLTAGE|VOLTAGE\s+AC|MAINS\s+VOLTAGE|LINE\s+VOLTAGE|GRID\s+VOLTAGE)\b""",
            RegexOption.IGNORE_CASE),
        Regex("""\bALTERNATING\s+(?:CURRENT|VOLTAGE)\b""", RegexOption.IGNORE_CASE),
        Regex("""\b(?:TENSI[OÓ]N|VOLTAJE|CORRIENTE)\s+(?:ALTERN[AO]|DE\s+(?:LA\s+)?(?:RED|L[IÍ]NEA))\b""",
            RegexOption.IGNORE_CASE),
        Regex("""\bRED\s+EL[EÉ]CTRICA\b""", RegexOption.IGNORE_CASE),
    )

    private fun mentionsAc(instruction: String): Boolean =
        AC_INSTRUCTION.any { it.containsMatchIn(instruction) }

    private fun requests(
        raw: Any?,
        components: List<BoardComponent>,
        reserved: String?,
        hypotheses: List<Hypothesis>,
        takenIds: Set<String>,
    ): List<MeasurementRequest> {
        val hypIds = hypotheses.map { it.id }.toSet()
        val usedReqIds = takenIds.toMutableSet()
        return (raw as? List<*>).orEmpty().mapIndexedNotNull { index, item ->
            val m = item as? Map<*, *> ?: return@mapIndexedNotNull null
            val componentId = resolve(m.str("componentId"), components, reserved)
                ?: return@mapIndexedNotNull null
            val kind = parseKind(m.str("kind")) ?: return@mapIndexedNotNull null
            // Nunca se guian mediciones con tension en alta tension especial.
            val zone = parseZone(m.str("zone"))
            if (kind == MeasurementKind.VOLTAGE_DC && zone == HazardZone.HIGH_VOLTAGE) {
                return@mapIndexedNotNull null
            }
            // Sin destino valido, solo la hipotesis sobre ese mismo componente: una
            // lectura anormal nunca debe reforzar la hipotesis de otro componente.
            val target = m.str("targetHypothesisId")?.takeIf { it in hypIds }
                ?: hypotheses.firstOrNull { it.componentId == componentId }?.id
                ?: return@mapIndexedNotNull null
            val instruction = m.firstStr("instruction", "how", "procedure")
                ?: return@mapIndexedNotNull null
            if (mentionsAc(instruction)) return@mapIndexedNotNull null
            var id = m.str("id")?.takeIf { it.isNotBlank() } ?: "m${index + 1}"
            while (id in usedReqIds) id += "b"
            usedReqIds += id
            fun human(text: String) = humanize(text, hypotheses, components)
            MeasurementRequest(
                id = id,
                componentId = componentId,
                kind = kind,
                instruction = human(instruction),
                normalDescription = human(m.firstStr("normal", "normalDescription", "expected").orEmpty()),
                abnormalDescription = human(m.firstStr("abnormal", "abnormalDescription", "fault").orEmpty()),
                gate = safetyGateFor(kind),
                targetHypothesisId = target,
                zone = zone,
            )
        }
    }

    /**
     * Id de candidato a partir de lo que mando el modelo: el id exacto, o el
     * designador sin importar mayusculas ni espacios ("CYL 2", "cyl2").
     */
    private fun resolve(raw: String?, components: List<BoardComponent>, reserved: String?): String? {
        val value = raw?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        if (components.any { it.id == value }) return value
        if (value == UNMAPPED_DAMAGE_ID && reserved != null) return reserved
        val key = value.lowercase().replace(Regex("\\s+"), "")
        return components.firstOrNull {
            it.id.lowercase() == key || it.designator.lowercase().replace(Regex("\\s+"), "") == key
        }?.id
    }

    private fun parseRoot(text: String): Map<*, *>? =
        runCatching { MiniJson.parse(extractJson(text)) }.getOrNull() as? Map<*, *>

    private fun extractJson(text: String): String {
        val start = text.indexOf('{')
        val end = text.lastIndexOf('}')
        require(start >= 0 && end > start) { "sin objeto JSON" }
        return text.substring(start, end + 1)
    }

    private fun Map<*, *>.str(key: String): String? = this[key] as? String

    private fun Map<*, *>.firstStr(vararg keys: String): String? =
        keys.firstNotNullOfOrNull { str(it)?.takeIf { value -> value.isNotBlank() } }

    private fun Map<*, *>.num(key: String): Double? =
        (this[key] as? Number)?.toDouble()?.takeIf { it.isFinite() }
}
