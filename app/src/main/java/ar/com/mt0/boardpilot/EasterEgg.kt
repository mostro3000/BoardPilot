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
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
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
 * Aca: C12, un electrolitico de 16 V en una fuente de banco a 24 V. Falla
 * como en el banco de verdad: la fuga crece, la fuente entra en limite de
 * corriente, la tapa se abomba, el electrolito hierve (siseo y crepitar) y
 * revienta el venteo con vapor y papel/aluminio del bobinado; en el ciclo
 * siguiente salta el tapon de goma y la lata sale volando como un cohete.
 * Sonido sintetizado en el momento (EggSoundtrack) y vibracion al reventar.
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

    // El sonido se pausa con la app en segundo plano y se libera al salir.
    DisposableEffect(lifecycleOwner) {
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
    val measurer = rememberTextMeasurer(cacheSize = 24)
    val loopT = timeSec % EggTimeline.LOOP
    val rocket = loopT >= EggTimeline.CYCLE
    val t = if (rocket) loopT - EggTimeline.CYCLE else loopT

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
        Canvas(Modifier.fillMaxSize()) { drawEggScene(t, rocket, timeSec, seeds, measurer) }
        Column(
            Modifier.align(Alignment.TopCenter).padding(top = 56.dp, start = 20.dp, end = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(strings.eggHeader, color = Color(0xFFFFB300),
                fontFamily = FontFamily.Monospace, fontSize = 14.sp,
                fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            Text(
                if (t < EggTimeline.POP) strings.eggCharging else strings.eggAftermath,
                color = Color(0xFFF5E9E2), fontFamily = FontFamily.Monospace,
                fontSize = 13.sp, textAlign = TextAlign.Center,
            )
            PsuPanel(strings, psuReading(t))
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
            append(String.format(Locale.US, "%4.1f V  %4.2f A", psu.volts, psu.amps))
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

private fun eggHaptics(view: View, from: Float, to: Float) {
    fun crossed(at: Float) = if (from <= to) at > from && at <= to else at > from || at <= to
    when {
        crossed(EggTimeline.POP) || crossed(EggTimeline.CYCLE + EggTimeline.POP) ->
            view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        crossed(EggTimeline.CYCLE + EggTimeline.LAND) ->
            view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
    }
}

/**
 * Banda sonora en loop por AudioTrack (volumen multimedia). Ademas es el
 * reloj maestro de la animacion: segundos ya escuchados, latencia de salida
 * incluida, para que el POP suene justo cuando se ve.
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
    t: Float,
    rocket: Boolean,
    timeSec: Float,
    seeds: EggSeeds,
    m: TextMeasurer,
) {
    val st = EggStage(size.width, size.height)
    val age = t - EggTimeline.POP
    drawBackdrop(st, timeSec, seeds.stars)
    val shake = shakeOffset(t, age, rocket)
    translate(shake.x, shake.y) {
        drawBoard(st, m)
        if (rocket) drawRocketFailure(st, t, age, seeds, m) else drawVentFailure(st, t, age, seeds, m)
    }
    // Fundido al terminar cada ciclo y al empezar el siguiente: reinicia sin saltos.
    val fade = max(
        1f - smoothstep(0f, EggTimeline.FADE, t),
        smoothstep(EggTimeline.CYCLE - EggTimeline.FADE, EggTimeline.CYCLE, t),
    )
    if (fade > 0.001f) drawRect(Color(0xFF050203).copy(alpha = fade))
}

/** Sacudon del estallido (y uno chico cuando la lata cae). */
private fun DrawScope.shakeOffset(t: Float, age: Float, rocket: Boolean): Offset {
    var amp = 0f
    var a = 0f
    if (age in 0f..0.5f) {
        amp = (if (rocket) 9f else 7f) * exp(-age / 0.1f)
        a = age
    }
    val land = t - EggTimeline.LAND
    if (rocket && land in 0f..0.3f) {
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
    drawSilk(m, "D3", st.at(0.80f, 0.07f), st)
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

/** Componente axial acostado sobre la placa (resistencia, diodo) con sus patas. */
private fun DrawScope.drawAxial(
    st: EggStage,
    left: Offset,
    right: Offset,
    body: List<Color>,
    bands: List<Pair<Float, Color>>,
) {
    val d = st.capW * 0.15f
    val y = (left.y + right.y) / 2f - d * 0.75f
    val bodyL = mix(left.x, right.x, 0.18f)
    val bodyR = mix(left.x, right.x, 0.82f)
    val lead = Color(0xFFB9C0C8)
    val leadW = 1.6.dp.toPx()
    drawLine(lead, left, Offset(left.x, y), leadW)
    drawLine(lead, Offset(left.x, y), Offset(bodyL, y), leadW)
    drawLine(lead, right, Offset(right.x, y), leadW)
    drawLine(lead, Offset(right.x, y), Offset(bodyR, y), leadW)
    drawOval(Color.Black.copy(alpha = 0.35f), Offset(bodyL, left.y - d * 0.25f),
        Size(bodyR - bodyL, d * 0.5f))
    drawRoundRect(Brush.verticalGradient(body, y - d / 2f, y + d / 2f),
        Offset(bodyL, y - d / 2f), Size(bodyR - bodyL, d), CornerRadius(d * 0.45f))
    bands.forEach { (f, c) ->
        drawRect(c, Offset(mix(bodyL, bodyR, f), y - d / 2f + 1f),
            Size((bodyR - bodyL) * 0.07f, d - 2f))
    }
    drawLine(Color.White.copy(alpha = 0.25f), Offset(bodyL + d * 0.3f, y - d * 0.25f),
        Offset(bodyR - d * 0.3f, y - d * 0.25f), 1.5.dp.toPx(), StrokeCap.Round)
}

private fun DrawScope.drawResistor(st: EggStage) = drawAxial(
    st, st.at(0.13f, 0.62f), st.at(0.27f, 0.62f),
    listOf(Color(0xFFEBD9AE), Color(0xFFC8AE7C), Color(0xFF8C744E)),
    listOf(0.20f to Color(0xFF6B3A1F), 0.34f to Color(0xFF151515),
        0.48f to Color(0xFFB3261E), 0.76f to Color(0xFFC9A227)),
)

private fun DrawScope.drawDiode(st: EggStage) = drawAxial(
    st, st.at(0.73f, 0.22f), st.at(0.87f, 0.22f),
    listOf(Color(0xFF3A3D42), Color(0xFF1B1D20), Color(0xFF0B0C0E)),
    listOf(0.76f to Color(0xFFC3C9D0)),
)

/** Puntos de prueba con las pinzas de la fuente: roja al +, negra al -. */
private fun DrawScope.drawTestLeads(st: EggStage) {
    val plus = drawTestPin(st, st.at(0.36f, 0.80f))
    val minus = drawTestPin(st, st.at(0.64f, 0.80f))
    drawTestLead(plus, Offset(-0.08f * st.w, st.h * 0.88f), Color(0xFFC62828), -18f)
    drawTestLead(minus, Offset(1.08f * st.w, st.h * 0.88f), Color(0xFF1E1E1E), 18f)
}

private fun DrawScope.drawTestPin(st: EggStage, base: Offset): Offset {
    val top = Offset(base.x, base.y - st.capW * 0.24f)
    drawLine(Color(0xFFBFC6CE), base, top, 2.dp.toPx(), StrokeCap.Round)
    drawCircle(Color(0xFFBFC6CE), 4.dp.toPx(), Offset(top.x, top.y - 3.dp.toPx()),
        style = Stroke(1.6.dp.toPx()))
    return top
}

private fun DrawScope.drawTestLead(clip: Offset, exit: Offset, body: Color, tiltDeg: Float) {
    val start = Offset(clip.x, clip.y + 22.dp.toPx())
    val cable = Path().apply {
        moveTo(start.x, start.y)
        cubicTo(start.x, start.y + size.height * 0.08f,
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

// --- Las dos fallas ---------------------------------------------------------

private fun DrawScope.drawVentFailure(st: EggStage, t: Float, age: Float, seeds: EggSeeds, m: TextMeasurer) {
    val popped = age >= 0f
    val dome = capDome(t)
    drawDiode(st)
    if (popped) drawStain(st, st.base, smoothstep(0.1f, 1.0f, age), 0.95f)
    drawContactShadow(st, st.base)
    drawStandingCap(
        st, m, dome,
        petals = if (popped) 1f - (1f - min(1f, age / 0.08f)).let { it * it * it } else 0f,
        burnt = if (popped) smoothstep(0f, 0.6f, age) else 0f,
        dripAge = age,
    )
    drawResistor(st)
    drawTestLeads(st)
    val lift = Offset(0f, -dome * st.capW * 0.06f)
    for (p in seeds.ventPuffs) drawPuff(p, st.vent + Offset(p.ox * st.capW, 0f) + lift, t - p.born, st.capW)
    if (popped) {
        for (b in seeds.ventBits) drawBit(b, st.vent, age, st)
        for (d in seeds.ventDrops) drawDrop(d, st.vent, age, st)
        drawSparks(seeds.sparks, st.vent, age, st.capW)
        drawFlash(st.vent, age, st.capW)
    }
}

private fun DrawScope.drawRocketFailure(st: EggStage, t: Float, age: Float, seeds: EggSeeds, m: TextMeasurer) {
    val popped = age >= 0f
    drawDiode(st)
    if (!popped) {
        drawContactShadow(st, st.base)
        drawStandingCap(st, m, capDome(t) * 0.5f, petals = 0f, burnt = 0f, dripAge = -1f)
        drawResistor(st)
        drawTestLeads(st)
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
    drawTestLeads(st)
    for (b in seeds.rocketBits) drawBit(b, st.base, age, st)
    for (d in seeds.rocketDrops) drawDrop(d, st.base, age, st)
    drawSparks(seeds.sparks, st.base, age, st.capW)
    drawFlash(st.base, age, st.capW)
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

private enum class PuffFrom { VENT, BASE, TRAIL }

/** Bocanada de vapor: velocidades en anchos de lata por segundo, radios en anchos de lata. */
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
    val dark: Boolean,
)

/** Trocito de papel o aluminio del bobinado: frena en el aire, flamea y cae sobre la placa. */
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

    init {
        // Starfield deterministico con la semilla de la familia MT.
        val sky = Random(11)
        stars = List(60) {
            EggStar(sky.nextFloat(), sky.nextFloat(), sky.nextFloat() * 6.2832f,
                0.8f + sky.nextFloat() * 1.3f)
        }
        val rng = Random(20261028)
        fun r(a: Float, b: Float) = a + (b - a) * rng.nextFloat()
        val pop = EggTimeline.POP
        val wispFrom = EggTimeline.BOIL_START + 0.5f
        ventPuffs = buildList {
            // Hilos de vapor por las ranuras del venteo antes de reventar.
            repeat(16) { i ->
                add(Puff(PuffFrom.VENT, r(-0.3f, 0.3f), wispFrom + i * (pop - wispFrom) / 16f,
                    r(-0.25f, 0.25f), r(-1.1f, -0.7f), 0.035f, 0.28f, 1.0f,
                    0.10f + 0.12f * i / 16f, r(0f, 6.28f), false))
            }
            // Chorro del estallido.
            repeat(24) {
                add(Puff(PuffFrom.VENT, r(-0.15f, 0.15f), pop + r(0f, 0.12f), r(-0.9f, 0.9f),
                    r(-7f, -4f), 0.07f, 0.85f, r(1.4f, 2.2f), 0.16f, r(0f, 6.28f), false))
            }
            // Nube que queda flotando.
            repeat(20) {
                add(Puff(PuffFrom.VENT, r(-0.3f, 0.3f), pop + r(0.03f, 0.5f), r(-1.4f, 1.4f),
                    r(-2.4f, -0.8f), 0.10f, 0.7f, r(1.8f, 2.5f), 0.12f, r(0f, 6.28f),
                    rng.nextFloat() < 0.3f))
            }
        }
        rocketPuffs = buildList {
            repeat(12) { i ->
                add(Puff(PuffFrom.BASE, r(-0.55f, 0.55f), wispFrom + i * (pop - wispFrom) / 12f,
                    r(-0.4f, 0.4f), r(-0.6f, -0.3f), 0.035f, 0.30f, 1.0f,
                    0.10f + 0.10f * i / 12f, r(0f, 6.28f), false))
            }
            repeat(18) {
                add(Puff(PuffFrom.BASE, r(-0.4f, 0.4f), pop + r(0f, 0.2f), r(-1.8f, 1.8f),
                    r(-3f, -1f), 0.10f, 0.8f, r(1.8f, 2.4f), 0.15f, r(0f, 6.28f),
                    rng.nextFloat() < 0.35f))
            }
            // Estela de la lata que vuela.
            repeat(18) { i ->
                add(Puff(PuffFrom.TRAIL, 0f, pop + i * 0.035f, r(-0.2f, 0.2f), r(-0.3f, 0.3f),
                    0.05f, 0.5f, 1.4f, 0.14f, r(0f, 6.28f), false))
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
    }
}

/** Vapor: frena al salir, sube por flotacion, deriva con el aire y se expande al disiparse. */
private fun DrawScope.drawPuff(p: Puff, origin: Offset, age: Float, capW: Float) {
    if (age < 0f || age > p.life) return
    val drag = 0.22f
    val k = drag * (1f - exp(-age / drag))
    val x = origin.x + (p.vx * k + 0.25f * age + 0.28f * sin(2.1f * age + p.phase) * age) * capW
    val y = origin.y + (p.vy * k - 0.55f * age + 0.08f * sin(1.7f * age + p.phase) * age) * capW
    val radius = (p.r0 + p.grow * 0.8f * (1f - exp(-age / 0.8f)) + 0.15f * age) * capW
    val alpha = p.alpha * smoothstep(0f, 0.07f, age) * (1f - smoothstep(p.life * 0.35f, p.life, age))
    if (alpha < 0.004f) return
    val c = if (p.dark) Color(0xFF8E949B) else Color(0xFFDADDE1)
    val center = Offset(x, y)
    drawCircle(
        Brush.radialGradient(
            listOf(c.copy(alpha = alpha), c.copy(alpha = alpha * 0.45f), Color.Transparent),
            center, radius,
        ),
        radius, center,
    )
}

private const val BIT_DRAG = 0.3f
private const val BIT_FALL = 0.55f

private fun DrawScope.drawBit(b: Bit, origin: Offset, age: Float, st: EggStage) {
    if (age <= 0f) return
    val capW = st.capW
    val o = Offset(origin.x + b.ox * capW, origin.y)
    val landY = mix(st.farY, st.nearY, b.landV)
    fun dy(a: Float) = ((b.vy - BIT_FALL) * BIT_DRAG * (1f - exp(-a / BIT_DRAG)) + BIT_FALL * a) * capW
    fun dx(a: Float) = (b.vx * BIT_DRAG * (1f - exp(-a / BIT_DRAG)) +
        b.flutter * sin(5.5f * a + b.phase) * min(1f, a / 0.5f)) * capW
    var a = age
    var landed = false
    if (o.y + dy(age) >= landY) {
        // Momento en que toco la placa (despues de subir), por biseccion.
        var lo = min(age, BIT_DRAG * ln((BIT_FALL - b.vy) / BIT_FALL))
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

/** Pocas chispas y muy cortas: un electrolitico revienta con vapor, no con fuegos artificiales. */
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

/** Destello breve y localizado en el punto del estallido. */
private fun DrawScope.drawFlash(at: Offset, age: Float, capW: Float) {
    if (age < 0f || age > 0.25f) return
    val a = 0.9f * exp(-age / 0.045f)
    val r = capW * (1f + age * 4f)
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
