package ar.com.mt0.boardpilot

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTimestamp
import android.media.AudioTrack
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import ar.com.mt0.boardpilot.i18n.AppStrings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Huevo de pascua de la familia MT: cada app anima el objeto de su dominio
 * sobre fondo negro con starfield y firma "h4ck3.ar" en latido lub-dub.
 * Aca, una fuente de banco (24 V, limite 2 A) mata un componente distinto en
 * cada escena, como en un banco de verdad: C12 (16 V) revienta el venteo,
 * R7 (10 ohm, 1/4 W) se carboniza, prende fuego y se parte, C12 sale volando
 * como un cohete, y D3 (1N4148 de vidrio) se pone incandescente y se raja.
 * Sonido sintetizado en el momento (EggSoundtrack) y vibracion en cada falla.
 * Se abre con 6 toques sobre el titulo "BoardPilot"; tocar la pantalla cierra.
 */

private const val EGG_SIGNATURE = "h4ck3.ar"

/** Seno del angulo de vista (~20 grados): achata elipses y textos sobre la placa. */
private const val TILT = 0.34f

private val SLEEVE_DARK = Color(0xFF0B0D10)
private val SLEEVE_RIM = Color(0xFF262B32)
private val PAD_METAL = Color(0xFFC9CED6)
private val SILK = Color(0xFFE9DDD8)
private val ELECTROLYTE = Color(0xFF2A1909)
private val PAPER = Color(0xFFE6D9BC)

@Composable
fun EasterEggScreen(strings: AppStrings, onExit: () -> Unit) {
    val view = LocalView.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val audio = remember { arrayOfNulls<EggAudio>(1) }
    var timeSec by remember { mutableFloatStateOf(0f) }

    // Como un video: la pantalla no se apaga mientras corre el loop de 24 s.
    // El sonido se pausa con la app en segundo plano y se libera al salir.
    DisposableEffect(lifecycleOwner) {
        view.keepScreenOn = true
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> audio[0]?.pause()
                Lifecycle.Event.ON_RESUME -> audio[0]?.resume()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            audio[0]?.release()
            audio[0] = null
            view.keepScreenOn = false
        }
    }
    LaunchedEffect(Unit) {
        val pcm = withContext(Dispatchers.Default) { EggSoundtrack.render() }
        audio[0] = EggAudio.start(pcm)
        val start = withFrameNanos { it }
        var last = -1f
        while (true) {
            withFrameNanos { now ->
                // Reloj maestro: lo que ya se escucho, asi imagen, sonido y
                // vibracion van juntos; sin audio, el reloj de cuadros.
                val t = (audio[0]?.heardSeconds() ?: ((now - start) / 1e9)).toFloat()
                if (last >= 0f && t - last in 0f..0.3f) {
                    eggHaptics(view, last % EggTimeline.LOOP, t % EggTimeline.LOOP)
                }
                last = t
                timeSec = t
            }
        }
    }

    val seeds = remember { EggSeeds() }
    val measurer = rememberTextMeasurer(cacheSize = 32)
    val (scene, t) = eggSceneAt(timeSec % EggTimeline.LOOP)
    val (header, during, after) = when (scene) {
        EggScene.VENT, EggScene.ROCKET ->
            Triple(strings.eggHeader, strings.eggCharging, strings.eggAftermath)
        EggScene.RESISTOR ->
            Triple(strings.eggResistorHeader, strings.eggResistorDuring, strings.eggResistorAfter)
        EggScene.DIODE ->
            Triple(strings.eggDiodeHeader, strings.eggDiodeDuring, strings.eggDiodeAfter)
    }

    val beat = (timeSec % EggTimeline.BEAT) / EggTimeline.BEAT
    val lub = exp(-((beat - 0.10f) * (beat - 0.10f)) / 0.004f)
    val dub = 0.6f * exp(-((beat - 0.34f) * (beat - 0.34f)) / 0.005f)
    val pulse = (lub + dub).coerceIn(0f, 1f)
    val beatScale = 1f + 0.16f * pulse

    Box(
        Modifier.fillMaxSize()
            .background(Color(0xFF050203))
            .clickable { onExit() },
    ) {
        Canvas(Modifier.fillMaxSize()) { drawEggScene(scene, t, timeSec, seeds, measurer) }
        Column(
            Modifier.align(Alignment.TopCenter).padding(top = 56.dp, start = 20.dp, end = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(header, color = Color(0xFFFFB300),
                fontFamily = FontFamily.Monospace, fontSize = 14.sp,
                fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            Text(
                if (t < EggTimeline.POP) during else after,
                color = Color(0xFFF5E9E2), fontFamily = FontFamily.Monospace,
                fontSize = 13.sp, textAlign = TextAlign.Center,
            )
            PsuPanel(strings, psuReading(scene, t))
        }
        Text(
            EGG_SIGNATURE,
            color = lerp(Color(0xFFC2253B), Color(0xFFFFB300), pulse),
            fontFamily = FontFamily.Monospace, fontSize = 38.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.align(Alignment.BottomCenter)
                .padding(bottom = 64.dp)
                .graphicsLayer(scaleX = beatScale, scaleY = beatScale),
        )
    }
}

/** Display de la fuente de banco: la luz CC se enciende al limitar la corriente. */
@Composable
private fun PsuPanel(strings: AppStrings, psu: PsuReading) {
    val shape = RoundedCornerShape(6.dp)
    Text(
        buildAnnotatedString {
            append("${strings.eggPsu}  ")
            val volts = if (psu.volts < 10f) "%4.2f" else "%4.1f"
            append(String.format(Locale.US, "$volts V  %4.2f A", psu.volts, psu.amps))
            withStyle(SpanStyle(
                color = if (psu.limiting) Color(0xFFFF5A4E) else Color(0x40FF5A4E),
            )) { append("  CC") }
        },
        color = Color(0xFFFFB300), fontFamily = FontFamily.Monospace,
        fontSize = 15.sp, fontWeight = FontWeight.Bold,
        modifier = Modifier.background(Color(0xFF120709), shape)
            .border(1.dp, Color(0xFF5A2A30), shape)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    )
}

/** Vibracion fuerte en los estallidos de C12; suave cuando cae la lata y cuando R7 o D3 se parten. */
private val STRONG_HITS = floatArrayOf(EggTimeline.POP, 2 * EggTimeline.CYCLE + EggTimeline.POP)
private val LIGHT_HITS = floatArrayOf(
    EggTimeline.CYCLE + EggTimeline.POP,
    2 * EggTimeline.CYCLE + EggTimeline.LAND,
    3 * EggTimeline.CYCLE + EggTimeline.POP,
)

private fun eggHaptics(view: View, from: Float, to: Float) {
    fun crossed(at: Float) = if (from <= to) at > from && at <= to else at > from || at <= to
    when {
        STRONG_HITS.any { crossed(it) } ->
            view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        LIGHT_HITS.any { crossed(it) } ->
            view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
    }
}

/**
 * Banda sonora en loop por AudioTrack (volumen multimedia). Ademas es el
 * reloj maestro de la animacion: segundos ya escuchados, latencia de salida
 * incluida, para que cada falla suene justo cuando se ve.
 */
private class EggAudio private constructor(
    private val pcm: ShortArray,
    private val track: AudioTrack,
) {
    private val rate = EggSoundtrack.SAMPLE_RATE
    private val stamp = AudioTimestamp()

    @Volatile
    private var running = true
    private val writer = Thread({
        var pos = 0
        while (running) {
            val n = min(2048, pcm.size - pos)
            val written = runCatching { track.write(pcm, pos, n) }.getOrDefault(-1)
            if (written < 0) break
            pos = (pos + written) % pcm.size
        }
    }, "boardpilot-egg-audio")

    fun pause() {
        runCatching { track.pause() }
    }

    fun resume() {
        runCatching { track.play() }
    }

    fun release() {
        running = false
        runCatching {
            track.pause()
            track.flush()
        }
        runCatching { track.release() }
    }

    fun heardSeconds(): Double? {
        if (!runCatching { track.getTimestamp(stamp) }.getOrDefault(false)) return null
        // Extrapola desde la ultima marca, sin saltar tras una pausa larga.
        val since = (System.nanoTime() - stamp.nanoTime).coerceIn(0L, 200_000_000L)
        return (stamp.framePosition + since * rate / 1e9) / rate
    }

    companion object {
        /** Arranca el loop; sin audio disponible devuelve null y el huevo sigue mudo. */
        fun start(pcm: ShortArray): EggAudio? {
            val rate = EggSoundtrack.SAMPLE_RATE
            val track = runCatching {
                val minBuffer = AudioTrack.getMinBufferSize(
                    rate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
                AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_GAME)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build(),
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(rate)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build(),
                    )
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .setBufferSizeInBytes(max(minBuffer, rate / 5 * 2))
                    .build()
            }.getOrNull() ?: return null
            return runCatching {
                EggAudio(pcm, track).also {
                    track.play()
                    it.writer.start()
                }
            }.getOrElse {
                runCatching { track.release() }
                null
            }
        }
    }
}

// --- Escena ---------------------------------------------------------------

/** Geometria: placa en perspectiva (u izquierda->derecha, v fondo->frente) y C12 de pie. */
private class EggStage(val w: Float, val h: Float) {
    val farY = h * 0.62f
    val nearY = h * 0.71f
    val capW = min(w, h) * 0.20f
    val capH = capW * 1.55f
    val base = at(0.5f, 0.42f)
    val vent = Offset(base.x, base.y - capH)

    fun at(u: Float, v: Float): Offset {
        val left = mix(w * 0.10f, w * 0.04f, v)
        val right = mix(w * 0.90f, w * 0.96f, v)
        return Offset(mix(left, right, u), mix(farY, nearY, v))
    }

    fun onBoard(x: Float, v: Float): Boolean =
        x in mix(w * 0.10f, w * 0.04f, v)..mix(w * 0.90f, w * 0.96f, v)
}

private fun DrawScope.drawEggScene(
    scene: EggScene,
    t: Float,
    timeSec: Float,
    seeds: EggSeeds,
    m: TextMeasurer,
) {
    val st = EggStage(size.width, size.height)
    val age = t - EggTimeline.POP
    drawBackdrop(st, timeSec, seeds.stars)
    val cam = camera(scene, t, st)
    val shake = shakeOffset(scene, t, age)
    withTransform({
        translate(cam.shift.x + shake.x, cam.shift.y + shake.y)
        scale(cam.zoom, cam.zoom, cam.pivot)
    }) {
        drawBoard(st, m)
        when (scene) {
            EggScene.VENT -> drawVentFailure(st, t, age, seeds, m)
            EggScene.RESISTOR -> drawResistorFailure(st, t, age, seeds, m)
            EggScene.ROCKET -> drawRocketFailure(st, t, age, seeds, m)
            EggScene.DIODE -> drawDiodeFailure(st, t, age, seeds, m)
        }
    }
    // Fundido al terminar cada escena y al empezar la siguiente: reinicia sin saltos.
    val fade = max(
        1f - smoothstep(0f, EggTimeline.FADE, t),
        smoothstep(EggTimeline.CYCLE - EggTimeline.FADE, EggTimeline.CYCLE, t),
    )
    if (fade > 0.001f) drawRect(Color(0xFF050203).copy(alpha = fade))
}

private class Camera(val zoom: Float, val pivot: Offset, val shift: Offset)

/** Con R7 y D3 la camara se acerca a la victima, como una toma macro. */
private fun camera(scene: EggScene, t: Float, st: EggStage): Camera {
    val (focus, zoom) = when (scene) {
        EggScene.RESISTOR -> resistorGeom(st).center to 2.8f
        EggScene.DIODE -> diodeGeom(st).center to 3.2f
        else -> return Camera(1f, Offset.Zero, Offset.Zero)
    }
    val k = smoothstep(0.2f, 1.6f, t)
    val target = Offset(st.w * 0.5f, st.h * 0.55f)
    return Camera(mix(1f, zoom, k), focus, (target - focus) * k)
}

/** Sacudon en cada falla (fuerte con C12) y uno chico cuando la lata cae. */
private fun DrawScope.shakeOffset(scene: EggScene, t: Float, age: Float): Offset {
    var amp = 0f
    var a = 0f
    if (age in 0f..0.5f) {
        amp = when (scene) {
            EggScene.ROCKET -> 9f
            EggScene.VENT -> 7f
            else -> 2.5f
        } * exp(-age / 0.1f)
        a = age
    }
    val land = t - EggTimeline.LAND
    if (scene == EggScene.ROCKET && land in 0f..0.3f) {
        amp = max(amp, 3f * exp(-land / 0.06f))
        a = land
    }
    if (amp < 0.05f) return Offset.Zero
    return Offset(sin(a * 91f) * amp.dp.toPx(), cos(a * 77f) * amp.dp.toPx())
}

private fun DrawScope.drawBackdrop(st: EggStage, timeSec: Float, stars: List<EggStar>) {
    for (star in stars) {
        val tw = 0.5f + 0.5f * sin(timeSec * 2f + star.phase)
        drawCircle(
            Color(0xFFD8DEE8).copy(alpha = 0.06f + 0.20f * tw),
            star.radiusDp.dp.toPx(),
            Offset(star.x * st.w, star.y * st.h),
        )
    }
    // Luz calida del banco de trabajo sobre la placa.
    val light = Offset(st.w / 2f, st.farY - st.capH * 0.4f)
    val reach = st.w * 0.85f
    drawCircle(
        Brush.radialGradient(
            listOf(Color(0x24FFE3BC), Color(0x0DFFE3BC), Color.Transparent), light, reach),
        reach, light,
    )
}

private fun DrawScope.drawBoard(st: EggStage, m: TextMeasurer) {
    val fl = st.at(0f, 0f)
    val fr = st.at(1f, 0f)
    val nr = st.at(1f, 1f)
    val nl = st.at(0f, 1f)
    val edge = 8.dp.toPx()
    // Canto de FR4 y cara superior con mascara bordo bajo la luz del banco.
    drawPath(
        Path().apply {
            moveTo(nl.x, nl.y); lineTo(nr.x, nr.y)
            lineTo(nr.x, nr.y + edge); lineTo(nl.x, nl.y + edge); close()
        },
        Brush.verticalGradient(listOf(Color(0xFF7A6646), Color(0xFF2E2216)), nl.y, nl.y + edge),
    )
    drawPath(
        Path().apply {
            moveTo(fl.x, fl.y); lineTo(fr.x, fr.y); lineTo(nr.x, nr.y); lineTo(nl.x, nl.y); close()
        },
        Brush.verticalGradient(listOf(Color(0xFF34101A), Color(0xFF581B2B)), fl.y, nl.y),
    )
    // Pistas de cobre bajo la mascara, vias y pads estanados.
    val trace = Color(0xFF7E2A3C)
    val traceW = 2.2.dp.toPx()
    fun track(vararg uv: Float) {
        for (i in 0 until uv.size / 2 - 1) {
            drawLine(trace, st.at(uv[2 * i], uv[2 * i + 1]),
                st.at(uv[2 * i + 2], uv[2 * i + 3]), traceW, StrokeCap.Round)
        }
    }
    track(0.36f, 0.80f, 0.36f, 0.56f, 0.46f, 0.42f)
    track(0.64f, 0.80f, 0.64f, 0.56f, 0.54f, 0.42f)
    track(0.27f, 0.62f, 0.36f, 0.62f)
    track(0.13f, 0.62f, 0f, 0.62f)
    track(0.73f, 0.22f, 0.60f, 0.22f, 0.54f, 0.42f)
    track(0.87f, 0.22f, 1f, 0.22f)
    listOf(0.36f to 0.56f, 0.64f to 0.56f, 0.60f to 0.22f).forEach { (u, v) ->
        drawCircle(trace, 3.dp.toPx(), st.at(u, v))
        drawCircle(Color(0xFF1A0A0E), 1.3.dp.toPx(), st.at(u, v))
    }
    listOf(
        0.46f to 0.42f, 0.54f to 0.42f, 0.13f to 0.62f, 0.27f to 0.62f,
        0.73f to 0.22f, 0.87f to 0.22f, 0.36f to 0.80f, 0.64f to 0.80f,
    ).forEach { (u, v) -> drawPad(st.at(u, v), 5.dp.toPx()) }
    drawSilk(m, "C12", st.at(0.5f, 0.70f), st)
    drawSilk(m, "+", st.at(0.41f, 0.53f), st)
    drawSilk(m, "R7", st.at(0.20f, 0.79f), st)
    drawSilk(m, "D3", st.at(0.80f, 0.36f), st)
    drawSilk(m, "TP+", st.at(0.27f, 0.88f), st)
    drawSilk(m, "TP−", st.at(0.73f, 0.88f), st)
}

private fun DrawScope.drawPad(c: Offset, r: Float) {
    drawOval(
        Brush.radialGradient(
            listOf(Color(0xFFEFF2F6), PAD_METAL, Color(0xFF8D949D)),
            Offset(c.x - r * 0.3f, c.y - r * TILT * 0.3f), r * 1.2f,
        ),
        Offset(c.x - r, c.y - r * TILT), Size(2f * r, 2f * r * TILT),
    )
}

/** Serigrafia: impresa sobre la placa, achatada por la perspectiva. */
private fun DrawScope.drawSilk(m: TextMeasurer, text: String, at: Offset, st: EggStage) {
    val layout = m.measure(text, TextStyle(
        color = SILK, fontSize = (st.capW * 0.14f).toSp(), fontWeight = FontWeight.Bold))
    scale(1f, 0.55f, at) {
        drawText(layout, alpha = 0.8f,
            topLeft = Offset(at.x - layout.size.width / 2f, at.y - layout.size.height / 2f))
    }
}

// --- R7 y D3: componentes axiales acostados -------------------------------

/** Axial acostado entre dos pads: patas que suben al eje y cuerpo entre ellas. */
private class Axial(st: EggStage, u1: Float, u2: Float, v: Float, diameter: Float, inset: Float) {
    val left = st.at(u1, v)
    val right = st.at(u2, v)
    val d = st.capW * diameter
    val y = (left.y + right.y) / 2f - d * 0.75f
    val bodyL = mix(left.x, right.x, inset)
    val bodyR = mix(left.x, right.x, 1f - inset)
    val len = bodyR - bodyL
    val center = Offset((bodyL + bodyR) / 2f, y)

    /** Donde muerde la pinza: la pata, entre el pad y el cuerpo. */
    fun bite(leftLead: Boolean): Offset {
        val pad = if (leftLead) left else right
        return Offset(pad.x, mix(pad.y, y, 0.45f))
    }
}

private fun resistorGeom(st: EggStage) = Axial(st, 0.13f, 0.27f, 0.62f, 0.15f, 0.18f)

private fun diodeGeom(st: EggStage) = Axial(st, 0.73f, 0.87f, 0.22f, 0.10f, 0.22f)

private fun DrawScope.drawAxialLeads(g: Axial) {
    val lead = Color(0xFFB9C0C8)
    val w = 1.6.dp.toPx()
    drawLine(lead, g.left, Offset(g.left.x, g.y), w)
    drawLine(lead, Offset(g.left.x, g.y), Offset(g.bodyL, g.y), w)
    drawLine(lead, g.right, Offset(g.right.x, g.y), w)
    drawLine(lead, Offset(g.right.x, g.y), Offset(g.bodyR, g.y), w)
    drawOval(Color.Black.copy(alpha = 0.35f), Offset(g.bodyL, g.left.y - g.d * 0.25f),
        Size(g.len, g.d * 0.5f))
}

/**
 * R7: 10 ohm 1/4 W (marron, negro, negro, oro). Con 40 W se carboniza desde
 * el centro ([char]), se pone incandescente ([glow]) y al partirse ([split])
 * las dos mitades se levantan y queda abierta.
 */
private fun DrawScope.drawResistor(st: EggStage, char: Float = 0f, glow: Float = 0f, split: Float = 0f) {
    val g = resistorGeom(st)
    drawAxialLeads(g)
    val top = g.y - g.d / 2f
    val corner = CornerRadius(g.d * 0.45f)
    fun body() {
        drawRoundRect(
            Brush.verticalGradient(
                listOf(
                    lerp(Color(0xFFEBD9AE), Color(0xFF6A513C), char * 0.7f),
                    lerp(Color(0xFFC8AE7C), Color(0xFF4A3526), char * 0.7f),
                    lerp(Color(0xFF8C744E), Color(0xFF2A1C12), char * 0.7f),
                ),
                top, top + g.d,
            ),
            Offset(g.bodyL, top), Size(g.len, g.d), corner,
        )
        listOf(0.20f to Color(0xFF6B3A1F), 0.34f to Color(0xFF151515),
            0.48f to Color(0xFF151515), 0.76f to Color(0xFFC9A227)).forEach { (f, c) ->
            drawRect(c.copy(alpha = 1f - 0.8f * char), Offset(g.bodyL + g.len * f, top + 1f),
                Size(g.len * 0.07f, g.d - 2f))
        }
        if (char > 0f) {
            drawRoundRect(
                Brush.horizontalGradient(
                    listOf(Color.Transparent, Color(0xFF2B1D14).copy(alpha = 0.95f * char),
                        Color.Transparent),
                    g.bodyL, g.bodyR,
                ),
                Offset(g.bodyL, top), Size(g.len, g.d), corner,
            )
        }
        drawLine(Color.White.copy(alpha = 0.25f * (1f - char)), Offset(g.bodyL + g.d * 0.3f, g.y - g.d * 0.25f),
            Offset(g.bodyR - g.d * 0.3f, g.y - g.d * 0.25f), 1.5.dp.toPx(), StrokeCap.Round)
    }
    if (split <= 0f) {
        body()
    } else {
        val gap = g.len * 0.14f * split
        val tilt = 16f * split
        clipRect(right = g.center.x - gap / 2f) {
            rotate(-tilt, Offset(g.bodyL, top + g.d)) { body() }
        }
        clipRect(left = g.center.x + gap / 2f) {
            rotate(tilt, Offset(g.bodyR, top + g.d)) { body() }
        }
    }
    drawHotSpot(g.center, glow, g.len * 0.45f)
}

/**
 * D3: 1N4148 de vidrio ambar (300 mA) con el chip a la vista. Con 2 A el
 * chip se pone incandescente ([glow]); al reventar el vidrio se raja
 * ([cracked]), se ennegrece por dentro ([soot]) y queda en corto.
 */
private fun DrawScope.drawGlassDiode(st: EggStage, glow: Float = 0f, cracked: Float = 0f, soot: Float = 0f) {
    val g = diodeGeom(st)
    drawAxialLeads(g)
    val top = g.y - g.d / 2f
    val corner = CornerRadius(g.d * 0.5f)
    drawRoundRect(
        Brush.verticalGradient(
            listOf(Color(0xE6F8B26A), Color(0xD9D9541E), Color(0xE68E2A0C)), top, top + g.d),
        Offset(g.bodyL, top), Size(g.len, g.d), corner,
    )
    // Por dentro: los dos contactos y el chip entre ellos.
    val slugH = g.d * 0.42f
    drawRect(Color(0xFF3A3A3D), Offset(g.bodyL + g.len * 0.06f, g.y - slugH / 2f),
        Size(g.len * 0.36f, slugH))
    drawRect(Color(0xFF3A3A3D), Offset(g.center.x + g.len * 0.08f, g.y - slugH / 2f),
        Size(g.len * 0.36f, slugH))
    drawRect(Color(0xFF1C1C1E), Offset(g.center.x - g.len * 0.06f, g.y - slugH * 0.4f),
        Size(g.len * 0.12f, slugH * 0.8f))
    // Banda del catodo.
    drawRect(Color(0xF0141414), Offset(g.bodyL + g.len * 0.80f, top + 1f), Size(g.len * 0.09f, g.d - 2f))
    if (soot > 0f) {
        drawRoundRect(
            Brush.horizontalGradient(
                listOf(Color.Transparent, Color(0xFF140A06).copy(alpha = 0.8f * soot),
                    Color.Transparent),
                g.bodyL, g.bodyR,
            ),
            Offset(g.bodyL, top), Size(g.len, g.d), corner,
        )
    }
    drawLine(Color.White.copy(alpha = 0.45f), Offset(g.bodyL + g.d * 0.3f, g.y - g.d * 0.28f),
        Offset(g.bodyR - g.d * 0.3f, g.y - g.d * 0.28f), 1.2.dp.toPx(), StrokeCap.Round)
    if (cracked > 0f) {
        val crack = Color.White.copy(alpha = 0.85f * cracked)
        val w = 1.dp.toPx()
        listOf(
            Offset(-0.26f, -0.5f), Offset(0.20f, 0.5f), Offset(0.34f, -0.45f),
            Offset(-0.12f, 0.5f), Offset(-0.38f, 0.2f),
        ).forEach { (fx, fy) ->
            val end = Offset(g.center.x + g.len * fx, g.center.y + g.d * fy)
            val mid = Offset(g.center.x + g.len * fx * 0.5f + g.d * 0.12f, g.center.y + g.d * fy * 0.35f)
            drawLine(crack, g.center, mid, w)
            drawLine(crack, mid, end, w)
        }
    }
    drawHotSpot(g.center, glow, g.len * 0.55f)
}

/** Color de incandescencia: rojo oscuro -> naranja -> amarillo blanquecino. */
private fun heatColor(k: Float): Color = when {
    k < 0.35f -> lerp(Color(0xFF4A0C04), Color(0xFFB3260A), k / 0.35f)
    k < 0.7f -> lerp(Color(0xFFB3260A), Color(0xFFFF7A1A), (k - 0.35f) / 0.35f)
    else -> lerp(Color(0xFFFF7A1A), Color(0xFFFFE6A3), ((k - 0.7f) / 0.3f).coerceAtMost(1f))
}

private fun DrawScope.drawHotSpot(at: Offset, k: Float, radius: Float) {
    if (k <= 0.02f) return
    val c = heatColor(k)
    val a = smoothstep(0f, 0.3f, k)
    drawCircle(
        Brush.radialGradient(listOf(c.copy(alpha = a), c.copy(alpha = a * 0.5f), Color.Transparent),
            at, radius),
        radius, at,
    )
}

/** Luz del componente al rojo sobre la placa. */
private fun DrawScope.drawGlowSpill(at: Offset, k: Float, radius: Float) {
    if (k <= 0.02f) return
    drawOval(
        Brush.radialGradient(listOf(heatColor(k).copy(alpha = 0.35f * k), Color.Transparent), at, radius),
        Offset(at.x - radius, at.y - radius * TILT), Size(2f * radius, 2f * radius * TILT),
    )
}

/** Mascara chamuscada debajo de lo que se quemo. */
private fun DrawScope.drawScorch(at: Offset, rx: Float, alpha: Float) {
    if (alpha <= 0.01f) return
    val ry = rx * TILT
    drawOval(
        Brush.radialGradient(
            listOf(Color(0xFF140805).copy(alpha = alpha), Color(0xFF3A1A0A).copy(alpha = alpha * 0.6f),
                Color.Transparent),
            at, rx,
        ),
        Offset(at.x - rx, at.y - ry), Size(2f * rx, 2f * ry),
    )
}

/** Llama del recubrimiento quemandose: titila y se mece; base azulada, punta amarilla. */
private fun DrawScope.drawFlame(base: Offset, height: Float, width: Float, t: Float, strength: Float) {
    if (strength <= 0.01f) return
    val flicker = 0.82f + 0.18f * sin(t * 31f) * sin(t * 17f + 1.3f)
    val sway = sin(t * 9f) * width * 0.3f
    fun tongue(scale: Float, colors: List<Color>) {
        val w = width * scale
        val h = height * scale * flicker * strength
        val tip = Offset(base.x + sway * scale, base.y - h)
        val path = Path().apply {
            moveTo(base.x - w / 2f, base.y)
            cubicTo(base.x - w * 0.62f, base.y - h * 0.45f, tip.x - w * 0.18f, base.y - h * 0.78f, tip.x, tip.y)
            cubicTo(tip.x + w * 0.18f, base.y - h * 0.78f, base.x + w * 0.62f, base.y - h * 0.45f,
                base.x + w / 2f, base.y)
            close()
        }
        drawPath(path, Brush.verticalGradient(colors, tip.y, base.y))
    }
    tongue(1f, listOf(Color(0x00FF6A1A), Color(0xB3FF6A1A), Color(0xE6FFA53A)))
    tongue(0.62f, listOf(Color(0x00FFF2B0), Color(0xE6FFE38A), Color(0xB36E9BFF)))
}

// --- Fuente de banco ---------------------------------------------------------

/**
 * Pinzas de la fuente: en los puntos de prueba de C12, o mordiendo las patas
 * de R7 o D3 (roja al +, al anodo de D3). Las mangas apuntan hacia sus cables.
 */
private fun DrawScope.drawTestLeads(st: EggStage, scene: EggScene) {
    val tpPlus = drawTestPin(st, st.at(0.36f, 0.80f))
    val tpMinus = drawTestPin(st, st.at(0.64f, 0.80f))
    val (plus, minus, tilt) = when (scene) {
        EggScene.RESISTOR -> resistorGeom(st).let { Triple(it.bite(true), it.bite(false), 40f) }
        EggScene.DIODE -> diodeGeom(st).let { Triple(it.bite(true), it.bite(false), 40f) }
        else -> Triple(tpPlus, tpMinus, 20f)
    }
    drawTestLead(plus, Offset(-0.08f * st.w, st.h * 0.88f), Color(0xFFC62828), tilt)
    drawTestLead(minus, Offset(1.08f * st.w, st.h * 0.88f), Color(0xFF1E1E1E), -tilt)
}

private fun DrawScope.drawTestPin(st: EggStage, base: Offset): Offset {
    val top = Offset(base.x, base.y - st.capW * 0.24f)
    drawLine(Color(0xFFBFC6CE), base, top, 2.dp.toPx(), StrokeCap.Round)
    drawCircle(Color(0xFFBFC6CE), 4.dp.toPx(), Offset(top.x, top.y - 3.dp.toPx()),
        style = Stroke(1.6.dp.toPx()))
    return top
}

private fun DrawScope.drawTestLead(clip: Offset, exit: Offset, body: Color, tiltDeg: Float) {
    // La manga sale hacia abajo girada tiltDeg; el cable arranca en su punta.
    val reach = 24.dp.toPx()
    val dir = Offset(-sin(tiltDeg.rad), cos(tiltDeg.rad))
    val start = clip + dir * reach
    val cable = Path().apply {
        moveTo(start.x, start.y)
        cubicTo(start.x + dir.x * reach * 2f, start.y + size.height * 0.08f,
            mix(exit.x, start.x, 0.35f), exit.y - size.height * 0.03f, exit.x, exit.y)
    }
    drawPath(cable, Color(0xFF070707), style = Stroke(7.dp.toPx(), cap = StrokeCap.Round))
    drawPath(cable, body, style = Stroke(5.dp.toPx(), cap = StrokeCap.Round))
    drawPath(cable, Color.White.copy(alpha = 0.22f), style = Stroke(1.4.dp.toPx(), cap = StrokeCap.Round))
    rotate(tiltDeg, clip) {
        val w = 11.dp.toPx()
        val l = 26.dp.toPx()
        // Mandibulas de la pinza cocodrilo y su capuchon.
        drawRoundRect(Color(0xFF9EA6AF), Offset(clip.x - w * 0.35f, clip.y - 4.dp.toPx()),
            Size(w * 0.7f, l * 0.45f), CornerRadius(2.dp.toPx()))
        drawLine(Color.Black.copy(alpha = 0.45f), Offset(clip.x, clip.y - 3.dp.toPx()),
            Offset(clip.x, clip.y + l * 0.2f), 1.dp.toPx())
        drawRoundRect(body, Offset(clip.x - w / 2f, clip.y + l * 0.25f), Size(w, l * 0.75f),
            CornerRadius(4.dp.toPx()))
        drawRoundRect(Color.White.copy(alpha = 0.18f), Offset(clip.x - w * 0.3f, clip.y + l * 0.3f),
            Size(w * 0.15f, l * 0.6f), CornerRadius(2.dp.toPx()))
    }
}

private fun DrawScope.drawContactShadow(st: EggStage, at: Offset) {
    val r = st.capW * 0.78f
    drawOval(
        Brush.radialGradient(listOf(Color.Black.copy(alpha = 0.6f), Color.Transparent), at, r),
        Offset(at.x - r, at.y - r * TILT * 1.3f), Size(2f * r, 2f * r * TILT * 1.3f),
    )
}

/** Electrolito derramado: charco oscuro y humedo que se agranda. */
private fun DrawScope.drawStain(st: EggStage, at: Offset, grow: Float, spread: Float) {
    if (grow <= 0f) return
    val rx = st.capW * (0.6f + 0.4f * grow) * spread
    val ry = rx * TILT
    drawOval(ELECTROLYTE.copy(alpha = 0.62f * grow), Offset(at.x - rx, at.y - ry), Size(2f * rx, 2f * ry))
    drawOval(Color.White.copy(alpha = 0.07f * grow), Offset(at.x - rx * 0.55f, at.y - ry * 0.75f),
        Size(rx * 0.8f, ry * 0.35f))
}

// --- C12 -------------------------------------------------------------------

/** Manga termocontraible: cilindro sombreado, franja de polaridad con "-" y rotulado. */
private fun DrawScope.drawSleeve(
    st: EggStage,
    m: TextMeasurer,
    cx: Float,
    topY: Float,
    bottomY: Float,
    burnt: Float,
) {
    val rx = st.capW / 2f
    val h = bottomY - topY
    drawRect(
        Brush.horizontalGradient(
            0f to Color(0xFF0A0C0F), 0.18f to Color(0xFF1E232A), 0.33f to Color(0xFF3B424C),
            0.52f to Color(0xFF1F242B), 1f to SLEEVE_DARK,
            startX = cx - rx, endX = cx + rx,
        ),
        Offset(cx - rx, topY), Size(2f * rx, h),
    )
    val stripeX = cx + rx * 0.30f
    val stripeW = rx * 0.42f
    drawRect(
        Brush.horizontalGradient(
            listOf(Color(0xFF545C67), Color(0xFF8F97A2), Color(0xFF59616C)),
            startX = stripeX, endX = stripeX + stripeW,
        ),
        Offset(stripeX, topY), Size(stripeW, h),
    )
    var y = topY + h * 0.14f
    while (y < bottomY - h * 0.08f) {
        drawLine(Color(0xFF262B32), Offset(stripeX + stripeW * 0.3f, y),
            Offset(stripeX + stripeW * 0.7f, y), 2.4.dp.toPx(), StrokeCap.Round)
        y += h * 0.13f
    }
    val textX = cx - rx * 0.30f
    drawCentered(m, "1000µF", textX, topY + h * 0.30f, st.capW * 0.14f,
        Color(0xFFD8C79A), FontWeight.SemiBold)
    drawCentered(m, "16V", textX, topY + h * 0.47f, st.capW * 0.19f,
        Color(0xFFFFB300), FontWeight.ExtraBold)
    drawCentered(m, "105°C", textX, topY + h * 0.63f, st.capW * 0.115f,
        Color(0xFFD8C79A), FontWeight.Medium)
    if (burnt > 0f) {
        drawRect(
            Brush.verticalGradient(
                listOf(Color(0xFF4A2A12).copy(alpha = 0.65f * burnt), Color.Transparent),
                topY, topY + h * 0.38f,
            ),
            Offset(cx - rx, topY), Size(2f * rx, h * 0.38f),
        )
    }
}

private fun DrawScope.drawCentered(
    m: TextMeasurer,
    text: String,
    cx: Float,
    cy: Float,
    px: Float,
    color: Color,
    weight: FontWeight,
) {
    val layout = m.measure(text, TextStyle(color = color, fontSize = px.toSp(), fontWeight = weight))
    drawText(layout, topLeft = Offset(cx - layout.size.width / 2f, cy - layout.size.height / 2f))
}

/** C12 en su lugar: la tapa se abomba ([dome]) o ya revento en petalos ([petals]). */
private fun DrawScope.drawStandingCap(
    st: EggStage,
    m: TextMeasurer,
    dome: Float,
    petals: Float,
    burnt: Float,
    dripAge: Float,
) {
    val cx = st.base.x
    val rx = st.capW / 2f
    val ry = rx * TILT
    val topY = st.vent.y
    drawOval(SLEEVE_DARK, Offset(cx - rx, st.base.y - ry), Size(2f * rx, 2f * ry))
    drawSleeve(st, m, cx, topY, st.base.y, burnt)
    if (dripAge > 0f) drawDrips(cx, topY + ry, rx, st.capH, dripAge)
    // La manga se dobla sobre el borde de la tapa de aluminio.
    drawOval(SLEEVE_RIM, Offset(cx - rx, topY - ry), Size(2f * rx, 2f * ry))
    if (petals > 0f) {
        drawOpenVent(cx, topY, rx * 0.86f, ry * 0.86f, petals, st.capW)
    } else {
        drawVentTop(cx, topY, rx * 0.86f, ry * 0.86f, dome, st.capW)
    }
}

/** Tapa de aluminio con el venteo ranurado en "Y"; al abombarse, las ranuras se abren. */
private fun DrawScope.drawVentTop(cx: Float, topY: Float, rxi: Float, ryi: Float, dome: Float, capW: Float) {
    val domeH = dome * capW * 0.11f
    val outline = Path().apply {
        arcTo(Rect(cx - rxi, topY - ryi - domeH, cx + rxi, topY + ryi + domeH), 180f, 180f, true)
        arcTo(Rect(cx - rxi, topY - ryi, cx + rxi, topY + ryi), 0f, 180f, false)
        close()
    }
    drawPath(
        outline,
        Brush.radialGradient(
            listOf(Color(0xFFF4F6F8), Color(0xFFB9C0C8), Color(0xFF7D858F)),
            Offset(cx - rxi * 0.3f, topY - domeH * 0.6f - ryi * 0.2f), rxi * 1.3f,
        ),
    )
    val apex = Offset(cx, topY - domeH * 0.55f)
    val score = lerp(Color(0xFF6B727B), Color(0xFF24282D), dome)
    val scoreW = (1.5f + 1.2f * dome).dp.toPx()
    listOf(90f, 210f, 330f).forEach { deg ->
        val rim = Offset(cx + rxi * 0.92f * cos(deg.rad), topY + ryi * 0.92f * sin(deg.rad))
        val mid = Offset((apex.x + rim.x) / 2f, (apex.y + rim.y) / 2f - domeH * 0.35f)
        drawPath(
            Path().apply { moveTo(apex.x, apex.y); quadraticBezierTo(mid.x, mid.y, rim.x, rim.y) },
            score, style = Stroke(scoreW, cap = StrokeCap.Round),
        )
    }
}

/** Venteo reventado: tres petalos de aluminio abiertos y el bobinado de papel a la vista. */
private fun DrawScope.drawOpenVent(cx: Float, topY: Float, rxi: Float, ryi: Float, open: Float, capW: Float) {
    drawOval(Color(0xFF140E0A), Offset(cx - rxi, topY - ryi), Size(2f * rxi, 2f * ryi))
    for (k in 1..3) {
        val f = k / 4f
        drawOval(PAPER.copy(alpha = 0.28f), Offset(cx - rxi * f, topY - ryi * f),
            Size(2f * rxi * f, 2f * ryi * f), style = Stroke(1.dp.toPx()))
    }
    drawPetal(cx, topY, rxi, ryi, 210f, 330f, open, capW)
    drawPetal(cx, topY, rxi, ryi, 330f, 450f, open, capW)
    drawPetal(cx, topY, rxi, ryi, 90f, 210f, open, capW)
}

private fun DrawScope.drawPetal(
    cx: Float,
    topY: Float,
    rxi: Float,
    ryi: Float,
    fromDeg: Float,
    toDeg: Float,
    open: Float,
    capW: Float,
) {
    fun rim(deg: Float) = Offset(cx + rxi * cos(deg.rad), topY + ryi * sin(deg.rad))
    val mid = (fromDeg + toDeg) / 2f
    val apex = Offset(
        cx + rxi * 0.70f * open * cos(mid.rad),
        topY + ryi * 0.70f * open * sin(mid.rad) - capW * 0.30f * open,
    )
    val petal = Path().apply {
        val a = rim(fromDeg)
        val b = rim(toDeg)
        moveTo(a.x, a.y)
        lineTo(apex.x, apex.y)
        lineTo(b.x, b.y)
        var d = toDeg
        while (d > fromDeg) {
            d = max(fromDeg, d - 15f)
            val p = rim(d)
            lineTo(p.x, p.y)
        }
        close()
    }
    drawPath(petal, Brush.linearGradient(listOf(Color(0xFFE9EDF1), Color(0xFF9AA2AB)), apex, rim(mid)))
    drawPath(petal, Color(0xFF4A5058), style = Stroke(1.dp.toPx()))
}

/** Electrolito que chorrea por la manga despues de reventar. */
private fun DrawScope.drawDrips(cx: Float, startY: Float, rx: Float, capH: Float, age: Float) {
    val grow = smoothstep(0.05f, 0.9f, age)
    listOf(Triple(-0.55f, 0.26f, 3.5f), Triple(0.05f, 0.42f, 4.5f), Triple(0.62f, 0.18f, 3f))
        .forEach { (fx, len, widthDp) ->
            val x = cx + rx * fx
            val end = startY + capH * len * grow
            val w = widthDp.dp.toPx()
            drawLine(ELECTROLYTE.copy(alpha = 0.9f), Offset(x, startY), Offset(x, end), w, StrokeCap.Round)
            drawCircle(ELECTROLYTE, w * 0.9f, Offset(x, end))
            drawLine(Color.White.copy(alpha = 0.18f), Offset(x - w * 0.2f, startY),
                Offset(x - w * 0.2f, end), 1.dp.toPx())
        }
}

/** La lata suelta (modo cohete): sin tapon abajo y con el bobinado asomando. */
private fun DrawScope.drawLooseCan(st: EggStage, m: TextMeasurer, center: Offset, angle: Float) {
    val rx = st.capW / 2f
    val ry = rx * TILT
    val top = center.y - st.capH / 2f
    val bottom = center.y + st.capH / 2f
    rotate(angle, center) {
        drawSleeve(st, m, center.x, top, bottom, burnt = 0.5f)
        drawOval(Color(0xFF120D0A), Offset(center.x - rx * 0.92f, bottom - ry * 0.92f),
            Size(1.84f * rx, 1.84f * ry))
        for (k in 1..3) {
            val f = 0.92f * k / 4f
            drawOval(PAPER.copy(alpha = 0.35f), Offset(center.x - rx * f, bottom - ry * f),
                Size(2f * rx * f, 2f * ry * f), style = Stroke(1.dp.toPx()))
        }
        drawOval(SLEEVE_RIM, Offset(center.x - rx, top - ry), Size(2f * rx, 2f * ry))
        drawVentTop(center.x, top, rx * 0.86f, ry * 0.86f, 0.4f, st.capW)
    }
}

/** Patas que quedaron dobladas en los pads y el tapon de goma tirado al lado. */
private fun DrawScope.drawLeftovers(st: EggStage) {
    val lead = Color(0xFFC3C9D1)
    val w = 2.dp.toPx()
    val a = st.at(0.46f, 0.42f)
    val b = st.at(0.54f, 0.42f)
    val h = st.capW * 0.35f
    drawPath(Path().apply {
        moveTo(a.x, a.y); quadraticBezierTo(a.x - h * 0.1f, a.y - h * 0.6f, a.x - h * 0.45f, a.y - h * 0.9f)
    }, lead, style = Stroke(w, cap = StrokeCap.Round))
    drawPath(Path().apply {
        moveTo(b.x, b.y); quadraticBezierTo(b.x + h * 0.05f, b.y - h * 0.7f, b.x + h * 0.3f, b.y - h)
    }, lead, style = Stroke(w, cap = StrokeCap.Round))
    val bung = st.at(0.40f, 0.50f)
    val r = st.capW * 0.16f
    drawOval(Color(0xFF2E3136), Offset(bung.x - r, bung.y - r * 0.55f), Size(2f * r, r * 1.1f))
    drawOval(Color(0xFF4A4E55), Offset(bung.x - r, bung.y - r * 0.85f), Size(2f * r, r * 0.8f))
}

// --- Las cuatro fallas -------------------------------------------------------

private fun DrawScope.drawVentFailure(st: EggStage, t: Float, age: Float, seeds: EggSeeds, m: TextMeasurer) {
    val popped = age >= 0f
    val dome = capDome(t)
    drawGlassDiode(st)
    if (popped) drawStain(st, st.base, smoothstep(0.1f, 1.0f, age), 0.95f)
    drawContactShadow(st, st.base)
    drawStandingCap(
        st, m, dome,
        petals = if (popped) 1f - (1f - min(1f, age / 0.08f)).let { it * it * it } else 0f,
        burnt = if (popped) smoothstep(0f, 0.6f, age) else 0f,
        dripAge = age,
    )
    drawResistor(st)
    drawTestLeads(st, EggScene.VENT)
    val lift = Offset(0f, -dome * st.capW * 0.06f)
    for (p in seeds.ventPuffs) drawPuff(p, st.vent + Offset(p.ox * st.capW, 0f) + lift, t - p.born, st.capW)
    if (popped) {
        for (b in seeds.ventBits) drawBit(b, st.vent, age, st)
        for (d in seeds.ventDrops) drawDrop(d, st.vent, age, st)
        drawSparks(seeds.sparks, st.vent, age, st.capW)
        drawFlash(st.vent, age, st.capW)
    }
}

/** R7: se carboniza, humea blanco y despues pardo, se pone al rojo, prende fuego y se parte. */
private fun DrawScope.drawResistorFailure(st: EggStage, t: Float, age: Float, seeds: EggSeeds, m: TextMeasurer) {
    val popped = age >= 0f
    val g = resistorGeom(st)
    val glow = if (popped) exp(-age / 0.6f) else smoothstep(2.0f, 3.1f, t)
    val flame = when {
        t < EggTimeline.FLAME -> 0f
        popped -> exp(-age / 0.12f)
        else -> smoothstep(EggTimeline.FLAME, EggTimeline.FLAME + 0.15f, t)
    }
    drawGlassDiode(st)
    drawContactShadow(st, st.base)
    drawStandingCap(st, m, 0f, petals = 0f, burnt = 0f, dripAge = -1f)
    val under = Offset(g.center.x, g.left.y)
    drawScorch(under, g.len * 0.75f, 0.6f * smoothstep(2.0f, 3.2f, t))
    drawGlowSpill(under, glow, g.len * 1.4f)
    val split = if (popped) 1f - (1f - min(1f, age / 0.06f)).let { it * it } else 0f
    drawResistor(st, smoothstep(1.0f, 2.9f, t), glow, split)
    drawTestLeads(st, EggScene.RESISTOR)
    for (p in seeds.resistorSmoke) {
        drawPuff(p, g.center + Offset(p.ox * st.capW, -g.d * 0.3f), t - p.born, st.capW)
    }
    drawFlame(Offset(g.center.x, g.y - g.d * 0.35f), st.capW * 0.5f, g.len * 0.55f, t, flame)
    if (popped) {
        drawSparks(seeds.embers, g.center, age, st.capW)
        drawFlash(g.center, age, st.capW * 0.3f)
    }
}

private fun DrawScope.drawRocketFailure(st: EggStage, t: Float, age: Float, seeds: EggSeeds, m: TextMeasurer) {
    val popped = age >= 0f
    drawGlassDiode(st)
    if (!popped) {
        drawContactShadow(st, st.base)
        drawStandingCap(st, m, capDome(t) * 0.5f, petals = 0f, burnt = 0f, dripAge = -1f)
        drawResistor(st)
        drawTestLeads(st, EggScene.ROCKET)
        // El tapon de goma cede: vapor por la junta de abajo.
        for (p in seeds.rocketPuffs) {
            if (p.from == PuffFrom.BASE && p.born < EggTimeline.POP) {
                drawPuff(p, st.base + Offset(p.ox * st.capW, 0f), t - p.born, st.capW)
            }
        }
        return
    }
    drawStain(st, st.base, smoothstep(0.05f, 0.8f, age), 1.25f)
    drawLeftovers(st)
    val flight = EggTimeline.LAND - EggTimeline.POP
    if (age > flight) {
        val pose = rocketPose(st, age)
        drawContactShadow(st, Offset(pose.center.x, pose.center.y + st.capW / 2f))
    }
    for (p in seeds.rocketPuffs) {
        val origin = when (p.from) {
            PuffFrom.TRAIL -> canBottom(st, p.born - EggTimeline.POP)
            else -> st.base + Offset(p.ox * st.capW, -st.capW * 0.05f)
        }
        drawPuff(p, origin, t - p.born, st.capW)
    }
    drawStreamer(st, age)
    val pose = rocketPose(st, age)
    drawLooseCan(st, m, pose.center, pose.angle)
    drawResistor(st)
    drawTestLeads(st, EggScene.ROCKET)
    for (b in seeds.rocketBits) drawBit(b, st.base, age, st)
    for (d in seeds.rocketDrops) drawDrop(d, st.base, age, st)
    drawSparks(seeds.sparks, st.base, age, st.capW)
    drawFlash(st.base, age, st.capW)
}

/** D3: el chip se pone incandescente, hierve el flux, el vidrio se raja y queda en corto. */
private fun DrawScope.drawDiodeFailure(st: EggStage, t: Float, age: Float, seeds: EggSeeds, m: TextMeasurer) {
    val popped = age >= 0f
    val g = diodeGeom(st)
    val glow = if (popped) exp(-age / 0.15f) else smoothstep(1.0f, 3.1f, t)
    val under = Offset(g.center.x, g.left.y)
    drawScorch(under, g.len * 0.6f, 0.45f * smoothstep(2.4f, 3.4f, t))
    drawGlowSpill(under, glow, g.len * 1.6f)
    drawGlassDiode(
        st, glow,
        cracked = if (popped) min(1f, age / 0.03f) else 0f,
        soot = if (popped) smoothstep(0f, 0.4f, age) else 0f,
    )
    for (p in seeds.diodeSmoke) {
        // El flux humea en los pads; el soplo de la grieta sale del cuerpo.
        val origin = if (p.from == PuffFrom.BASE) {
            Offset(g.center.x + p.ox * st.capW, g.left.y)
        } else {
            g.center + Offset(p.ox * st.capW, 0f)
        }
        drawPuff(p, origin, t - p.born, st.capW)
    }
    drawContactShadow(st, st.base)
    drawStandingCap(st, m, 0f, petals = 0f, burnt = 0f, dripAge = -1f)
    drawResistor(st)
    drawTestLeads(st, EggScene.DIODE)
    if (popped) {
        for (b in seeds.shards) drawBit(b, g.center, age, st)
        drawFlash(g.center, age, st.capW * 0.25f)
    }
}

private class Pose(val center: Offset, val angle: Float)

/**
 * Vuelo balistico de la lata: sale por arriba de la pantalla girando, vuelve
 * a caer acostada sobre la placa en LAND, rebota una vez y se desliza un poco.
 */
private fun rocketPose(st: EggStage, age: Float): Pose {
    val flight = EggTimeline.LAND - EggTimeline.POP
    val start = Offset(st.base.x, st.base.y - st.capH / 2f)
    val surface = st.at(0.74f, 0.52f)
    val land = Offset(surface.x, surface.y - st.capW / 2f)
    if (age <= flight) {
        val rise = start.y + st.capH * 1.3f
        val g = 8f * rise / (flight * flight)
        val v0 = (start.y - land.y + 0.5f * g * flight * flight) / flight
        return Pose(
            Offset(mix(start.x, land.x, age / flight), start.y - v0 * age + 0.5f * g * age * age),
            810f * (age / flight),
        )
    }
    val after = age - flight
    val hopLen = EggTimeline.BOUNCE - EggTimeline.LAND
    val hop = if (after < hopLen) {
        val k = after / hopLen
        st.capW * 0.22f * 4f * k * (1f - k)
    } else {
        0f
    }
    val slide = st.capW * 0.18f * (1f - exp(-after / 0.25f))
    return Pose(Offset(land.x + slide, land.y - hop), 90f)
}

private fun canBottom(st: EggStage, age: Float): Offset {
    val pose = rocketPose(st, age.coerceAtLeast(0f))
    val r = pose.angle.rad
    val half = st.capH / 2f
    return Offset(pose.center.x - half * sin(r), pose.center.y + half * cos(r))
}

/** Cinta de papel del bobinado que sale detras de la lata en vuelo. */
private fun DrawScope.drawStreamer(st: EggStage, age: Float) {
    val flight = EggTimeline.LAND - EggTimeline.POP
    val fadeOut = if (age > flight) exp(-(age - flight) / 0.25f) else 1f
    if (fadeOut < 0.02f) return
    val n = 10
    var prev: Offset? = null
    for (i in 0..n) {
        val a = age - i * 0.022f
        if (a < 0f) break
        val bottom = canBottom(st, min(a, flight))
        val p = Offset(bottom.x + st.capW * 0.12f * sin(a * 22f + i * 0.9f), bottom.y)
        prev?.let {
            val k = i / n.toFloat()
            drawLine(PAPER.copy(alpha = (1f - k) * 0.9f * fadeOut), it, p,
                5.dp.toPx() * (1f - k * 0.5f), StrokeCap.Round)
        }
        prev = p
    }
}

// --- Particulas (todas analiticas: posicion = f(semilla, edad)) -------------

private class EggStar(val x: Float, val y: Float, val phase: Float, val radiusDp: Float)

/** De donde sale cada bocanada: tapa o cuerpo, pie o pads, o la estela de la lata. */
private enum class PuffFrom { VENT, BASE, TRAIL, PART }

/** Bocanada: velocidades en anchos de lata por segundo, radios en anchos de lata. */
private class Puff(
    val from: PuffFrom,
    val ox: Float,
    val born: Float,
    val vx: Float,
    val vy: Float,
    val r0: Float,
    val grow: Float,
    val life: Float,
    val alpha: Float,
    val phase: Float,
    /** 0 = vapor blanco; 1 = humo pardo de algo que se quema. */
    val shade: Float,
)

/** Trocito que vuela: frena en el aire, flamea y cae sobre la placa ([fall] = caida final). */
private class Bit(
    val ox: Float,
    val vx: Float,
    val vy: Float,
    val spin: Float,
    val flip: Float,
    val phase: Float,
    val w: Float,
    val h: Float,
    val foil: Boolean,
    val flutter: Float,
    val landV: Float,
    val fall: Float = 0.55f,
    val drag: Float = 0.3f,
)

private class Drop(val ox: Float, val vx: Float, val vy: Float, val sizeDp: Float, val landV: Float)

private class Spark(val angle: Float, val speed: Float, val life: Float, val sizeDp: Float)

private class EggSeeds {
    val stars: List<EggStar>
    val ventPuffs: List<Puff>
    val rocketPuffs: List<Puff>
    val ventBits: List<Bit>
    val rocketBits: List<Bit>
    val ventDrops: List<Drop>
    val rocketDrops: List<Drop>
    val sparks: List<Spark>
    val resistorSmoke: List<Puff>
    val embers: List<Spark>
    val diodeSmoke: List<Puff>
    val shards: List<Bit>

    init {
        // Starfield deterministico con la semilla de la familia MT.
        val sky = Random(11)
        stars = List(60) {
            EggStar(sky.nextFloat(), sky.nextFloat(), sky.nextFloat() * 6.2832f,
                0.8f + sky.nextFloat() * 1.3f)
        }
        val rng = Random(20261028)
        fun r(a: Float, b: Float) = a + (b - a) * rng.nextFloat()
        fun smoky(chance: Float) = if (rng.nextFloat() < chance) 0.6f else 0f
        val pop = EggTimeline.POP
        val wispFrom = EggTimeline.BOIL_START + 0.5f
        ventPuffs = buildList {
            // Hilos de vapor por las ranuras del venteo antes de reventar.
            repeat(16) { i ->
                add(Puff(PuffFrom.VENT, r(-0.3f, 0.3f), wispFrom + i * (pop - wispFrom) / 16f,
                    r(-0.25f, 0.25f), r(-1.1f, -0.7f), 0.035f, 0.28f, 1.0f,
                    0.10f + 0.12f * i / 16f, r(0f, 6.28f), 0f))
            }
            // Chorro del estallido.
            repeat(24) {
                add(Puff(PuffFrom.VENT, r(-0.15f, 0.15f), pop + r(0f, 0.12f), r(-0.9f, 0.9f),
                    r(-7f, -4f), 0.07f, 0.85f, r(1.4f, 2.2f), 0.16f, r(0f, 6.28f), 0f))
            }
            // Nube que queda flotando.
            repeat(20) {
                add(Puff(PuffFrom.VENT, r(-0.3f, 0.3f), pop + r(0.03f, 0.5f), r(-1.4f, 1.4f),
                    r(-2.4f, -0.8f), 0.10f, 0.7f, r(1.8f, 2.5f), 0.12f, r(0f, 6.28f), smoky(0.3f)))
            }
        }
        rocketPuffs = buildList {
            repeat(12) { i ->
                add(Puff(PuffFrom.BASE, r(-0.55f, 0.55f), wispFrom + i * (pop - wispFrom) / 12f,
                    r(-0.4f, 0.4f), r(-0.6f, -0.3f), 0.035f, 0.30f, 1.0f,
                    0.10f + 0.10f * i / 12f, r(0f, 6.28f), 0f))
            }
            repeat(18) {
                add(Puff(PuffFrom.BASE, r(-0.4f, 0.4f), pop + r(0f, 0.2f), r(-1.8f, 1.8f),
                    r(-3f, -1f), 0.10f, 0.8f, r(1.8f, 2.4f), 0.15f, r(0f, 6.28f), smoky(0.35f)))
            }
            // Estela de la lata que vuela.
            repeat(18) { i ->
                add(Puff(PuffFrom.TRAIL, 0f, pop + i * 0.035f, r(-0.2f, 0.2f), r(-0.3f, 0.3f),
                    0.05f, 0.5f, 1.4f, 0.14f, r(0f, 6.28f), 0f))
            }
        }
        fun bits(count: Int, spread: Float, up: Float, down: Float) = List(count) {
            Bit(r(-spread, spread), r(-2.4f, 2.4f), r(up, down), r(-540f, 540f), r(4f, 9f),
                r(0f, 6.28f), r(0.05f, 0.10f), r(0.025f, 0.05f), rng.nextFloat() < 0.4f,
                r(0.12f, 0.3f), r(0.05f, 0.95f))
        }
        ventBits = bits(20, 0.3f, -7f, -3.5f)
        rocketBits = bits(14, 0.4f, -4f, -1.5f)
        ventDrops = List(10) {
            Drop(r(-0.2f, 0.2f), r(-1.5f, 1.5f), r(-5f, -2.5f), r(2f, 3.5f), r(0.1f, 0.9f))
        }
        rocketDrops = List(8) {
            // Caen delante del pie (sale de la placa): nunca por encima de donde arrancan.
            Drop(r(-0.4f, 0.4f), r(-2.2f, 2.2f), r(-3f, -1f), r(2f, 3.2f), r(0.45f, 0.95f))
        }
        sparks = List(6) { Spark(r(-2.4f, -0.7f), r(2.5f, 5f), r(0.12f, 0.26f), r(1.4f, 2.4f)) }
        // R7: humo blanco al principio y pardo al carbonizarse; bocanada al partirse.
        resistorSmoke = buildList {
            repeat(36) { i ->
                val born = 1.2f + 3.6f * i / 36f + r(0f, 0.05f)
                val burning = smoothstep(1.8f, 2.6f, born) * (1f - 0.5f * smoothstep(pop, pop + 1.2f, born))
                add(Puff(PuffFrom.PART, r(-0.14f, 0.14f), born, r(-0.15f, 0.15f), r(-0.9f, -0.5f),
                    0.02f, 0.22f, r(1.6f, 2.4f), 0.08f + 0.12f * burning, r(0f, 6.28f), 0.75f * burning))
            }
            repeat(8) {
                add(Puff(PuffFrom.PART, r(-0.08f, 0.08f), pop + r(0f, 0.1f), r(-0.5f, 0.5f),
                    r(-1.6f, -0.8f), 0.03f, 0.3f, r(1.2f, 1.8f), 0.18f, r(0f, 6.28f), 0.45f))
            }
        }
        embers = List(8) { Spark(r(-2.7f, -0.45f), r(0.8f, 2.0f), r(0.4f, 0.8f), r(1.2f, 2.0f)) }
        // D3: hierve el flux de sus dos soldaduras y sale un soplo por la grieta del vidrio.
        diodeSmoke = buildList {
            repeat(20) { i ->
                val side = if (i % 2 == 0) -0.29f else 0.29f
                add(Puff(PuffFrom.BASE, side + r(-0.03f, 0.03f), 1.6f + 1.6f * i / 20f + r(0f, 0.05f),
                    r(-0.1f, 0.1f), r(-0.6f, -0.35f), 0.015f, 0.16f, r(1.2f, 1.8f),
                    r(0.07f, 0.12f), r(0f, 6.28f), 0.15f))
            }
            repeat(8) {
                add(Puff(PuffFrom.PART, r(-0.05f, 0.05f), pop + r(0f, 0.08f), r(-0.6f, 0.6f),
                    r(-1.4f, -0.6f), 0.02f, 0.25f, r(1.0f, 1.6f), 0.16f, r(0f, 6.28f), 0.35f))
            }
        }
        shards = List(6) {
            Bit(r(-0.05f, 0.05f), r(-1.5f, 1.5f), r(-2.5f, -1f), r(-720f, 720f), r(6f, 12f),
                r(0f, 6.28f), r(0.015f, 0.03f), r(0.01f, 0.02f), true, r(0f, 0.03f),
                r(0.12f, 0.40f), fall = 2.5f, drag = 1.0f)
        }
    }
}

/** Vapor o humo: frena al salir, sube por flotacion, deriva con el aire y se expande. */
private fun DrawScope.drawPuff(p: Puff, origin: Offset, age: Float, capW: Float) {
    if (age < 0f || age > p.life) return
    val drag = 0.22f
    val k = drag * (1f - exp(-age / drag))
    val x = origin.x + (p.vx * k + 0.25f * age + 0.28f * sin(2.1f * age + p.phase) * age) * capW
    val y = origin.y + (p.vy * k - 0.55f * age + 0.08f * sin(1.7f * age + p.phase) * age) * capW
    val radius = (p.r0 + p.grow * 0.8f * (1f - exp(-age / 0.8f)) + 0.15f * age) * capW
    val alpha = p.alpha * smoothstep(0f, 0.07f, age) * (1f - smoothstep(p.life * 0.35f, p.life, age))
    if (alpha < 0.004f) return
    val c = lerp(Color(0xFFDADDE1), Color(0xFF5A4F48), p.shade)
    val center = Offset(x, y)
    drawCircle(
        Brush.radialGradient(
            listOf(c.copy(alpha = alpha), c.copy(alpha = alpha * 0.45f), Color.Transparent),
            center, radius,
        ),
        radius, center,
    )
}

private fun DrawScope.drawBit(b: Bit, origin: Offset, age: Float, st: EggStage) {
    if (age <= 0f) return
    val capW = st.capW
    val o = Offset(origin.x + b.ox * capW, origin.y)
    val landY = mix(st.farY, st.nearY, b.landV)
    fun dy(a: Float) = ((b.vy - b.fall) * b.drag * (1f - exp(-a / b.drag)) + b.fall * a) * capW
    fun dx(a: Float) = (b.vx * b.drag * (1f - exp(-a / b.drag)) +
        b.flutter * sin(5.5f * a + b.phase) * min(1f, a / 0.5f)) * capW
    var a = age
    var landed = false
    if (o.y + dy(age) >= landY) {
        // Momento en que toco la placa (despues de subir), por biseccion.
        var lo = min(age, b.drag * ln((b.fall - b.vy) / b.fall))
        var hi = age
        repeat(18) {
            val mid = (lo + hi) / 2f
            if (o.y + dy(mid) >= landY) hi = mid else lo = mid
        }
        a = hi
        landed = true
    }
    val x = o.x + dx(a)
    val y = o.y + dy(a)
    if (landed && !st.onBoard(x, b.landV)) return
    val angle = b.phase * 57f + b.spin * a
    val flip = if (landed) 1f else 0.25f + 0.75f * abs(cos(b.flip * a + b.phase))
    val w = b.w * capW * flip
    val h = b.h * capW * (if (landed) TILT * 1.5f else 1f)
    val color = if (b.foil) {
        lerp(Color(0xFF7F868E), Color(0xFFF4F7FA), abs(cos(angle * 0.035f)))
    } else {
        PAPER
    }
    rotate(angle, Offset(x, y)) {
        drawRect(color, Offset(x - w / 2f, y - h / 2f), Size(w, h))
    }
}

/** Gota de electrolito: balistica; al caer deja su manchita sobre la placa. */
private fun DrawScope.drawDrop(d: Drop, origin: Offset, age: Float, st: EggStage) {
    if (age <= 0f) return
    val capW = st.capW
    val o = Offset(origin.x + d.ox * capW, origin.y)
    val g = 9f
    val delta = (mix(st.farY, st.nearY, d.landV) - o.y) / capW
    val landAt = (-d.vy + sqrt(d.vy * d.vy + 2f * g * delta)) / g
    val a = min(age, landAt)
    val x = o.x + d.vx * a * capW
    val y = o.y + (d.vy * a + 0.5f * g * a * a) * capW
    val r = d.sizeDp.dp.toPx()
    if (age < landAt) {
        drawCircle(Color(0xFF6B4A1E).copy(alpha = 0.85f), r, Offset(x, y))
    } else if (st.onBoard(x, d.landV)) {
        drawOval(ELECTROLYTE.copy(alpha = 0.6f), Offset(x - r * 1.6f, y - r * 1.6f * TILT),
            Size(r * 3.2f, r * 3.2f * TILT))
    }
}

/** Chispas cortas y pocas: los componentes fallan con humo, no con fuegos artificiales. */
private fun DrawScope.drawSparks(sparks: List<Spark>, at: Offset, age: Float, capW: Float) {
    if (age < 0f) return
    for (s in sparks) {
        if (age > s.life) continue
        val f = 1f - age / s.life
        val x = at.x + cos(s.angle) * s.speed * age * capW
        val y = at.y + sin(s.angle) * s.speed * age * capW + 4.5f * age * age * capW
        val c = when {
            f > 0.6f -> Color(0xFFFFF6D0)
            f > 0.3f -> Color(0xFFFFC14D)
            else -> Color(0xFFE0612A)
        }
        drawCircle(c.copy(alpha = min(1f, f * 1.5f)), s.sizeDp.dp.toPx() * (0.5f + f * 0.5f), Offset(x, y))
    }
}

/** Destello breve y localizado en el punto de la falla. */
private fun DrawScope.drawFlash(at: Offset, age: Float, size: Float) {
    if (age < 0f || age > 0.25f) return
    val a = 0.9f * exp(-age / 0.045f)
    val r = size * (1f + age * 4f)
    drawCircle(
        Brush.radialGradient(
            listOf(Color(0xFFFFFBEA).copy(alpha = a), Color(0xFFFFC266).copy(alpha = a * 0.45f),
                Color.Transparent),
            at, r,
        ),
        r, at,
    )
}

private fun mix(a: Float, b: Float, t: Float) = a + (b - a) * t

private val Float.rad: Float get() = this * (PI.toFloat() / 180f)
