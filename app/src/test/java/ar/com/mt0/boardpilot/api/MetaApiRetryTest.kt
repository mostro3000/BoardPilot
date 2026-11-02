package ar.com.mt0.boardpilot.api

import java.net.SocketTimeoutException
import java.net.UnknownHostException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class MetaApiRetryTest {

    @Test
    fun `un 503 pasajero se reintenta con esperas crecientes`() {
        // Visto en las pruebas: SAM devolvio un 503 y corto la corrida.
        val waits = mutableListOf<Long>()
        var calls = 0
        val result = MetaApiRetry.run(sleep = { waits += it }, jitterMs = { 0 }) {
            calls++
            if (calls < 3) throw MetaApiException(503, "Meta API HTTP 503") else "ok"
        }
        assertEquals("ok", result)
        assertEquals(3, calls)
        assertEquals(listOf(2_000L, 4_000L), waits)
    }

    @Test
    fun `retry-after manda sobre la espera fija y el jitter se suma`() {
        assertEquals(30_000L, MetaApiRetry.retryAfterMs("30"))
        assertEquals(2_000L, MetaApiRetry.retryAfterMs("2"))
        assertEquals(MetaApiRetry.MAX_DELAY_MS, MetaApiRetry.retryAfterMs("99999"))
        assertEquals(0L, MetaApiRetry.retryAfterMs("-5"))
        assertEquals(null, MetaApiRetry.retryAfterMs(null))
        assertEquals(null, MetaApiRetry.retryAfterMs("manana"))
        // Fecha HTTP: 10 s despues de "ahora".
        val now = 1_700_000_000_000L
        assertEquals(
            10_000L,
            MetaApiRetry.retryAfterMs("Tue, 14 Nov 2023 22:13:30 GMT", nowMs = now),
        )

        val waits = mutableListOf<Long>()
        var calls = 0
        MetaApiRetry.run(sleep = { waits += it }, jitterMs = { 7 }) {
            calls++
            if (calls == 1) throw MetaApiException(429, "Meta API HTTP 429", retryAfterMs = 30_000)
            if (calls == 2) throw MetaApiException(503, "Meta API HTTP 503", retryAfterMs = 10)
        }
        assertEquals(3, calls)
        // 30 s que pidio el servidor; despues la fija (4 s) gana sobre 10 ms.
        assertEquals(listOf(30_007L, 4_007L), waits)
    }

    @Test
    fun `la red sin dns se reintenta y el limite 429 tambien`() {
        var calls = 0
        MetaApiRetry.run(sleep = {}) {
            calls++
            when (calls) {
                1 -> throw UnknownHostException("api.meta.ai")
                2 -> throw MetaApiException(429, "Meta API HTTP 429")
                else -> Unit
            }
        }
        assertEquals(3, calls)
    }

    @Test
    fun `clave invalida timeout y respuesta vacia no se reintentan`() {
        listOf(
            MetaApiException(401, "Meta API HTTP 401"),
            SocketTimeoutException("read timed out"),
            IllegalStateException("Meta API: respuesta vacia (finish_reason=length)"),
        ).forEach { error ->
            var calls = 0
            assertThrows(error::class.java) {
                MetaApiRetry.run(sleep = {}) {
                    calls++
                    throw error
                }
            }
            assertEquals(1, calls)
        }
    }

    @Test
    fun `tras los intentos se rinde con el ultimo error`() {
        var calls = 0
        val thrown = assertThrows(MetaApiException::class.java) {
            MetaApiRetry.run(sleep = {}) {
                calls++
                throw MetaApiException(500 + calls, "Meta API HTTP ${500 + calls}")
            }
        }
        assertEquals(MetaApiRetry.ATTEMPTS, calls)
        assertEquals(503, thrown.code)
    }
}
