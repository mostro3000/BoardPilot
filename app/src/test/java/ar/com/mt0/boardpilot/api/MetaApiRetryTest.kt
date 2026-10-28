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
        val result = MetaApiRetry.run(sleep = { waits += it }) {
            calls++
            if (calls < 3) throw MetaApiException(503, "Meta API HTTP 503") else "ok"
        }
        assertEquals("ok", result)
        assertEquals(3, calls)
        assertEquals(listOf(2_000L, 4_000L), waits)
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
