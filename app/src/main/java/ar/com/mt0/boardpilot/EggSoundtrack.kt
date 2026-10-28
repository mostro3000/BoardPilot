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
 * Escenas del huevo de pascua en el orden del loop: C12 se alterna con un
 * componente chico para que nunca se repita la misma falla dos veces seguidas.
 */
internal enum class EggScene { VENT, RESISTOR, ROCKET, DIODE }

/**
 * Linea de tiempo comun a imagen, sonido y vibracion (segundos dentro de
 * cada escena). POP es el instante de la falla en todas las escenas.
 */
internal object EggTimeline {
    /** 5 latidos de 1,2 s: el loop de audio cierra justo con el pulso de la firma. */
    const val CYCLE = 6.0f
    const val BEAT = 1.2f
    const val LOOP = 4 * CYCLE
    const val RAMP_END = 0.9f
    const val DOME_START = 1.3f
    const val BOIL_START = 1.7f
    /** R7: se enciende la llama del recubrimiento. */
    const val FLAME = 2.6f
    const val POP = 3.2f
    const val LAND = 4.6f
    const val BOUNCE = 4.86f
    const val FADE = 0.35f
}

/** Escena y segundos dentro de ella para un instante del loop. */
internal fun eggSceneAt(loopT: Float): Pair<EggScene, Float> {
    val index = (loopT / EggTimeline.CYCLE).toInt().coerceIn(0, EggScene.entries.size - 1)
    return EggScene.entries[index] to loopT - index * EggTimeline.CYCLE
}

/** Lectura de la fuente de banco: tension, corriente y si esta limitando (CC). */
internal data class PsuReading(val volts: Float, val amps: Float, val limiting: Boolean)

private const val SET_VOLTS = 24f
private const val LIMIT_AMPS = 2f
private const val LEAK_START = 0.6f

/**
 * Fuente de banco en 24 V con limite de 2 A sobre la victima de cada escena;
 * la lectura cuenta la historia que un tecnico reconoce en el banco:
 * - C12 (16 V): pasada su tension nominal la fuga crece exponencialmente,
 *   la fuente entra en CC y la tension cae; al reventar queda abierto.
 * - R7 (10 ohm, 1/4 W): pide 2,4 A y la fuente limita en 20 V y 2 A (40 W);
 *   al carbonizarse sube su resistencia y al partirse queda abierta.
 * - D3 (1N4148 de 300 mA) en directa: CC a ~1,2 V; al calentarse baja su
 *   caida de tension y al reventar queda en corto.
 */
internal fun psuReading(scene: EggScene, t: Float): PsuReading = when (scene) {
    EggScene.VENT, EggScene.ROCKET -> capacitorReading(t)
    EggScene.RESISTOR -> resistorReading(t)
    EggScene.DIODE -> diodeReading(t)
}

private fun setVolts(t: Float): Float {
    val ramp = (t / EggTimeline.RAMP_END).coerceIn(0f, 1f)
    return SET_VOLTS * (1f - (1f - ramp).pow(3))
}

private fun capacitorReading(t: Float): PsuReading {
    if (t >= EggTimeline.POP) return PsuReading(SET_VOLTS, 0f, false)
    val charge = if (t < EggTimeline.RAMP_END) 0.03f else 0f
    val leak = if (t < LEAK_START) 0f else 0.0037f * exp(2.8f * (t - LEAK_START))
    val demand = charge + leak
    val volts = setVolts(t)
    return if (demand <= LIMIT_AMPS) {
        PsuReading(volts, demand, false)
    } else {
        PsuReading(volts * LIMIT_AMPS / demand, LIMIT_AMPS, true)
    }
}

private fun resistorReading(t: Float): PsuReading {
    if (t >= EggTimeline.POP) return PsuReading(SET_VOLTS, 0f, false)
    val ohms = 10f - 0.6f * smoothstep(0.4f, 2.0f, t) +
        5f * smoothstep(EggTimeline.FLAME, EggTimeline.POP, t).pow(2)
    val volts = setVolts(t)
    val demand = volts / ohms
    return if (demand <= LIMIT_AMPS) {
        PsuReading(volts, demand, false)
    } else {
        PsuReading(LIMIT_AMPS * ohms, LIMIT_AMPS, true)
    }
}

private fun diodeReading(t: Float): PsuReading {
    if (t >= EggTimeline.POP) return PsuReading(0.04f, LIMIT_AMPS, true)
    val volts = setVolts(t)
    val forward = 1.24f - 0.26f * smoothstep(0.3f, EggTimeline.POP, t)
    return if (volts < forward) PsuReading(volts, 0f, false) else PsuReading(forward, LIMIT_AMPS, true)
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
 * licencias de terceros): rele de la fuente, zumbido de bobina mientras
 * limita, y el sonido propio de cada falla (el POP de C12 o la lata que
 * vuela, R7 que chisporrotea, prende fuego y se parte, el vidrio de D3 que
 * se raja), mas el latido lub-dub de la firma. Determinista: misma semilla,
 * mismo audio.
 */
internal object EggSoundtrack {
    const val SAMPLE_RATE = 22050

    fun render(sampleRate: Int = SAMPLE_RATE): ShortArray {
        val synth = Synth(FloatArray((EggTimeline.LOOP * sampleRate).roundToInt()), sampleRate)
        EggScene.entries.forEachIndexed { n, scene ->
            renderScene(synth, n * EggTimeline.CYCLE, scene, Random(1027 + n))
        }
        heartbeat(synth)
        return toPcm(synth.buf)
    }

    private fun renderScene(s: Synth, t0: Float, scene: EggScene, rng: Random) {
        // Rele de la fuente al habilitar la salida.
        var lpClick = 0f
        s.add(t0 + 0.04f, 0.015f) { a ->
            val x = rng.noise()
            lpClick += 0.5f * (x - lpClick)
            (x - lpClick) * 0.35f * exp(-a / 0.0025f) +
                0.18f * sin(TAU * 2900f * a) * exp(-a / 0.004f)
        }

        // Zumbido de bobina de la fuente: sigue a la corriente. Con D3 en
        // corto la fuente sigue limitando, asi que no para hasta el fundido.
        val whineFrom = when (scene) {
            EggScene.RESISTOR -> 0.35f
            EggScene.DIODE -> 0.1f
            else -> 2.4f
        }
        val whineTo = if (scene == EggScene.DIODE) EggTimeline.CYCLE - EggTimeline.FADE else EggTimeline.POP
        var phWhine = 0f
        s.add(t0 + whineFrom, whineTo - whineFrom) { a ->
            val tt = whineFrom + a
            val amps = psuReading(scene, tt).amps
            phWhine += TAU * (3300f + 550f * amps) / s.rate
            val fade = if (scene == EggScene.DIODE) 1f - smoothstep(whineTo - 0.25f, whineTo, tt) else 1f
            0.045f * (amps / LIMIT_AMPS).pow(1.5f) * fade * (sin(phWhine) + 0.3f * sin(2f * phWhine))
        }

        when (scene) {
            EggScene.VENT -> capacitorSound(s, t0, rocket = false, rng)
            EggScene.ROCKET -> capacitorSound(s, t0, rocket = true, rng)
            EggScene.RESISTOR -> resistorSound(s, t0, rng)
            EggScene.DIODE -> diodeSound(s, t0, rng)
        }
    }

    private fun capacitorSound(s: Synth, t0: Float, rocket: Boolean, rng: Random) {
        val pop = t0 + EggTimeline.POP

        // Hierve el electrolito: siseo agudo que crece, con burbujeo.
        val boil = EggTimeline.POP - EggTimeline.BOIL_START
        var lpBoil = 0f
        s.add(t0 + EggTimeline.BOIL_START, boil) { a ->
            val p = a / boil
            val x = rng.noise()
            lpBoil += 0.35f * (x - lpBoil)
            val bubble = 0.75f + 0.25f * sin(TAU * 7.3f * a + 2f * sin(TAU * 1.1f * a))
            (x - lpBoil) * 0.16f * p.pow(1.6f) * bubble
        }
        // ...y crepita cada vez mas seguido.
        crackles(s, t0, EggTimeline.BOIL_START, EggTimeline.POP, rng) { p -> (4f + 70f * p * p) to (0.08f + 0.32f * p) }

        // POP: chasquido + resonancia hueca + golpe grave (mas fuerte si sale el tapon).
        val big = if (rocket) 1.25f else 1f
        s.add(pop, 0.09f) { a -> rng.noise() * 0.85f * big * exp(-a / 0.010f) }
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
            val x = rng.noise()
            lpHi += 0.75f * (x - lpHi)
            lpLo += 0.18f * (x - lpLo)
            val sputter = 1f + 0.3f * sin(TAU * 13f * a) * sin(TAU * 3.1f * a)
            (lpHi - lpLo) * 0.34f * exp(-a / ventTau) * sputter * min(1f, a / 0.01f)
        }

        if (!rocket) {
            // Papel y aluminio del bobinado que caen sobre la placa.
            tinks(s, pop + 0.6f, 1.1f, 7, 3800f, 7400f, 0.05f, 0.14f, rng)
            return
        }

        // Cohete: silbido brillante al salir y al volver a caer.
        val flight = EggTimeline.LAND - EggTimeline.POP
        var lpA = 0f
        var lpB = 0f
        s.add(pop + 0.02f, flight) { a ->
            val p = a / flight
            val bright = 0.10f + 0.55f * (1f - sin(PI.toFloat() * p))
            val x = rng.noise()
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

    /** R7: frita el recubrimiento, prende una llamita, se parte con un tic y se enfria. */
    private fun resistorSound(s: Synth, t0: Float, rng: Random) {
        val pop = t0 + EggTimeline.POP
        val from = 1.2f
        var lp = 0f
        s.add(t0 + from, EggTimeline.POP + 1.6f - from) { a ->
            val tt = from + a
            val grow = smoothstep(from, EggTimeline.FLAME, tt)
            val cool = if (tt > EggTimeline.POP) exp(-(tt - EggTimeline.POP) / 0.6f) else 1f
            val x = rng.noise()
            lp += 0.4f * (x - lp)
            (x - lp) * 0.10f * grow * cool * (0.7f + 0.3f * sin(TAU * 5.3f * a))
        }
        crackles(s, t0, from, EggTimeline.POP + 1.0f, rng) { p ->
            val tt = from + p * (EggTimeline.POP + 1.0f - from)
            val rate = when {
                tt < EggTimeline.FLAME -> 3f + 30f * smoothstep(from, EggTimeline.FLAME, tt)
                tt < EggTimeline.POP -> 35f
                else -> 35f * exp(-(tt - EggTimeline.POP) / 0.4f)
            }
            rate to 0.14f
        }
        // La llama: soplido grave al encenderse y rugido suave mientras arde.
        var lpFlame = 0f
        s.add(t0 + EggTimeline.FLAME, EggTimeline.POP + 0.6f - EggTimeline.FLAME) { a ->
            val tt = EggTimeline.FLAME + a
            val env = smoothstep(0f, 0.06f, a) *
                (if (tt > EggTimeline.POP) exp(-(tt - EggTimeline.POP) / 0.12f) else 1f)
            val x = rng.noise()
            lpFlame += 0.06f * (x - lpFlame)
            val whoomp = 1f + 1.5f * exp(-a / 0.08f)
            lpFlame * 0.9f * env * whoomp * (0.8f + 0.2f * sin(TAU * 11f * a + sin(TAU * 3f * a)))
        }
        // Se parte: chasquido seco, un tic agudo y un golpecito.
        s.add(pop, 0.04f) { a -> rng.noise() * 0.55f * exp(-a / 0.003f) }
        s.add(pop, 0.05f) { a -> 0.3f * sin(TAU * 3400f * a) * exp(-a / 0.006f) }
        var phTick = 0f
        s.add(pop, 0.08f) { a ->
            phTick += TAU * (140f + 120f * exp(-a / 0.01f)) / s.rate
            0.25f * sin(phTick) * exp(-a / 0.015f)
        }
        // Tics del metal al enfriarse.
        listOf(0.8f, 1.5f, 2.1f).forEach { d ->
            val f = 5000f + rng.nextFloat() * 2200f
            s.add(pop + d, 0.04f) { a -> 0.05f * sin(TAU * f * a) * exp(-a / 0.008f) }
        }
    }

    /** D3: hierve el flux, el vidrio se raja (dos chasquidos cristalinos) y caen esquirlas. */
    private fun diodeSound(s: Synth, t0: Float, rng: Random) {
        val pop = t0 + EggTimeline.POP
        val from = 1.6f
        var lp = 0f
        s.add(t0 + from, EggTimeline.POP + 0.8f - from) { a ->
            val tt = from + a
            val env = smoothstep(from, EggTimeline.POP, tt) *
                (if (tt > EggTimeline.POP) exp(-(tt - EggTimeline.POP) / 0.3f) else 1f)
            val x = rng.noise()
            lp += 0.5f * (x - lp)
            (x - lp) * 0.06f * env
        }
        crackles(s, t0, from, EggTimeline.POP, rng) { p -> (2f + 13f * p) to 0.08f }
        glassCrack(s, pop, 1f, rng)
        glassCrack(s, pop + 0.12f, 0.5f, rng)
        var lpPuff = 0f
        s.add(pop + 0.01f, 0.6f) { a ->
            val x = rng.noise()
            lpPuff += 0.5f * (x - lpPuff)
            (x - lpPuff) * 0.08f * exp(-a / 0.2f)
        }
        tinks(s, pop + 0.4f, 0.5f, 3, 6000f, 9000f, 0.04f, 0.07f, rng)
    }

    private fun glassCrack(s: Synth, at: Float, gain: Float, rng: Random) {
        s.add(at, 0.03f) { a -> rng.noise() * 0.7f * gain * exp(-a / 0.0015f) }
        s.add(at, 0.2f) { a ->
            gain * (0.25f * sin(TAU * 5200f * a) * exp(-a / 0.025f) +
                0.15f * sin(TAU * 7900f * a) * exp(-a / 0.015f) +
                0.10f * sin(TAU * 9600f * a) * exp(-a / 0.010f))
        }
    }

    /** Chasquidos sueltos (Poisson): [shape] da tasa por segundo y amplitud segun el avance 0..1. */
    private inline fun crackles(
        s: Synth,
        t0: Float,
        from: Float,
        to: Float,
        rng: Random,
        shape: (p: Float) -> Pair<Float, Float>,
    ) {
        var tc = from
        while (tc < to) {
            val (rate, amp) = shape((tc - from) / (to - from))
            if (rng.nextFloat() < rate * 0.001f) {
                val a0 = amp * (0.5f + rng.nextFloat())
                s.add(t0 + tc, 0.006f) { a -> rng.noise() * a0 * exp(-a / 0.0012f) }
            }
            tc += 0.001f
        }
    }

    /** Pequenos golpecitos agudos (trocitos que caen) repartidos en [span] segundos. */
    private fun tinks(
        s: Synth,
        from: Float,
        span: Float,
        count: Int,
        fLow: Float,
        fHigh: Float,
        ampLow: Float,
        ampHigh: Float,
        rng: Random,
    ) {
        repeat(count) {
            val at = from + rng.nextFloat() * span
            val f = fLow + rng.nextFloat() * (fHigh - fLow)
            val amp = ampLow + rng.nextFloat() * (ampHigh - ampLow)
            val tau = 0.006f + rng.nextFloat() * 0.01f
            s.add(at, 0.06f) { a -> amp * sin(TAU * f * a) * exp(-a / tau) }
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

    /** Lub-dub de la firma h4ck3.ar, mismo pulso que la animacion; baja durante cada falla. */
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

private fun Random.noise(): Float = nextFloat() * 2f - 1f

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
