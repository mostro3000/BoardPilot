package ar.com.mt0.boardpilot.api

import android.graphics.Bitmap
import android.util.Base64
import ar.com.mt0.boardpilot.domain.SamBox
import ar.com.mt0.boardpilot.domain.SamOutputParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * Cliente directo de SAM 3.1 contra Meta Model API (sin servidor propio).
 * Protocolo oficial: POST /v1/responses, stream SSE, un concepto por pedido,
 * imagen como data URL base64. La clave llega por constructor desde las
 * preferencias privadas del dispositivo y nunca se registra en logs.
 */
class MetaSamClient(
    private val apiKey: String,
    private val baseUrl: String = "https://api.meta.ai/v1",
) {
    suspend fun segment(bitmap: Bitmap, concept: String): List<SamBox> =
        withContext(Dispatchers.IO) {
            val imageUrl = "data:image/jpeg;base64," + encodeJpeg(bitmap)
            val body = JSONObject().apply {
                put("model", "sam-3.1")
                put("stream", true)
                put("metadata", JSONObject().apply {
                    put("include_confidence", "true")
                    put("mask_encoding", "one_bit")
                })
                put("input", JSONArray().put(JSONObject().apply {
                    put("type", "message")
                    put("role", "user")
                    put("content", JSONArray()
                        .put(JSONObject().apply {
                            put("type", "input_text")
                            put("text", concept)
                        })
                        .put(JSONObject().apply {
                            put("type", "input_image")
                            put("image_url", imageUrl)
                        }))
                }))
            }
            val connection = (URL("$baseUrl/responses").openConnection()
                as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 30_000
                readTimeout = 180_000
                doOutput = true
                setRequestProperty("Authorization", "Bearer $apiKey")
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Accept", "text/event-stream")
            }
            try {
                connection.outputStream.use { it.write(body.toString().toByteArray()) }
                val code = connection.responseCode
                if (code !in 200..299) {
                    val detail = connection.errorStream?.bufferedReader()
                        ?.readText()?.take(300).orEmpty()
                    throw IllegalStateException("Meta API HTTP $code: $detail")
                }
                val outputText = StringBuilder()
                connection.inputStream.bufferedReader().useLines { lines ->
                    for (line in lines) {
                        if (!line.startsWith("data:")) continue
                        val data = line.removePrefix("data:").trim()
                        if (data == "[DONE]") break
                        val event = runCatching { JSONObject(data) }.getOrNull()
                            ?: continue
                        when (event.optString("type")) {
                            "response.output_text.delta" ->
                                outputText.append(event.optString("delta"))
                            "error" -> throw IllegalStateException(
                                "Meta API: ${event.optString("message").take(300)}",
                            )
                        }
                    }
                }
                SamOutputParser.parse(outputText.toString())
            } finally {
                connection.disconnect()
            }
        }

    private fun encodeJpeg(bitmap: Bitmap): String {
        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 85, stream)
        return Base64.encodeToString(stream.toByteArray(), Base64.NO_WRAP)
    }
}
