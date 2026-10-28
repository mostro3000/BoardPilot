package ar.com.mt0.boardpilot

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.tanh
import kotlin.random.Random

/**
 * Linea de tiempo del huevo de pascua, comun a imagen, sonido y vibracion
 * (segundos dentro de cada ciclo). El loop son dos ciclos: en el par revienta
 * el venteo de la tapa; en el impar salta el tapon de goma y la lata vuela.
 */
internal object EggTimeline {
    /** 5 latidos de 1,2 s: el loop de audio cierra justo con el pulso de la firma. */
    const val CYCLE = 6.0f
    const val BEAT = 1.2f
    const val LOOP = 2 * CYCLE
    const val RAMP_END = 0.9f
    const val DOME_START = 1.3f
    const val BOIL_START = 1.7f
    const val POP = 3.2f
    const val LAND = 4.6f
    const val BOUNCE = 4.86f
    const val FADE = 0.35f
}

/** Lectura de la fuente de banco: tension, corriente y si esta limitando (CC). */
internal data class PsuReading(val volts: Float, val amps: Float, val limiting: Boolean)

private const val SET_VOLTS = 24f
private const val LIMIT_AMPS = 2f
private const val LEAK_START = 0.6f

/**
 * Fuente de banco en 24 V con limite de 2 A sobre un electrolitico de 16 V:
 * pasada su tension nominal, la fuga crece exponencialmente (el dielectrico
 * se rompe); al tocar el limite la fuente entra en CC y la tension cae.
 * Tras reventar, el capacitor queda abierto: 24 V y 0 A.
 */
internal fun psuReading(t: Float): PsuReading {
    if (t >= EggTimeline.POP) return PsuReading(SET_VOLTS, 0f, false)
    val ramp = (t / EggTimeline.RAMP_END).coerceIn(0f, 1f)
    val setVolts = SET_VOLTS * (1f - (1f - ramp).pow(3))
    val charge = if (ramp < 1f) 0.03f else 0f
    val leak = if (t < LEAK_START) 0f else 0.0037f * exp(2.8f * (t - LEAK_START))
    val demand = charge + leak
    return if (demand <= LIMIT_AMPS) {
        PsuReading(setVolts, demand, false)
    } else {
        PsuReading(setVolts * LIMIT_AMPS / demand, LIMIT_AMPS, true)
    }
}

/** Abombado de la tapa (0..1): arranca lento y se acelera hasta reventar. */
internal fun capDome(t: Float): Float =
    smoothstep(EggTimeline.DOME_START, EggTimeline.POP, t).pow(1.5f)

internal fun smoothstep(edge0: Float, edge1: Float, x: Float): Float {
    val k = ((x - edge0) / (edge1 - edge0)).coerceIn(0f, 1f)
    return k * k * (3f - 2f * k)
}

/**
 * Banda sonora del huevo, sintetizada en el momento (sin archivos de audio ni
 * licencias de terceros): clic del rele de la fuente, siseo y crepitar del
 * electrolito que hierve, zumbido de bobina de la fuente en limite de
 * corriente, el POP del venteo (o el estallido del tapon, el silbido de la
 * lata que vuela y su clink al caer), venteo de gas, trocitos que caen y el
 * latido lub-dub de la firma. Determinista: misma semilla, mismo audio.
 */
internal object EggSoundtrack {
    const val SAMPLE_RATE = 22050

    fun render(sampleRate: Int = SAMPLE_RATE): ShortArray {
        val synth = Synth(FloatArray((EggTimeline.LOOP * sampleRate).roundToInt()), sampleRate)
        for (n in 0..1) {
            renderCycle(synth, n * EggTimeline.CYCLE, rocket = n == 1, Random(1027 + n))
        }
        heartbeat(synth)
        return toPcm(synth.buf)
    }

    private fun renderCycle(s: Synth, t0: Float, rocket: Boolean, rng: Random) {
        fun noise() = rng.nextFloat() * 2f - 1f
        val pop = t0 + EggTimeline.POP

        // Rele de la fuente al habilitar la salida.
        var lpClick = 0f
        s.add(t0 + 0.04f, 0.015f) { a ->
            val x = noise()
            lpClick += 0.5f * (x - lpClick)
            (x - lpClick) * 0.35f * exp(-a / 0.0025f) +
                0.18f * sin(TAU * 2900f * a) * exp(-a / 0.004f)
        }

        // Hierve el electrolito: siseo agudo que crece, con burbujeo.
        val boil = EggTimeline.POP - EggTimeline.BOIL_START
        var lpBoil = 0f
        s.add(t0 + EggTimeline.BOIL_START, boil) { a ->
            val p = a / boil
            val x = noise()
            lpBoil += 0.35f * (x - lpBoil)
            val bubble = 0.75f + 0.25f * sin(TAU * 7.3f * a + 2f * sin(TAU * 1.1f * a))
            (x - lpBoil) * 0.16f * p.pow(1.6f) * bubble
        }
        // ...y crepita cada vez mas seguido.
        var tc = EggTimeline.BOIL_START
        while (tc < EggTimeline.POP) {
            val p = (tc - EggTimeline.BOIL_START) / boil
            if (rng.nextFloat() < (4f + 70f * p * p) * 0.001f) {
                val amp = (0.08f + 0.32f * p) * (0.5f + rng.nextFloat())
                s.add(t0 + tc, 0.006f) { a -> noise() * amp * exp(-a / 0.0012f) }
            }
            tc += 0.001f
        }

        // Zumbido de bobina de la fuente: sube con la corriente y se corta al reventar.
        var phWhine = 0f
        s.add(t0 + 2.4f, EggTimeline.POP - 2.4f) { a ->
            val amps = psuReading(2.4f + a).amps
            phWhine += TAU * (3300f + 550f * amps) / s.rate
            0.045f * (amps / LIMIT_AMPS).pow(1.5f) * (sin(phWhine) + 0.3f * sin(2f * phWhine))
        }

        // POP: chasquido + resonancia hueca + golpe grave (mas fuerte si sale el tapon).
        val big = if (rocket) 1.25f else 1f
        s.add(pop, 0.09f) { a -> noise() * 0.85f * big * exp(-a / 0.010f) }
        var phPok = 0f
        s.add(pop, 0.16f) { a ->
            phPok += TAU * (640f + 260f * exp(-a / 0.02f)) / s.rate
            0.5f * sin(phPok) * exp(-a / (0.028f * big))
        }
        var phThump = 0f
        s.add(pop, 0.35f) { a ->
            phThump += TAU * (48f + 105f * exp(-a / 0.045f)) / s.rate
            0.75f * big * sin(phThump) * exp(-a / (0.07f * big)) * min(1f, a / 0.002f)
        }

        // Venteo de gas tras el estallido, con chisporroteo.
        var lpHi = 0f
        var lpLo = 0f
        val ventTau = if (rocket) 0.32f else 0.55f
        s.add(pop + 0.008f, 2.2f) { a ->
            val x = noise()
            lpHi += 0.75f * (x - lpHi)
            lpLo += 0.18f * (x - lpLo)
            val sputter = 1f + 0.3f * sin(TAU * 13f * a) * sin(TAU * 3.1f * a)
            (lpHi - lpLo) * 0.34f * exp(-a / ventTau) * sputter * min(1f, a / 0.01f)
        }

        if (!rocket) {
            // Papel y aluminio del bobinado que caen sobre la placa.
            repeat(7) {
                val at = pop + 0.6f + rng.nextFloat() * 1.1f
                val f = 3800f + rng.nextFloat() * 3600f
                val amp = 0.05f + rng.nextFloat() * 0.09f
                val tau = 0.006f + rng.nextFloat() * 0.01f
                s.add(at, 0.06f) { a -> amp * sin(TAU * f * a) * exp(-a / tau) }
            }
            return
        }

        // Cohete: silbido brillante al salir y al volver a caer.
        val flight = EggTimeline.LAND - EggTimeline.POP
        var lpA = 0f
        var lpB = 0f
        s.add(pop + 0.02f, flight) { a ->
            val p = a / flight
            val bright = 0.10f + 0.55f * (1f - sin(PI.toFloat() * p))
            val x = noise()
            lpA += bright * (x - lpA)
            lpB += 0.04f * (x - lpB)
            (lpA - lpB) * 0.20f * (exp(-a / 0.18f) + 0.6f * smoothstep(0.7f, 1f, p))
        }
        // Clink de la lata de aluminio al caer y al rebotar (parciales inarmonicos).
        clink(s, t0 + EggTimeline.LAND, 1f)
        clink(s, t0 + EggTimeline.BOUNCE, 0.45f)
        // Rueda un poco: tics cada vez mas seguidos y suaves.
        var at = t0 + EggTimeline.BOUNCE + 0.12f
        var gap = 0.09f
        var gain = 0.10f
        repeat(6) {
            val g = gain
            s.add(at, 0.03f) { a -> g * sin(TAU * 4200f * a) * exp(-a / 0.004f) }
            at += gap
            gap *= 0.82f
            gain *= 0.75f
        }
    }

    private fun clink(s: Synth, at: Float, gain: Float) {
        s.add(at, 0.6f) { a ->
            gain * (0.42f * sin(TAU * 2050f * a) * exp(-a / 0.16f) +
                0.26f * sin(TAU * 3660f * a) * exp(-a / 0.10f) +
                0.15f * sin(TAU * 5710f * a) * exp(-a / 0.06f))
        }
        s.add(at, 0.08f) { a -> 0.3f * gain * sin(TAU * 170f * a) * exp(-a / 0.02f) }
    }

    /** Lub-dub de la firma h4ck3.ar, mismo pulso que la animacion; baja durante el estallido. */
    private fun heartbeat(s: Synth) {
        val beats = (EggTimeline.LOOP / EggTimeline.BEAT).roundToInt()
        for (k in 0 until beats) {
            val start = k * EggTimeline.BEAT
            thump(s, start + 0.10f * EggTimeline.BEAT, 0.20f)
            thump(s, start + 0.34f * EggTimeline.BEAT, 0.13f)
        }
    }

    private fun thump(s: Synth, at: Float, amp: Float) {
        val inCycle = at % EggTimeline.CYCLE
        val duck = if (inCycle in (EggTimeline.POP - 0.05f)..(EggTimeline.POP + 1.4f)) 0.3f else 1f
        var ph = 0f
        s.add(at, 0.32f) { a ->
            ph += TAU * (52f + 34f * exp(-a / 0.04f)) / s.rate
            amp * duck * (sin(ph) + 0.45f * sin(2f * ph)) * min(1f, a / 0.006f) * exp(-a / 0.075f)
        }
    }

    /** Limita suave (sin recortes) y pasa a PCM de 16 bits. */
    private fun toPcm(buf: FloatArray): ShortArray {
        val peak = buf.maxOf { abs(it) }.coerceAtLeast(1e-6f)
        val gain = if (peak > 0.95f) 0.95f / peak else 1f
        val norm = tanh(1.1f)
        return ShortArray(buf.size) { i ->
            (tanh(buf[i] * gain * 1.1f) / norm * 0.92f * 32767f).roundToInt()
                .coerceIn(-32767, 32767).toShort()
        }
    }

    private const val TAU = (2.0 * PI).toFloat()
}

/** Mezclador minimo: suma eventos de [len] segundos desde [t] (tiempo local [a]). */
private class Synth(val buf: FloatArray, val rate: Int) {
    inline fun add(t: Float, len: Float, sample: (a: Float) -> Float) {
        val start = (t * rate).roundToInt()
        val n = (len * rate).roundToInt()
        for (k in 0 until n) {
            val i = start + k
            if (i in buf.indices) buf[i] += sample(k.toFloat() / rate)
        }
    }
}
