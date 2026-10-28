package ar.com.mt0.boardpilot.domain

import kotlin.math.max
import kotlin.math.min

/** Rectangulo (en px o dp) donde se dibuja la foto dentro de su recuadro. */
data class FitRect(val left: Float, val top: Float, val width: Float, val height: Float)

/**
 * Geometria y marcas del mapa de la placa y de la lupa de medicion: donde cae
 * la foto en su recuadro, que zona ampliar para una medicion y que se marca
 * sobre cada componente (lo ya medido y el % de su hipotesis).
 */
object BoardView {
    /**
     * La foto entera centrada en el recuadro, sin recortar ni deformar: las
     * cajas se dibujan sobre este rectangulo. Antes la foto se recortaba y las
     * cajas quedaban corridas en fotos muy altas o muy anchas.
     */
    fun fit(containerW: Float, containerH: Float, imageW: Float, imageH: Float): FitRect {
        if (containerW <= 0f || containerH <= 0f || imageW <= 0f || imageH <= 0f) {
            return FitRect(0f, 0f, max(containerW, 0f), max(containerH, 0f))
        }
        val scale = min(containerW / imageW, containerH / imageH)
        val w = imageW * scale
        val h = imageH * scale
        return FitRect((containerW - w) / 2f, (containerH - h) / 2f, w, h)
    }

    /** Proporciones (ancho/alto) de la lupa: cuadrada para piezas altas, apaisada para anchas. */
    const val LENS_MIN_ASPECT = 1.0f
    const val LENS_MAX_ASPECT = 1.8f

    /**
     * Zona normalizada de la foto que muestra la lupa: el componente medido y
     * los puntos de las puntas, con aire alrededor y una proporcion entre
     * [LENS_MIN_ASPECT] y [LENS_MAX_ASPECT] medida en px de una foto de
     * [photoAspect]. El aire es proporcional a la pieza pero con tope: antes
     * crecia sin limite y con un componente grande (y la lupa siempre apaisada)
     * la lupa mostraba casi toda la placa.
     */
    fun lensRegion(
        target: NormBox,
        points: List<Pair<Float, Float>>,
        photoAspect: Float,
    ): NormBox {
        // En "altos de foto": x por la proporcion, asi ambos ejes miden en px.
        val a = photoAspect.coerceAtLeast(0.01f)
        var x0 = target.x * a
        var x1 = (target.x + target.w) * a
        var y0 = target.y
        var y1 = target.y + target.h
        points.forEach { (px, py) ->
            x0 = min(x0, px * a)
            x1 = max(x1, px * a)
            y0 = min(y0, py)
            y1 = max(y1, py)
        }
        val pad = (max(x1 - x0, y1 - y0) * 0.6f).coerceIn(LENS_MIN_PAD, LENS_MAX_PAD)
        // Proporcion dentro del rango, creciendo el lado corto desde el centro.
        var w = x1 - x0 + 2 * pad
        var h = y1 - y0 + 2 * pad
        if (w / h < LENS_MIN_ASPECT) w = h * LENS_MIN_ASPECT
        if (w / h > LENS_MAX_ASPECT) h = w / LENS_MAX_ASPECT
        // Dentro de la foto; si no entra entera, la foto completa en ese eje.
        w = min(w, a)
        h = min(h, 1f)
        val cx = ((x0 + x1) / 2f).coerceIn(w / 2f, a - w / 2f)
        val cy = ((y0 + y1) / 2f).coerceIn(h / 2f, 1f - h / 2f)
        return NormBox(
            ((cx - w / 2f) / a).coerceIn(0f, 1f),
            (cy - h / 2f).coerceIn(0f, 1f),
            (w / a).coerceIn(0f, 1f),
            h.coerceIn(0f, 1f),
        )
    }

    /** Proporcion (ancho/alto) con que se dibuja la lupa para esa zona de la foto. */
    fun lensAspect(region: NormBox, photoAspect: Float): Float =
        (region.w * photoAspect / region.h.coerceAtLeast(0.0001f))
            .coerceIn(LENS_MIN_ASPECT, LENS_MAX_ASPECT)

    // Aire alrededor de la pieza en la lupa, en altos de foto.
    private const val LENS_MIN_PAD = 0.04f
    private const val LENS_MAX_PAD = 0.10f

    /** Cajas del mapa sin "ver todas": en placas densas SAM devuelve cientos (visto: 116). */
    const val MAP_LIMIT = 40

    /**
     * Las cajas principales del mapa: las que estan en juego ([inPlay]: hipotesis,
     * mediciones, lo medido, el veredicto, la seleccionada), las danadas y las mas
     * grandes, con el mismo criterio que lo que se manda a Muse Spark.
     */
    fun mapComponents(
        components: List<BoardComponent>,
        inPlay: Set<String>,
        limit: Int = MAP_LIMIT,
    ): List<BoardComponent> = SparkCandidates.select(components, inPlay, limit)

    /** Lo medido sobre cada componente: anormal pesa mas que no claro, y este mas que normal. */
    fun measuredEvidence(results: List<MeasurementResult>): Map<String, MeasurementOutcome> =
        results.filter { it.componentId.isNotBlank() }
            .groupBy { it.componentId }
            .mapValues { (_, measured) -> measured.maxBy { severity(it.outcome) }.outcome }

    /** Porcentaje de la hipotesis mas probable sobre cada componente (como en la lista). */
    fun hypothesisShares(hypotheses: List<Hypothesis>): Map<String, Int> =
        hypotheses.groupBy { it.componentId }
            .mapValues { (_, onComponent) -> (onComponent.maxOf { it.confidence } * 100).toInt() }

    private fun severity(outcome: MeasurementOutcome): Int = when (outcome) {
        MeasurementOutcome.NORMAL -> 0
        MeasurementOutcome.UNCLEAR -> 1
        MeasurementOutcome.ABNORMAL -> 2
    }
}
