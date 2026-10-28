package ar.com.mt0.boardpilot.api

import java.io.IOException
import java.net.SocketTimeoutException

/** Error HTTP de la Meta Model API: el codigo dice si vale la pena reintentar. */
class MetaApiException(val code: Int, message: String) : IllegalStateException(message)

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

    /** Espera antes del reintento numero [attempt] (1, 2...): 2 s, 4 s. */
    fun delayMs(attempt: Int): Long = 2_000L * attempt

    /** Corre [block] hasta [attempts] veces mientras falle por algo pasajero. */
    fun <T> run(
        attempts: Int = ATTEMPTS,
        sleep: (Long) -> Unit = { Thread.sleep(it) },
        block: () -> T,
    ): T {
        var attempt = 1
        while (true) {
            try {
                return block()
            } catch (e: Exception) {
                if (attempt >= attempts || !isTransient(e)) throw e
                sleep(delayMs(attempt))
                attempt++
            }
        }
    }
}
