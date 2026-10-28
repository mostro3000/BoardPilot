package ar.com.mt0.boardpilot

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.sqrt

class EggSoundtrackTest {

    private val rate = EggSoundtrack.SAMPLE_RATE
    private val pcm = EggSoundtrack.render()
    private val cycle = EggTimeline.CYCLE
    private val pop = EggTimeline.POP

    private fun rms(fromSec: Float, toSec: Float): Double {
        val from = (fromSec * rate).toInt()
        val to = (toSec * rate).toInt()
        var sum = 0.0
        for (i in from until to) sum += pcm[i].toDouble() * pcm[i]
        return sqrt(sum / (to - from)) / 32767.0
    }

    @Test
    fun `el loop alterna C12 con un componente chico`() {
        assertEquals(EggScene.VENT to 1f, eggSceneAt(1f))
        assertEquals(EggScene.RESISTOR, eggSceneAt(cycle + 0.5f).first)
        assertEquals(EggScene.ROCKET, eggSceneAt(2 * cycle + 0.5f).first)
        val (scene, t) = eggSceneAt(3 * cycle + 2f)
        assertEquals(EggScene.DIODE, scene)
        assertEquals(2f, t, 0.001f)
    }

    @Test
    fun `C12 entra en CC con caida de tension y queda abierto al reventar`() {
        assertEquals(24f, psuReading(EggScene.VENT, EggTimeline.RAMP_END).volts, 0.01f)
        assertFalse(psuReading(EggScene.VENT, 1.5f).limiting)
        val limiting = psuReading(EggScene.ROCKET, pop - 0.05f)
        assertTrue(limiting.limiting)
        assertEquals(2f, limiting.amps, 0.001f)
        assertTrue(limiting.volts < 16f) // el capacitor ya casi es un corto
        val after = psuReading(EggScene.VENT, pop + 0.1f)
        assertEquals(0f, after.amps, 0f)
        assertEquals(24f, after.volts, 0f)
    }

    @Test
    fun `R7 limita en 20 V y 2 A, se va de CC al carbonizarse y queda abierta`() {
        val burning = psuReading(EggScene.RESISTOR, 1.0f)
        assertTrue(burning.limiting)
        assertEquals(2f, burning.amps, 0.001f)
        assertTrue(burning.volts in 18.5f..20.1f) // 40 W sobre una de 1/4 W
        val charred = psuReading(EggScene.RESISTOR, pop - 0.05f)
        assertFalse(charred.limiting)
        assertTrue(charred.amps < 2f)
        val open = psuReading(EggScene.RESISTOR, pop + 0.1f)
        assertEquals(0f, open.amps, 0f)
        assertEquals(24f, open.volts, 0f)
    }

    @Test
    fun `D3 limita a 1,2 V, baja su caida al calentarse y queda en corto`() {
        val cold = psuReading(EggScene.DIODE, 0.5f)
        val hot = psuReading(EggScene.DIODE, pop - 0.1f)
        assertTrue(cold.limiting && hot.limiting)
        assertTrue(cold.volts in 1.1f..1.3f)
        assertTrue(hot.volts < cold.volts)
        val shorted = psuReading(EggScene.DIODE, pop + 0.5f)
        assertTrue(shorted.limiting)
        assertTrue(shorted.volts < 0.1f)
        assertEquals(2f, shorted.amps, 0f)
    }

    @Test
    fun `la tapa se abomba de a poco hasta reventar`() {
        assertEquals(0f, capDome(EggTimeline.DOME_START), 0f)
        assertTrue(capDome(2.2f) in 0.05f..0.6f)
        assertEquals(1f, capDome(pop), 0.001f)
    }

    @Test
    fun `cuatro escenas exactas, deterministas y sin recortes`() {
        assertEquals((EggTimeline.LOOP * rate).toInt(), pcm.size)
        assertArrayEquals(pcm, EggSoundtrack.render())
        val peak = pcm.maxOf { abs(it.toInt()) }
        assertTrue("pico $peak", peak in 16000..32766)
    }

    @Test
    fun `lo mas fuerte es un estallido de C12`() {
        val window = (0.02f * rate).toInt()
        var loudestAt = 0
        var loudest = -1.0
        var i = 0
        while (i + window < pcm.size) {
            var sum = 0.0
            for (k in i until i + window) sum += pcm[k].toDouble() * pcm[k]
            if (sum > loudest) {
                loudest = sum
                loudestAt = i
            }
            i += window / 2
        }
        val atSec = loudestAt.toFloat() / rate
        val nearPop = listOf(pop, 2 * cycle + pop).any { abs(atSec - it) < 0.06f }
        assertTrue("pico en $atSec s", nearPop)
        assertTrue(rms(pop, pop + 0.05f) > 4 * rms(0.5f, 1.5f))
    }

    @Test
    fun `R7 y D3 tambien se escuchan al fallar`() {
        // Contra el tramo previo, donde solo suena la fuente limitando.
        assertTrue(rms(cycle + pop, cycle + pop + 0.05f) > 2 * rms(cycle + 0.5f, cycle + 1.0f))
        assertTrue(rms(3 * cycle + pop, 3 * cycle + pop + 0.05f) > 2 * rms(3 * cycle + 0.5f, 3 * cycle + 1.0f))
    }

    @Test
    fun `el loop empieza y termina en silencio para no chasquear al repetirse`() {
        assertTrue(rms(0f, 0.03f) < 0.01)
        assertTrue(rms(EggTimeline.LOOP - 0.1f, EggTimeline.LOOP) < 0.02)
    }
}
