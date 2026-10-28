package ar.com.mt0.boardpilot.domain

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/** Que parte del recuadro de recorte se esta arrastrando. */
enum class CropHandle { TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT, LEFT, TOP, RIGHT, BOTTOM, MOVE }

/**
 * La foto girada [angle] grados (horarios) dentro de su caja contenedora,
 * en px de la foto: que puntos caen sobre la foto y cual es el recuadro mas
 * grande sin esquinas vacias.
 */
data class RotatedPhoto(val width: Float, val height: Float, val angle: Float) {
    private val c = cos(Math.toRadians(angle.toDouble())).toFloat()
    private val s = sin(Math.toRadians(angle.toDouble())).toFloat()
    val boundsW: Float = abs(width * c) + abs(height * s)
    val boundsH: Float = abs(width * s) + abs(height * c)

    /** El punto ([x], [y]) de la caja contenedora cae sobre la foto (con medio px de tolerancia). */
    fun contains(x: Float, y: Float): Boolean {
        val dx = x - boundsW / 2f
        val dy = y - boundsH / 2f
        return abs(dx * c + dy * s) <= width / 2f + EDGE && abs(-dx * s + dy * c) <= height / 2f + EDGE
    }

    fun contains(box: CropBox): Boolean = contains(box.left, box.top) &&
        contains(box.right, box.top) && contains(box.left, box.bottom) && contains(box.right, box.bottom)

    /** El recuadro mas grande que entra en la foto girada, centrado (enderezar sin bordes vacios). */
    fun largestInside(): CropBox {
        val sa = abs(s)
        val ca = abs(c)
        val longer = max(width, height)
        val shorter = min(width, height)
        val (w, h) = if (shorter <= 2f * sa * ca * longer || abs(sa - ca) < 1e-6f) {
            // Dos esquinas tocan el lado largo y las otras quedan en su linea media.
            val x = 0.5f * shorter
            if (width >= height) (x / sa) to (x / ca) else (x / ca) to (x / sa)
        } else {
            // Las cuatro esquinas tocan los cuatro lados.
            val cos2a = ca * ca - sa * sa
            ((width * ca - height * sa) / cos2a) to ((height * ca - width * sa) / cos2a)
        }
        val l = (boundsW - w) / 2f
        val t = (boundsH - h) / 2f
        return CropBox(l, t, l + w, t + h)
    }

    private companion object {
        const val EDGE = 0.5f
    }
}

/**
 * Recuadro de recorte libre sobre la foto girada, en px de su caja
 * contenedora, como en las apps de imagenes: se mueve arrastrando por
 * dentro y se ajusta desde las esquinas o los bordes. Nunca sale de la foto
 * (si el dedo se pasa, se frena contra el borde y desliza a lo largo) ni
 * queda mas chico que el minimo pedido.
 */
data class CropBox(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top

    /** Que agarra un toque en ([x], [y]): primero las esquinas, despues los bordes, despues adentro. */
    fun hit(x: Float, y: Float, radius: Float): CropHandle? {
        fun near(px: Float, py: Float) = abs(x - px) <= radius && abs(y - py) <= radius
        when {
            near(left, top) -> return CropHandle.TOP_LEFT
            near(right, top) -> return CropHandle.TOP_RIGHT
            near(left, bottom) -> return CropHandle.BOTTOM_LEFT
            near(right, bottom) -> return CropHandle.BOTTOM_RIGHT
        }
        val withinX = x in left..right
        val withinY = y in top..bottom
        return when {
            withinY && abs(x - left) <= radius -> CropHandle.LEFT
            withinY && abs(x - right) <= radius -> CropHandle.RIGHT
            withinX && abs(y - top) <= radius -> CropHandle.TOP
            withinX && abs(y - bottom) <= radius -> CropHandle.BOTTOM
            withinX && withinY -> CropHandle.MOVE
            else -> null
        }
    }

    /**
     * Arrastre de [handle] en ([dx], [dy]) px sin salir de [photo]: si el
     * arrastre entero no entra, se queda con el mayor tramo que entra, sea en
     * la direccion del dedo o solo en horizontal o en vertical (deslizar).
     */
    fun dragged(handle: CropHandle, dx: Float, dy: Float, photo: RotatedPhoto, minSize: Float): CropBox {
        fun fit(mx: Float, my: Float): CropBox {
            val full = step(handle, mx, my, photo.boundsW, photo.boundsH, minSize)
            if (photo.contains(full)) return full
            var lo = 0f
            var hi = 1f
            repeat(18) {
                val mid = (lo + hi) / 2f
                if (photo.contains(step(handle, mx * mid, my * mid, photo.boundsW, photo.boundsH, minSize))) {
                    lo = mid
                } else {
                    hi = mid
                }
            }
            return step(handle, mx * lo, my * lo, photo.boundsW, photo.boundsH, minSize)
        }
        return listOf(fit(dx, dy), fit(dx, 0f), fit(0f, dy))
            .filter { photo.contains(it) }
            .maxByOrNull { it.distanceTo(this) } ?: this
    }

    private fun distanceTo(other: CropBox): Float {
        val a = left - other.left
        val b = top - other.top
        val c = right - other.right
        val d = bottom - other.bottom
        return sqrt(a * a + b * b + c * c + d * d)
    }

    /** El arrastre sin mirar la foto: solo la caja contenedora y el tamano minimo. */
    private fun step(handle: CropHandle, dx: Float, dy: Float, boundsW: Float, boundsH: Float, minSize: Float): CropBox =
        when (handle) {
            CropHandle.MOVE -> {
                val mx = dx.coerceIn(-left, boundsW - right)
                val my = dy.coerceIn(-top, boundsH - bottom)
                CropBox(left + mx, top + my, right + mx, bottom + my)
            }
            CropHandle.LEFT -> copy(left = (left + dx).coerceIn(0f, max(0f, right - minSize)))
            CropHandle.RIGHT -> copy(right = (right + dx).coerceIn(min(left + minSize, boundsW), boundsW))
            CropHandle.TOP -> copy(top = (top + dy).coerceIn(0f, max(0f, bottom - minSize)))
            CropHandle.BOTTOM -> copy(bottom = (bottom + dy).coerceIn(min(top + minSize, boundsH), boundsH))
            else -> {
                val movesLeft = handle == CropHandle.TOP_LEFT || handle == CropHandle.BOTTOM_LEFT
                val movesTop = handle == CropHandle.TOP_LEFT || handle == CropHandle.TOP_RIGHT
                val l = if (movesLeft) (left + dx).coerceIn(0f, max(0f, right - minSize)) else left
                val r = if (movesLeft) right else (right + dx).coerceIn(min(left + minSize, boundsW), boundsW)
                val t = if (movesTop) (top + dy).coerceIn(0f, max(0f, bottom - minSize)) else top
                val b = if (movesTop) bottom else (bottom + dy).coerceIn(min(top + minSize, boundsH), boundsH)
                CropBox(l, t, r, b)
            }
        }
}

/** Giro de la foto en el recorte: el cuarto de vuelta mas cercano y el ajuste fino (+-45). */
fun quarterOf(angle: Float): Float = (angle / 90f).roundToInt() * 90f

fun fineOf(angle: Float): Float = angle - quarterOf(angle)

/** Angulo llevado a (-180, 180]. */
fun normalizedAngle(angle: Float): Float {
    val a = ((angle + 180f) % 360f + 360f) % 360f - 180f
    return if (a == -180f) 180f else a
}

/**
 * Zoom de solo lectura del mapa de la placa (foto + cajas juntas): de 1 a 6
 * aumentos, acercando hacia el punto entre los dedos y sin dejar ver fuera
 * de la foto. El desplazamiento es en px, desde el centro.
 */
data class MapZoom(val zoom: Float = 1f, val panX: Float = 0f, val panY: Float = 0f) {
    val zoomed: Boolean get() = zoom > 1.01f

    fun afterPinch(
        centroidX: Float,
        centroidY: Float,
        dragX: Float,
        dragY: Float,
        zoomChange: Float,
        width: Float,
        height: Float,
    ): MapZoom {
        val newZoom = (zoom * zoomChange).coerceIn(1f, MAX_ZOOM)
        val z = newZoom / zoom
        val qx = centroidX - width / 2f
        val qy = centroidY - height / 2f
        val maxX = (newZoom - 1f) * width / 2f
        val maxY = (newZoom - 1f) * height / 2f
        return MapZoom(
            zoom = newZoom,
            panX = (qx * (1f - z) + z * panX + dragX).coerceIn(-maxX, maxX),
            panY = (qy * (1f - z) + z * panY + dragY).coerceIn(-maxY, maxY),
        )
    }

    companion object {
        const val MAX_ZOOM = 6f
    }
}
