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

    private fun rms(fromSec: Float, toSec: Float): Double {
        val from = (fromSec * rate).toInt()
        val to = (toSec * rate).toInt()
        var sum = 0.0
        for (i in from until to) sum += pcm[i].toDouble() * pcm[i]
        return sqrt(sum / (to - from)) / 32767.0
    }

    @Test
    fun `la fuente sube a 24 V, entra en CC con caida de tension y queda abierta al reventar`() {
        assertEquals(24f, psuReading(EggTimeline.RAMP_END).volts, 0.01f)
        assertFalse(psuReading(1.5f).limiting)
        val limiting = psuReading(EggTimeline.POP - 0.05f)
        assertTrue(limiting.limiting)
        assertEquals(2f, limiting.amps, 0.001f)
        assertTrue(limiting.volts < 16f) // el capacitor ya casi es un corto
        val after = psuReading(EggTimeline.POP + 0.1f)
        assertEquals(0f, after.amps, 0f)
        assertEquals(24f, after.volts, 0f)
    }

    @Test
    fun `la tapa se abomba de a poco hasta reventar`() {
        assertEquals(0f, capDome(EggTimeline.DOME_START), 0f)
        assertTrue(capDome(2.2f) in 0.05f..0.6f)
        assertEquals(1f, capDome(EggTimeline.POP), 0.001f)
    }

    @Test
    fun `dos ciclos exactos, deterministas y sin recortes`() {
        assertEquals((EggTimeline.LOOP * rate).toInt(), pcm.size)
        assertArrayEquals(pcm, EggSoundtrack.render())
        val peak = pcm.maxOf { abs(it.toInt()) }
        assertTrue("pico $peak", peak in 16000..32766)
    }

    @Test
    fun `lo mas fuerte es el estallido de cada ciclo`() {
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
        val nearPop = listOf(EggTimeline.POP, EggTimeline.CYCLE + EggTimeline.POP)
            .any { abs(atSec - it) < 0.06f }
        assertTrue("pico en $atSec s", nearPop)
        assertTrue(rms(EggTimeline.POP, EggTimeline.POP + 0.05f) > 4 * rms(0.5f, 1.5f))
    }

    @Test
    fun `el loop empieza y termina en silencio para no chasquear al repetirse`() {
        assertTrue(rms(0f, 0.03f) < 0.01)
        assertTrue(rms(EggTimeline.LOOP - 0.1f, EggTimeline.LOOP) < 0.02)
    }
}
