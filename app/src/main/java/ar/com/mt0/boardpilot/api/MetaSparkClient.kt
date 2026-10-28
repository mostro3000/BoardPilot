package ar.com.mt0.boardpilot.api

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.util.Base64
import ar.com.mt0.boardpilot.domain.BoardComponent
import ar.com.mt0.boardpilot.domain.DamageFinding
import ar.com.mt0.boardpilot.domain.Hypothesis
import ar.com.mt0.boardpilot.domain.MeasurementResult
import ar.com.mt0.boardpilot.domain.ReasoningEffort
import ar.com.mt0.boardpilot.domain.SparkDamageParser
import ar.com.mt0.boardpilot.i18n.AppLanguage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * Cliente directo de Muse Spark 1.3 contra Meta Model API (sin servidor
 * propio): POST /v1/chat/completions multimodal con la foto de la placa
 * ANOTADA (cajas + etiquetas de los candidatos SAM) y el sintoma; el modelo
 * debe responder SOLO el JSON que parsea SparkOutputParser en el dominio.
 *
 * Protocolo verificado con tools/smoke_spark.py sobre la SMPS 18650 real:
 * `max_tokens` debe dar margen al razonamiento (con 3000 devolvio
 * finish_reason=length y contenido vacio). El esfuerzo de razonamiento lo
 * elige el usuario en Configuracion ([ReasoningEffort]; null = adaptativo
 * de la API). La clave llega por constructor y nunca se registra.
 */
class MetaSparkClient(
    private val apiKey: String,
    private val baseUrl: String = "https://api.meta.ai/v1",
    private val reasoningEffort: ReasoningEffort = ReasoningEffort.DEFAULT,
) {
    /**
     * Pre-escaneo dedicado de dano visible sobre la foto LIMPIA. Pregunta
     * unica y directa (en las pruebas acerto siempre, a diferencia de la
     * deteccion incidental dentro del diagnostico completo): devuelve los
     * hallazgos con caja normalizada, ya validados por el dominio.
     */
    suspend fun scanDamage(
        bitmap: Bitmap,
        language: AppLanguage,
    ): List<DamageFinding> = withContext(Dispatchers.IO) {
        val languageName = if (language == AppLanguage.ES) "Spanish" else "English"
        val body = JSONObject().apply {
            put("model", "muse-spark-1.3")
            put("max_tokens", 6000)
            reasoningEffort.apiValue?.let { put("reasoning_effort", it) }
            put("temperature", 0.2)
            put("messages", JSONArray()
                .put(JSONObject().apply {
                    put("role", "user")
                    put("content", JSONArray()
                        .put(JSONObject().apply {
                            put("type", "text")
                            put("text", DAMAGE_PROMPT.replace("LANGUAGE", languageName))
                        })
                        .put(JSONObject().apply {
                            put("type", "image_url")
                            put("image_url", JSONObject().put(
                                "url",
                                "data:image/jpeg;base64," + encodeJpeg(bitmap),
                            ))
                        }))
                }))
        }
        SparkDamageParser.parse(chat(body))
    }

    /** Devuelve el texto crudo del asistente (JSON a validar en el dominio). */
    suspend fun diagnose(
        bitmap: Bitmap,
        components: List<BoardComponent>,
        symptom: String,
        language: AppLanguage,
        damageFindings: List<DamageFinding> = emptyList(),
    ): String = withContext(Dispatchers.IO) {
        val annotated = annotate(bitmap, components)
        val cleanUrl = "data:image/jpeg;base64," + encodeJpeg(bitmap)
        val imageUrl = "data:image/jpeg;base64," + encodeJpeg(annotated)
        val candidatesJson = candidatesJson(components)
        val languageName = if (language == AppLanguage.ES) "Spanish" else "English"
        val prescan = if (damageFindings.isEmpty()) {
            ""
        } else {
            "\nVisible damage found by a dedicated pre-scan (already included " +
                "as candidates with visibleDamage): " +
                damageFindings.joinToString("; ") { it.description } + "\n"
        }
        val userText = "Symptom: $symptom\n$prescan" +
            "Candidate components from segmentation (kind is a rough visual guess):\n" +
            candidatesJson.toString()
        val body = JSONObject().apply {
            put("model", "muse-spark-1.3")
            put("max_tokens", 12000)
            reasoningEffort.apiValue?.let { put("reasoning_effort", it) }
            put("temperature", 0.2)
            put("messages", JSONArray()
                .put(JSONObject().apply {
                    put("role", "system")
                    put("content", SYSTEM_PROMPT.replace("LANGUAGE", languageName))
                })
                .put(JSONObject().apply {
                    put("role", "user")
                    put("content", JSONArray()
                        .put(JSONObject().apply {
                            put("type", "text")
                            put("text", userText)
                        })
                        .put(JSONObject().apply {
                            put("type", "image_url")
                            put("image_url", JSONObject().put("url", cleanUrl))
                        })
                        .put(JSONObject().apply {
                            put("type", "image_url")
                            put("image_url", JSONObject().put("url", imageUrl))
                        }))
                }))
        }
        chat(body)
    }

    /**
     * Siguientes mediciones sobre las hipotesis ya cargadas, con lo que ya se
     * midio y su resultado: el paso del loop del agente cuando el diagnostico
     * no trajo mediciones o se agotaron sin veredicto. Devuelve el JSON crudo.
     */
    suspend fun planMeasurements(
        bitmap: Bitmap,
        components: List<BoardComponent>,
        symptom: String,
        hypotheses: List<Hypothesis>,
        done: List<MeasurementResult>,
        language: AppLanguage,
    ): String = withContext(Dispatchers.IO) {
        val languageName = if (language == AppLanguage.ES) "Spanish" else "English"
        val hypothesesJson = JSONArray().apply {
            hypotheses.forEach { h ->
                put(JSONObject().apply {
                    put("id", h.id)
                    put("title", h.title)
                    put("componentId", h.componentId)
                    put("confidence", h.confidence)
                    put("confirmedByMeasurement", h.confirmedByMeasurement)
                })
            }
        }
        val doneJson = JSONArray().apply {
            done.forEach { r ->
                put(JSONObject().apply {
                    put("id", r.requestId)
                    put("measured", r.note)
                    put("result", r.outcome.name)
                })
            }
        }
        val userText = "Symptom: $symptom\n" +
            "Candidate components:\n" + candidatesJson(components) + "\n" +
            "Current hypotheses:\n" + hypothesesJson + "\n" +
            "Measurements already done:\n" + doneJson
        val body = JSONObject().apply {
            put("model", "muse-spark-1.3")
            put("max_tokens", 8000)
            reasoningEffort.apiValue?.let { put("reasoning_effort", it) }
            put("temperature", 0.2)
            put("messages", JSONArray()
                .put(JSONObject().apply {
                    put("role", "system")
                    put("content", PLAN_PROMPT.replace("LANGUAGE", languageName))
                })
                .put(JSONObject().apply {
                    put("role", "user")
                    put("content", JSONArray()
                        .put(JSONObject().apply {
                            put("type", "text")
                            put("text", userText)
                        })
                        .put(JSONObject().apply {
                            put("type", "image_url")
                            put("image_url", JSONObject().put(
                                "url", "data:image/jpeg;base64," + encodeJpeg(bitmap)))
                        }))
                }))
        }
        chat(body)
    }

    private fun candidatesJson(components: List<BoardComponent>): JSONArray = JSONArray().apply {
        components.forEach { c ->
            put(JSONObject().apply {
                put("id", c.id)
                put("designator", c.designator)
                put("kind", c.kind.name)
                put("x", c.box.x.toDouble())
                put("y", c.box.y.toDouble())
                put("w", c.box.w.toDouble())
                put("h", c.box.h.toDouble())
                c.visibleDamage?.let { put("visibleDamage", it) }
            })
        }
    }

    /** POST /chat/completions y devuelve el contenido del asistente. */
    private fun chat(body: JSONObject): String {
        val connection = (URL("$baseUrl/chat/completions").openConnection()
            as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 30_000
            readTimeout = 240_000
            doOutput = true
            setRequestProperty("Authorization", "Bearer $apiKey")
            setRequestProperty("Content-Type", "application/json")
        }
        try {
            connection.outputStream.use { it.write(body.toString().toByteArray()) }
            val code = connection.responseCode
            if (code !in 200..299) {
                val detail = connection.errorStream?.bufferedReader()
                    ?.readText()?.take(300).orEmpty()
                throw IllegalStateException("Meta API HTTP $code: $detail")
            }
            val payload = connection.inputStream.bufferedReader().readText()
            val root = JSONObject(payload)
            val choice = root.optJSONArray("choices")?.optJSONObject(0)
                ?: throw IllegalStateException("Meta API: sin choices")
            val content = choice.optJSONObject("message")?.optString("content").orEmpty()
            if (content.isBlank()) {
                throw IllegalStateException(
                    "Meta API: respuesta vacia (finish_reason=" +
                        choice.optString("finish_reason") + ")",
                )
            }
            return content
        } finally {
            connection.disconnect()
        }
    }

    /** Copia de la foto con las cajas candidatas dibujadas y su etiqueta. */
    private fun annotate(source: Bitmap, components: List<BoardComponent>): Bitmap {
        val out = source.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(out)
        val w = out.width.toFloat()
        val h = out.height.toFloat()
        val amber = 0xFFFFB300.toInt()
        val stroke = Paint().apply {
            color = amber
            style = Paint.Style.STROKE
            strokeWidth = maxOf(3f, w * 0.006f)
            isAntiAlias = true
        }
        val labelBg = Paint().apply { color = amber }
        val labelText = Paint().apply {
            color = 0xFF000000.toInt()
            textSize = maxOf(22f, w * 0.038f)
            isAntiAlias = true
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        }
        components.forEach { c ->
            val left = c.box.x * w
            val top = c.box.y * h
            val right = (c.box.x + c.box.w) * w
            val bottom = (c.box.y + c.box.h) * h
            canvas.drawRect(left, top, right, bottom, stroke)
            val label = c.designator
            val textW = labelText.measureText(label)
            val textH = labelText.textSize * 1.25f
            val labelTop = maxOf(0f, top - textH)
            canvas.drawRect(left, labelTop, left + textW + 12f, labelTop + textH, labelBg)
            canvas.drawText(label, left + 6f, labelTop + textH * 0.85f, labelText)
        }
        return out
    }

    private fun encodeJpeg(bitmap: Bitmap): String {
        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 85, stream)
        return Base64.encodeToString(stream.toByteArray(), Base64.NO_WRAP)
    }

    companion object {
        // Mismo prompt validado por tools/smoke_spark.py (mantener en sync).
        private const val DAMAGE_PROMPT = """Look at this electronic board photo and scan the ENTIRE board for visible physical damage ONLY: burnt, charred or blackened components or PCB areas; bulging, leaking or exploded capacitors; cracked or melted parts; heavy corrosion or burnt traces.
Reply with ONLY one valid JSON object in this exact shape, no prose and no code fences:
{"damages":[{"description":"...","x":0.0,"y":0.0,"w":0.0,"h":0.0}]}
Use tight normalized boxes (0..1, origin top-left) around each damaged component or area, at most 3, worst first. Descriptions in LANGUAGE. If there is no visible damage, return {"damages":[]}."""

        private const val SYSTEM_PROMPT = """You are BoardPilot, an expert electronics repair copilot for component-level PCB diagnosis.
You receive TWO images of the same board: first the clean photo, second the same photo annotated with candidate regions from a segmentation model; plus the candidate list as JSON (normalized 0..1 coordinates, origin top-left) and a symptom. Candidates are visual regions and may be wrong or incomplete; use the clean photo (labels can hide detail) to interpret what each region really is.
Reply with ONLY one valid JSON object in this exact shape, no prose and no code fences:
{"hypotheses":[{"id":"h1","title":"...","componentId":"...","confidence":0.4,"rationale":"...","fix":"..."}],"measurements":[{"id":"m1","componentId":"...","kind":"CONTINUITY|RESISTANCE|VOLTAGE_DC|DIODE","instruction":"...","normal":"...","abnormal":"...","targetHypothesisId":"h1"}],"damages":[{"componentId":"...","damage":"..."}],"unmappedDamage":{"description":"...","x":0.0,"y":0.0,"w":0.0,"h":0.0},"needMoreEvidence":""}
Rules:
- FIRST scan the ENTIRE photo for visible damage: burnt/charred/blackened components or PCB areas, bulging or leaking capacitors, cracked or melted parts, heavy corrosion. For every candidate that is visibly damaged add a "damages" entry describing it. A visibly burnt or exploded component is the strongest evidence there is: rank its hypothesis FIRST with the highest confidence and target it with the first measurement.
- If visible damage has NO candidate box around it, report it in "unmappedDamage" with a tight normalized box (x,y,w,h in 0..1) around the damaged component/area; in that case you may reference that region as componentId "dmg1-spark" in hypotheses and measurements. If there is no unmapped damage, set "unmappedDamage" to null.
- componentId must be the candidate "id" value exactly as in the list (for example "cyl2-sam1"), plus "dmg1-spark" when you reported unmappedDamage. Designators (for example "CYL 2") are only for naming components inside texts; never invent silkscreen reference designators.
- At most 4 hypotheses ranked by likelihood for the symptom (on a dead switch-mode power supply consider fuse, bridge rectifier, bulk capacitor, switching MOSFET short, startup resistor, PWM controller supply, optocoupler/feedback, secondary diodes and output capacitors) adapted to what is actually visible.
- For each hypothesis, "fix" says what to replace or repair if it is confirmed, with a generic replacement spec when it applies (part type, key ratings, package and a common part number as an example).
- Whenever you give hypotheses, also give between 1 and 5 measurements, ordered by diagnostic value and safety. Each targets exactly one hypothesis by its id and one component, uses a kind that is exactly CONTINUITY, RESISTANCE, VOLTAGE_DC or DIODE (never AC or mains measurements), says where to put the probes on this board, and describes what normal vs abnormal looks like on the meter.
- Safety: continuity, resistance and diode measurements are done only with the unit unplugged and capacitors discharged; voltage measurements only when essential and with explicit caution in the instruction.
- If the photo or candidates are insufficient, return empty arrays and explain in needMoreEvidence what photo/measurement you need.
- All user-visible strings (title, rationale, fix, instruction, normal, abnormal, damage, description, needMoreEvidence) in LANGUAGE."""

        // Mismo prompt que tools/smoke_spark.py --plan (mantener en sync).
        private const val PLAN_PROMPT = """You are BoardPilot, an expert electronics repair copilot guiding a technician with a multimeter, one measurement at a time.
You receive the clean photo of a board, its candidate components (JSON, normalized 0..1 boxes, origin top-left), the symptom, the current fault hypotheses (with ids and confidence) and the measurements already done with their results. Propose the NEXT measurements that best confirm or rule out the hypotheses, most informative first.
Reply with ONLY one valid JSON object in this exact shape, no prose and no code fences:
{"measurements":[{"id":"m1","componentId":"...","kind":"CONTINUITY|RESISTANCE|VOLTAGE_DC|DIODE","instruction":"...","normal":"...","abnormal":"...","targetHypothesisId":"h1"}],"needMoreEvidence":""}
Rules:
- Between 1 and 3 measurements and never repeat one already done. Each targets one hypothesis id from the list and one componentId exactly as in the candidate list.
- kind is exactly CONTINUITY, RESISTANCE, VOLTAGE_DC or DIODE (never AC or mains measurements). The instruction says where to put the probes on this board; normal and abnormal say what the meter shows.
- Safety: continuity, resistance and diode measurements only with the unit unplugged and capacitors discharged; voltage measurements only when essential and with explicit caution in the instruction.
- If no further measurement can tell the hypotheses apart, return an empty list and say in needMoreEvidence what photo or information you need.
- All user-visible strings (instruction, normal, abnormal, needMoreEvidence) in LANGUAGE."""
    }
}
