package ar.com.mt0.boardpilot.domain

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/**
 * Encuadre de la foto en el recorte, en px del marco: [scale] relativa al
 * encaje inicial (1 = la foto cubre el marco), [rotation] en grados horarios
 * y desplazamiento del centro de la foto respecto del centro del marco.
 */
data class PhotoFraming(
    val scale: Float = 1f,
    val rotation: Float = 0f,
    val offsetX: Float = 0f,
    val offsetY: Float = 0f,
) {
    /**
     * Gesto con los dedos: acerca/aleja y gira alrededor del punto entre los
     * dedos, que queda fijo bajo ellos, y suma el arrastre.
     */
    fun afterGesture(
        centroidX: Float,
        centroidY: Float,
        panX: Float,
        panY: Float,
        zoom: Float,
        turn: Float,
        frameW: Float,
        frameH: Float,
    ): PhotoFraming {
        val newScale = (scale * zoom).coerceIn(MIN_SCALE, MAX_SCALE)
        val z = newScale / scale
        val (rx, ry) = rotate(
            (centroidX - (frameW / 2f + offsetX)) * z,
            (centroidY - (frameH / 2f + offsetY)) * z,
            turn,
        )
        return PhotoFraming(
            scale = newScale,
            rotation = normalized(rotation + turn),
            offsetX = centroidX - rx - frameW / 2f + panX,
            offsetY = centroidY - ry - frameH / 2f + panY,
        )
    }

    /** Giro exacto de 90 grados horarios alrededor del centro del marco. */
    fun turned90(): PhotoFraming =
        copy(rotation = normalized(rotation + 90f), offsetX = -offsetY, offsetY = offsetX)

    /**
     * Escala minima (relativa al encaje inicial) para que la foto, con su giro
     * actual y centrada, cubra todo el marco sin dejar bordes vacios.
     */
    fun coverScale(imageW: Float, imageH: Float, frameW: Float, frameH: Float): Float {
        val r = Math.toRadians(rotation.toDouble())
        val c = abs(cos(r)).toFloat()
        val s = abs(sin(r)).toFloat()
        val fit = max(frameW / imageW, frameH / imageH)
        val needed = max((frameW * c + frameH * s) / imageW, (frameW * s + frameH * c) / imageH)
        return needed / fit
    }

    /**
     * Donde cae en el marco un punto de la foto ([px], [py] en px de la foto
     * relativos a su centro), con [fit] = escala de encaje inicial.
     */
    fun toFrame(px: Float, py: Float, fit: Float, frameW: Float, frameH: Float): Pair<Float, Float> {
        val (rx, ry) = rotate(px * fit * scale, py * fit * scale, rotation)
        return (frameW / 2f + offsetX + rx) to (frameH / 2f + offsetY + ry)
    }

    companion object {
        const val MIN_SCALE = 0.3f
        const val MAX_SCALE = 8f
    }
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

private fun rotate(x: Float, y: Float, degrees: Float): Pair<Float, Float> {
    val r = Math.toRadians(degrees.toDouble())
    val c = cos(r).toFloat()
    val s = sin(r).toFloat()
    return (x * c - y * s) to (x * s + y * c)
}

private fun normalized(degrees: Float): Float = ((degrees % 360f) + 360f) % 360f
