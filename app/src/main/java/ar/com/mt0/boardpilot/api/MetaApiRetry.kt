package ar.com.mt0.boardpilot.api

import java.io.IOException
import java.net.SocketTimeoutException
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import kotlin.random.Random

/** Error HTTP de la Meta Model API: el codigo dice si vale la pena reintentar. */
class MetaApiException(
    val code: Int,
    message: String,
    /** Espera que pidio el servidor (header Retry-After), si vino. */
    val retryAfterMs: Long? = null,
) : IllegalStateException(message)

/**
 * Reintento ante fallas pasajeras de la Meta Model API: 429 (limite de pedidos),
 * 5xx (servicio caido un momento: visto con un 503 de SAM en las pruebas) o la
 * red sin conexion o sin DNS. No se reintentan los 4xx (clave invalida, pedido
 * mal armado), un timeout de lectura (ya se espero minutos) ni una respuesta
 * vacia por falta de tokens (fallaria igual y gasta tokens).
 */
object MetaApiRetry {
    const val ATTEMPTS = 3

    fun isTransient(error: Throwable): Boolean = when (error) {
        is MetaApiException -> error.code == 429 || error.code in 500..599
        is SocketTimeoutException -> false
        is IOException -> true
        else -> false
    }

    /** Espera maxima antes de un reintento, pida lo que pida el servidor. */
    const val MAX_DELAY_MS = 120_000L

    /** Disperso aleatorio que se suma a la espera (ver [delayMs]). */
    const val MAX_JITTER_MS = 1_000L

    /**
     * Header Retry-After a milisegundos: segundos ("30") o fecha HTTP. El
     * servidor sabe cuanto hay que esperar ante un 429; ignorarlo y reintentar
     * a los 2 s solo renueva el limite y multiplica pedidos facturados.
     */
    fun retryAfterMs(header: String?, nowMs: Long = System.currentTimeMillis()): Long? {
        val value = header?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        value.toLongOrNull()?.let { return it.coerceIn(0, MAX_DELAY_MS / 1_000) * 1_000 }
        return runCatching {
            val at = ZonedDateTime.parse(value, DateTimeFormatter.RFC_1123_DATE_TIME)
                .toInstant().toEpochMilli()
            (at - nowMs).coerceIn(0, MAX_DELAY_MS)
        }.getOrNull()
    }

    /**
     * Espera antes del reintento numero [attempt] (1, 2...): 2 s, 4 s... o lo
     * que haya pedido el servidor si es mas. Sin jitter todavia; [run] suma el
     * disperso para que pedidos en paralelo (las 5 pasadas del pre-escaneo) no
     * reintenten todos en el mismo instante y agraven el 429.
     */
    fun delayMs(attempt: Int, error: Throwable? = null): Long {
        val asked = (error as? MetaApiException)?.retryAfterMs ?: 0L
        return maxOf(2_000L * attempt, asked).coerceAtMost(MAX_DELAY_MS)
    }

    /** Corre [block] hasta [attempts] veces mientras falle por algo pasajero. */
    fun <T> run(
        attempts: Int = ATTEMPTS,
        sleep: (Long) -> Unit = { Thread.sleep(it) },
        jitterMs: () -> Long = { Random.nextLong(0, MAX_JITTER_MS + 1) },
        block: () -> T,
    ): T {
        var attempt = 1
        while (true) {
            try {
                return block()
            } catch (e: Exception) {
                if (attempt >= attempts || !isTransient(e)) throw e
                sleep(delayMs(attempt, e) + jitterMs().coerceIn(0, MAX_JITTER_MS))
                attempt++
            }
        }
    }
}
