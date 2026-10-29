package ar.com.mt0.boardpilot.api

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import ar.com.mt0.boardpilot.domain.BoardComponent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * Cliente directo de Muse Image 1.0 contra Meta Model API (sin servidor
 * propio): POST /v1/responses con la foto de la placa como referencia
 * (`input_image`) y la instruccion de mostrarla ya reparada; la imagen
 * generada llega en base64 en el item `image_generation_call` de `output`.
 * Protocolo verificado con tools/smoke_image.py sobre la SMPS 18650 real
 * (HTTP 200, misma placa y encuadre con la zona quemada limpia).
 *
 * La referencia es solo orientativa: la UI la rotula como imagen generada
 * y jamas cuenta como verificacion de la reparacion.
 */
class MetaImageClient(
    private val apiKey: String,
    private val baseUrl: String = "https://api.meta.ai/v1",
) {
    suspend fun repairReference(
        bitmap: Bitmap,
        component: BoardComponent,
    ): Bitmap = withContext(Dispatchers.IO) {
        val damage = buildString {
            append("the damaged component ")
            append(component.designator)
            append(" at the ")
            append(positionText(component))
            append(" of the board")
            component.visibleDamage?.let { append(" ($it)") }
        }
        val body = JSONObject().apply {
            put("model", "muse-image-1.0")
            put("input", JSONArray().put(JSONObject().apply {
                put("type", "message")
                put("role", "user")
                put("content", JSONArray()
                    .put(JSONObject().apply {
                        put("type", "input_text")
                        put("text", PROMPT.replace("DAMAGE", damage))
                    })
                    .put(JSONObject().apply {
                        put("type", "input_image")
                        put(
                            "image_url",
                            "data:image/jpeg;base64," + encodeJpeg(bitmap),
                        )
                    }))
            }))
        }
        // Fallas pasajeras (429, 5xx, red) se reintentan.
        MetaApiRetry.run {
            val connection = (URL("$baseUrl/responses").openConnection()
                as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 30_000
                readTimeout = 300_000
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
                    throw MetaApiException(code, "Meta API HTTP $code: $detail")
                }
                val root = JSONObject(connection.inputStream.bufferedReader().readText())
                val output = root.optJSONArray("output")
                    ?: throw IllegalStateException("Meta API: sin output")
                val result = (0 until output.length())
                    .mapNotNull { output.optJSONObject(it) }
                    .firstOrNull {
                        it.optString("type") == "image_generation_call" &&
                            it.optString("result").isNotBlank()
                    }
                    ?.optString("result")
                    ?: throw IllegalStateException("Meta API: sin imagen generada")
                val bytes = Base64.decode(result, Base64.DEFAULT)
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                    ?: throw IllegalStateException("Meta API: imagen no decodificable")
            } finally {
                connection.disconnect()
            }
        }
    }

    /** Posicion gruesa del componente en la foto, para anclar la edicion. */
    private fun positionText(component: BoardComponent): String {
        val cx = component.box.x + component.box.w / 2f
        val cy = component.box.y + component.box.h / 2f
        val horizontal = when {
            cx < 0.36f -> "left"
            cx > 0.64f -> "right"
            else -> "center"
        }
        val vertical = when {
            cy < 0.36f -> "top"
            cy > 0.64f -> "bottom"
            else -> "middle"
        }
        return "$vertical-$horizontal area"
    }

    private fun encodeJpeg(bitmap: Bitmap): String {
        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 85, stream)
        return Base64.encodeToString(stream.toByteArray(), Base64.NO_WRAP)
    }

    companion object {
        // Mismo prompt validado por tools/smoke_image.py (mantener en sync).
        private const val PROMPT = "Edit the attached photo of an electronic " +
            "circuit board. Keep the exact same board, camera angle, " +
            "framing and background. Show the board AFTER a successful repair: " +
            "DAMAGE is cleaned, the damaged component is replaced with a new " +
            "clean component of the same type, scorch marks are gone and solder " +
            "joints look fresh. Everything else must stay identical. " +
            "Photorealistic electronics repair bench photo. Do not add any " +
            "text, labels, arrows or extra tools."
    }
}
