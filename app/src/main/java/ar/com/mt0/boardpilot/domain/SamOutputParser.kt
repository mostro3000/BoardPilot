package ar.com.mt0.boardpilot.domain

/** Una caja devuelta por SAM, ya normalizada a 0..1 sobre la imagen. */
data class SamBox(
    val objectId: Int,
    val box: NormBox,
    val confidence: Double?,
)

/**
 * Parser de la salida de SAM 3.1 (gramatica oficial de tokens especiales):
 * `<0f>id<|box;x1=..;y1=..;x2=..;y2=..;w=W;h=H[;c=CONF]|><|mask;...|>`
 * A BoardPilot le alcanzan las cajas para el overlay; las mascaras se
 * ignoran por ahora (la Fase 2 dibuja cajas, no pixeles).
 */
object SamOutputParser {
    /** Solape IoU entre dos cajas normalizadas (0 = nada, 1 = identicas). */
    fun iou(a: NormBox, b: NormBox): Float {
        val x1 = maxOf(a.x, b.x)
        val y1 = maxOf(a.y, b.y)
        val x2 = minOf(a.x + a.w, b.x + b.w)
        val y2 = minOf(a.y + a.h, b.y + b.h)
        val interW = (x2 - x1).coerceAtLeast(0f)
        val interH = (y2 - y1).coerceAtLeast(0f)
        val inter = interW * interH
        val union = a.w * a.h + b.w * b.h - inter
        return if (union <= 0f) 0f else inter / union
    }

    private val boxRegex = Regex(
        """(\d+)<\|box;x1=([\d.]+);y1=([\d.]+);x2=([\d.]+);y2=([\d.]+);w=(\d+);h=(\d+)(?:;c=([\d.]+))?\|>""",
    )

    fun parse(outputText: String): List<SamBox> =
        boxRegex.findAll(outputText).mapNotNull { match ->
            val (id, x1, y1, x2, y2, w, h, conf) = match.destructured
            val frameW = w.toFloatOrNull() ?: return@mapNotNull null
            val frameH = h.toFloatOrNull() ?: return@mapNotNull null
            if (frameW <= 0f || frameH <= 0f) return@mapNotNull null
            val nx1 = (x1.toFloatOrNull() ?: return@mapNotNull null) / frameW
            val ny1 = (y1.toFloatOrNull() ?: return@mapNotNull null) / frameH
            val nx2 = (x2.toFloatOrNull() ?: return@mapNotNull null) / frameW
            val ny2 = (y2.toFloatOrNull() ?: return@mapNotNull null) / frameH
            val cx = nx1.coerceIn(0f, 1f)
            val cy = ny1.coerceIn(0f, 1f)
            val width = (nx2.coerceIn(0f, 1f) - cx).coerceAtLeast(0f)
            val height = (ny2.coerceIn(0f, 1f) - cy).coerceAtLeast(0f)
            if (width == 0f || height == 0f) return@mapNotNull null
            SamBox(
                objectId = id.toIntOrNull() ?: return@mapNotNull null,
                box = NormBox(cx, cy, width, height),
                confidence = conf.takeIf { it.isNotEmpty() }?.toDoubleOrNull(),
            )
        }.toList()
}
