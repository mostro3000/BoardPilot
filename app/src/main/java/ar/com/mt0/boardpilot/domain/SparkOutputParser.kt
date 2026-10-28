package ar.com.mt0.boardpilot.domain

/** Resultado util de Muse Spark ya validado contra los candidatos SAM. */
data class SparkDiagnosis(
    val hypotheses: List<Hypothesis> = emptyList(),
    val requests: List<MeasurementRequest> = emptyList(),
    val needMoreEvidence: String? = null,
    /** Dano visible por componente ya mapeado: id -> descripcion localizada. */
    val damages: Map<String, String> = emptyMap(),
    /** Componentes sinteticos por dano visible SIN caja SAM (p. ej. DMG 1). */
    val extraComponents: List<BoardComponent> = emptyList(),
)

/**
 * Interpreta la salida JSON de Muse Spark de forma defensiva:
 * - Tolera cercos de codigo Markdown y texto alrededor del objeto JSON.
 * - Descarta hipotesis/mediciones que referencien componentes inexistentes,
 *   tipos de medicion desconocidos o textos vacios.
 * - La compuerta de seguridad la fija el dominio segun el tipo de medicion
 *   ([safetyGateFor]), nunca el modelo.
 * - Limita cantidades (4 hipotesis, 5 mediciones) y normaliza ids unicos.
 * Ante JSON invalido devuelve un [SparkDiagnosis] vacio.
 */
object SparkOutputParser {
    private const val MAX_HYPOTHESES = 4
    private const val MAX_MEASUREMENTS = 5

    /** Id reservado para el dano visible que SAM no llego a encuadrar. */
    const val UNMAPPED_DAMAGE_ID = "dmg1-spark"

    fun parse(text: String, components: List<BoardComponent>): SparkDiagnosis {
        val root = runCatching { MiniJson.parse(extractJson(text)) }.getOrNull()
            as? Map<*, *> ?: return SparkDiagnosis()
        val inputIds = components.map { it.id }.toSet()

        // Dano visible sobre componentes ya mapeados por SAM.
        val damages = LinkedHashMap<String, String>()
        (root["damages"] as? List<*>).orEmpty().forEach { raw ->
            val m = raw as? Map<*, *> ?: return@forEach
            val id = m.str("componentId")?.takeIf { it in inputIds }
                ?: return@forEach
            val damage = m.str("damage")?.takeIf { it.isNotBlank() }
                ?: return@forEach
            damages.putIfAbsent(id, damage)
        }

        // Dano visible sin caja: Spark estima la caja y pasa a ser candidato
        // sintetico DMG (id libre siguiente si el pre-escaneo ya creo alguno),
        // o se asigna al componente que ya lo contiene.
        val extraComponents = mutableListOf<BoardComponent>()
        (root["unmappedDamage"] as? Map<*, *>)?.let { unmapped ->
            val description = unmapped.str("description")
                ?.takeIf { it.isNotBlank() }
            val x = unmapped.num("x")
            val y = unmapped.num("y")
            val w = unmapped.num("w")
            val h = unmapped.num("h")
            if (description != null && x != null && y != null && w != null && h != null) {
                val assigned = SparkDamageParser.assign(
                    listOf(DamageFinding(description, SparkDamageParser.clampBox(x, y, w, h))),
                    components,
                )
                assigned.first.forEach { (id, damage) ->
                    damages.putIfAbsent(id, damage)
                }
                extraComponents += assigned.second
            }
        }
        val validIds = inputIds + extraComponents.map { it.id }

        val usedHypIds = mutableSetOf<String>()
        val hypotheses = (root["hypotheses"] as? List<*>).orEmpty()
            .mapIndexedNotNull { index, raw ->
                val m = raw as? Map<*, *> ?: return@mapIndexedNotNull null
                val componentId = m.str("componentId")?.takeIf { it in validIds }
                    ?: return@mapIndexedNotNull null
                val title = m.str("title")?.takeIf { it.isNotBlank() }
                    ?: return@mapIndexedNotNull null
                var id = m.str("id")?.takeIf { it.isNotBlank() } ?: "h${index + 1}"
                while (id in usedHypIds) id += "b"
                usedHypIds += id
                Hypothesis(
                    id = id,
                    title = title,
                    componentId = componentId,
                    confidence = (m["confidence"] as? Number)?.toDouble()
                        ?.coerceIn(0.01, 0.99) ?: 0.25,
                    rationale = m.str("rationale").orEmpty(),
                )
            }
            .take(MAX_HYPOTHESES)

        val usedReqIds = mutableSetOf<String>()
        val requests = (root["measurements"] as? List<*>).orEmpty()
            .mapIndexedNotNull { index, raw ->
                val m = raw as? Map<*, *> ?: return@mapIndexedNotNull null
                val componentId = m.str("componentId")?.takeIf { it in validIds }
                    ?: return@mapIndexedNotNull null
                val kind = m.str("kind")?.let {
                    runCatching { MeasurementKind.valueOf(it.trim().uppercase()) }
                        .getOrNull()
                } ?: return@mapIndexedNotNull null
                val target = m.str("targetHypothesisId")?.takeIf { it in usedHypIds }
                    ?: return@mapIndexedNotNull null
                val instruction = m.str("instruction")?.takeIf { it.isNotBlank() }
                    ?: return@mapIndexedNotNull null
                val normal = m.str("normal")?.takeIf { it.isNotBlank() }
                    ?: return@mapIndexedNotNull null
                val abnormal = m.str("abnormal")?.takeIf { it.isNotBlank() }
                    ?: return@mapIndexedNotNull null
                var id = m.str("id")?.takeIf { it.isNotBlank() } ?: "m${index + 1}"
                while (id in usedReqIds) id += "b"
                usedReqIds += id
                MeasurementRequest(
                    id = id,
                    componentId = componentId,
                    kind = kind,
                    instruction = instruction,
                    normalDescription = normal,
                    abnormalDescription = abnormal,
                    gate = safetyGateFor(kind),
                    targetHypothesisId = target,
                )
            }
            .take(MAX_MEASUREMENTS)

        return SparkDiagnosis(
            hypotheses = hypotheses,
            requests = requests,
            needMoreEvidence = root.str("needMoreEvidence")
                ?.takeIf { it.isNotBlank() },
            damages = damages,
            extraComponents = extraComponents,
        )
    }

    private fun extractJson(text: String): String {
        val start = text.indexOf('{')
        val end = text.lastIndexOf('}')
        require(start >= 0 && end > start) { "sin objeto JSON" }
        return text.substring(start, end + 1)
    }

    private fun Map<*, *>.str(key: String): String? = this[key] as? String

    private fun Map<*, *>.num(key: String): Double? =
        (this[key] as? Number)?.toDouble()?.takeIf { it.isFinite() }
}
