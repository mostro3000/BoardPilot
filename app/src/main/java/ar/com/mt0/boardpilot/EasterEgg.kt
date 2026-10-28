package ar.com.mt0.boardpilot

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ar.com.mt0.boardpilot.i18n.AppStrings
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/**
 * Huevo de pascua de la familia MT: cada app anima el objeto de su dominio
 * sobre fondo negro con starfield y firma "h4ck3.ar" en latido lub-dub.
 * Aca el objeto es un capacitor electrolitico alimentado con 24 V siendo de
 * 16 V: se hincha, tiembla, echa humo... y revienta en chispas, una y otra vez.
 * Se abre con 6 toques sobre el titulo "BoardPilot"; tocar la pantalla cierra.
 */

private const val EGG_CYCLE_SEC = 5.4f
private const val EGG_BLAST_AT_SEC = 3.0f
private const val EGG_SIGNATURE = "h4ck3.ar"

private data class EggStar(val x: Float, val y: Float, val phase: Float, val radiusDp: Float)
private data class SparkSeed(
    val angleRad: Float,
    val speedDp: Float,
    val lifeSec: Float,
    val sizeDp: Float,
)

@Composable
fun EasterEggScreen(strings: AppStrings, onExit: () -> Unit) {
    var timeSec by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        val start = withFrameNanos { it }
        while (true) {
            withFrameNanos { timeSec = (it - start) / 1_000_000_000f }
        }
    }
    // Starfield deterministico con la semilla de la familia MT.
    val stars = remember {
        val rng = Random(11)
        List(60) {
            EggStar(rng.nextFloat(), rng.nextFloat(), rng.nextFloat() * 6.2832f,
                0.8f + rng.nextFloat() * 1.3f)
        }
    }
    val sparks = remember {
        val rng = Random(20261027)
        List(44) {
            SparkSeed(
                angleRad = -1.9f + rng.nextFloat() * 2.2f,
                speedDp = 120f + rng.nextFloat() * 340f,
                lifeSec = 0.35f + rng.nextFloat() * 0.75f,
                sizeDp = 1.5f + rng.nextFloat() * 2.6f,
            )
        }
    }

    val cycleT = timeSec % EGG_CYCLE_SEC
    val charging = cycleT < EGG_BLAST_AT_SEC
    val volts = if (charging) ((cycleT / EGG_BLAST_AT_SEC) * 24f).toInt() else 24

    val phase = (timeSec % 1.2f) / 1.2f
    val lub = exp(-((phase - 0.10f) * (phase - 0.10f)) / 0.004f)
    val dub = 0.6f * exp(-((phase - 0.34f) * (phase - 0.34f)) / 0.005f)
    val pulse = (lub + dub).coerceIn(0f, 1f)
    val beatScale = 1f + 0.16f * pulse

    Box(
        Modifier.fillMaxSize()
            .background(Color(0xFF050203))
            .clickable { onExit() },
    ) {
        Canvas(Modifier.fillMaxSize()) { drawEggScene(timeSec, stars, sparks) }
        Column(
            Modifier.align(Alignment.TopCenter).padding(top = 64.dp, start = 20.dp, end = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(strings.eggHeader, color = Color(0xFFFFB300),
                fontFamily = FontFamily.Monospace, fontSize = 14.sp,
                fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            Text(
                if (charging) "$volts V / 16 V MAX — ${strings.eggCharging}"
                else strings.eggAftermath,
                color = Color(0xFFF5E9E2), fontFamily = FontFamily.Monospace,
                fontSize = 13.sp, textAlign = TextAlign.Center,
            )
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

private fun DrawScope.drawEggScene(timeSec: Float, stars: List<EggStar>, sparks: List<SparkSeed>) {
    val w = size.width
    val h = size.height
    val cx = w / 2f
    val boardY = h * 0.60f
    val minSide = min(w, h)
    val capW = minSide * 0.22f
    val capH = minSide * 0.34f
    val cycleT = timeSec % EGG_CYCLE_SEC
    val chargeP = (cycleT / EGG_BLAST_AT_SEC).coerceIn(0f, 1f)
    val ageSinceBlast = cycleT - EGG_BLAST_AT_SEC

    // Starfield de fondo
    for (star in stars) {
        val tw = 0.5f + 0.5f * sin(timeSec * 2f + star.phase)
        drawCircle(
            Color(0xFFD8DEE8).copy(alpha = 0.10f + 0.28f * tw),
            star.radiusDp.dp.toPx(),
            Offset(star.x * w, star.y * h),
        )
    }

    // Placa (mascara de soldadura bordo) con pistas y pads
    val boardLeft = w * 0.08f
    val boardW = w * 0.84f
    val boardH = h * 0.19f
    drawRoundRect(Color(0xFF351019), Offset(boardLeft, boardY), Size(boardW, boardH),
        CornerRadius(10.dp.toPx()))
    drawRoundRect(Color(0xFF7A2233), Offset(boardLeft, boardY), Size(boardW, boardH),
        CornerRadius(10.dp.toPx()), style = androidx.compose.ui.graphics.drawscope.Stroke(2.dp.toPx()))
    val trace = Color(0xFF9C3548)
    val traceW = 3.dp.toPx()
    drawLine(trace, Offset(boardLeft, boardY + boardH * 0.45f),
        Offset(cx - capW * 0.18f, boardY + boardH * 0.45f), traceW, StrokeCap.Round)
    drawLine(trace, Offset(cx + capW * 0.18f, boardY + boardH * 0.45f),
        Offset(boardLeft + boardW * 0.78f, boardY + boardH * 0.45f), traceW, StrokeCap.Round)
    drawLine(trace, Offset(boardLeft + boardW * 0.78f, boardY + boardH * 0.45f),
        Offset(boardLeft + boardW, boardY + boardH * 0.70f), traceW, StrokeCap.Round)
    listOf(0.16f, 0.34f, 0.82f, 0.92f).forEach { f ->
        drawCircle(trace, 4.dp.toPx(),
            Offset(boardLeft + boardW * f, boardY + boardH * 0.72f))
    }

    // Mancha de quemado tras la explosion
    if (ageSinceBlast > 0f) {
        val grow = min(1f, ageSinceBlast * 3f)
        drawOval(
            Color.Black.copy(alpha = 0.55f * grow),
            Offset(cx - capW * 0.85f * grow, boardY - 8.dp.toPx() * grow),
            Size(capW * 1.7f * grow, 22.dp.toPx() * grow),
        )
    }

    // Terminales
    val leadColor = Color(0xFFC0C6CE)
    drawRect(leadColor, Offset(cx - capW * 0.24f, boardY - 6.dp.toPx()),
        Size(3.dp.toPx(), 8.dp.toPx()))
    drawRect(leadColor, Offset(cx + capW * 0.24f, boardY - 6.dp.toPx()),
        Size(3.dp.toPx(), 8.dp.toPx()))

    if (ageSinceBlast <= 0f) {
        // Cargando: se hincha y tiembla cada vez mas; arcos cerca del final.
        val bulge = 1f + 0.16f * chargeP * chargeP
        val wobble = sin(timeSec * 46f) * 4.5f * chargeP * chargeP
        drawCapacitor(Offset(cx + wobble.dp.toPx(), boardY - capH / 2f), capW * bulge, capH, 0f)
        if (chargeP > 0.6f) drawChargeSmoke(cx, boardY - capH, timeSec, (chargeP - 0.6f) / 0.4f)
        if (chargeP > 0.78f) drawArcs(cx, boardY - capH, minSide * 0.17f, timeSec)
    } else {
        // La lata sale despedida rotando; quedan la base y el humo.
        val flyX = cx + ageSinceBlast * 34.dp.toPx()
        val rise = ageSinceBlast * 330.dp.toPx() - 250.dp.toPx() * ageSinceBlast * ageSinceBlast
        val flyY = boardY - capH / 2f - rise
        // Resto de la base
        drawRoundRect(Color(0xFF262B33), Offset(cx - capW / 2f, boardY - capH * 0.18f),
            Size(capW, capH * 0.18f), CornerRadius(3.dp.toPx()))
        drawCapacitor(Offset(flyX, flyY), capW, capH, ageSinceBlast * 300f)
        drawBlastSparks(Offset(cx, boardY - capH * 0.75f), ageSinceBlast, sparks)
        drawBlastSmoke(cx, boardY - capH * 0.75f, ageSinceBlast)
        val flashAlpha = exp(-ageSinceBlast * 7f) * 0.55f
        if (flashAlpha > 0.01f) drawRect(Color(0xFFFFD777).copy(alpha = flashAlpha))
    }
}

/** Electrolitico vertical: cuerpo oscuro, faja bordo, tapa plateada con la cruz de venteo. */
private fun DrawScope.drawCapacitor(center: Offset, capW: Float, capH: Float, rotationDeg: Float) {
    rotate(rotationDeg, center) {
        translate(center.x - capW / 2f, center.y - capH / 2f) {
            drawRoundRect(Color(0xFF20242B), Offset.Zero, Size(capW, capH),
                CornerRadius(6.dp.toPx()))
            drawRect(Color(0xFF7A1220), Offset(capW * 0.62f, capH * 0.06f),
                Size(capW * 0.24f, capH * 0.88f))
            // Lineas de rotulo
            val label = Color(0xFFF5E9E2).copy(alpha = 0.75f)
            listOf(0.30f, 0.42f, 0.54f).forEach { f ->
                drawLine(label, Offset(capW * 0.14f, capH * f),
                    Offset(capW * 0.52f, capH * f), 3.dp.toPx(), StrokeCap.Round)
            }
            drawLine(Color(0xFFFFB300), Offset(capW * 0.14f, capH * 0.68f),
                Offset(capW * 0.52f, capH * 0.68f), 4.dp.toPx(), StrokeCap.Round)
            // Tapa con venteo en K
            drawOval(Color(0xFFC0C6CE), Offset(0f, -capH * 0.045f),
                Size(capW, capH * 0.09f))
            val vent = Color(0xFF5A6068)
            drawLine(vent, Offset(capW * 0.22f, 0f), Offset(capW * 0.78f, 0f),
                2.5.dp.toPx(), StrokeCap.Round)
            drawLine(vent, Offset(capW * 0.5f, -capH * 0.035f),
                Offset(capW * 0.5f, capH * 0.035f), 2.5.dp.toPx(), StrokeCap.Round)
        }
    }
}

private fun DrawScope.drawChargeSmoke(cx: Float, topY: Float, timeSec: Float, strength: Float) {
    for (i in 0 until 5) {
        val age = ((timeSec * 0.55f + i * 0.23f) % 1f)
        val alpha = (1f - age) * 0.20f * strength
        if (alpha <= 0.01f) continue
        drawCircle(
            Color(0xFFB9BDC4).copy(alpha = alpha),
            (5f + age * 17f).dp.toPx(),
            Offset(cx + sin(age * 9f + i * 1.7f) * 9.dp.toPx(), topY - age * 64.dp.toPx()),
        )
    }
}

private fun DrawScope.drawArcs(cx: Float, topY: Float, reach: Float, timeSec: Float) {
    val arcColor = Color(0xFF7DD3FC)
    for (k in 0 until 3) {
        var p = Offset(cx + (k - 1) * 12.dp.toPx(), topY)
        for (seg in 1..4) {
            val next = Offset(
                p.x + sin(timeSec * 39f + k * 5f + seg * 2.3f) * reach * 0.16f,
                topY - reach * seg / 4f,
            )
            drawLine(arcColor.copy(alpha = 0.75f), p, next, 1.8.dp.toPx(), StrokeCap.Round)
            p = next
        }
    }
}

private fun DrawScope.drawBlastSparks(origin: Offset, ageSec: Float, sparks: List<SparkSeed>) {
    val gravity = 560.dp.toPx()
    for (s in sparks) {
        if (ageSec > s.lifeSec) continue
        val frac = 1f - ageSec / s.lifeSec
        val x = origin.x + cos(s.angleRad) * s.speedDp.dp.toPx() * ageSec
        val y = origin.y + sin(s.angleRad) * s.speedDp.dp.toPx() * ageSec +
            0.5f * gravity * ageSec * ageSec
        val alpha = min(1f, frac * 1.4f)
        val color = when {
            frac > 0.75f -> Color(0xFFFFFAD2)
            frac > 0.45f -> Color(0xFFFFD25A)
            frac > 0.20f -> Color(0xFFF08228)
            else -> Color(0xFFC82D23)
        }
        drawCircle(color.copy(alpha = alpha), (s.sizeDp * (0.4f + frac)).dp.toPx(), Offset(x, y))
    }
}

private fun DrawScope.drawBlastSmoke(cx: Float, originY: Float, ageSec: Float) {
    for (i in 0 until 7) {
        val localAge = ageSec - i * 0.07f
        if (localAge <= 0f) continue
        val alpha = (0.13f * exp(-localAge * 1.15f)).coerceIn(0f, 0.13f)
        drawCircle(
            Color(0xFF9AA0A8).copy(alpha = alpha),
            (8f + localAge * 24f).dp.toPx(),
            Offset(cx + sin(i * 2.1f) * (6f + localAge * 13f).dp.toPx(),
                originY - localAge * 74.dp.toPx()),
        )
    }
}
