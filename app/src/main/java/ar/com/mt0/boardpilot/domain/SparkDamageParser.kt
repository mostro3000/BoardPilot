package ar.com.mt0.boardpilot.domain

/** Un dano visible localizado por el pre-escaneo dedicado de Muse Spark. */
data class DamageFinding(val description: String, val box: NormBox)

/**
 * Parsea el pre-escaneo de dano (pregunta directa y unica sobre la foto
 * limpia: acerto 3/3 en las pruebas, contra ~mitad de las corridas del
 * diagnostico conjunto) y lo asigna a los candidatos SAM:
 * - si el centro del dano cae dentro de un componente mapeado, ese
 *   componente recibe la marca de dano visible;
 * - si no, se crea un candidato sintetico DMG numerado sin chocar ids.
 */
object SparkDamageParser {
    private const val MAX_FINDINGS = 3
    private val DMG_ID = Regex("""dmg(\d+)-spark""")

    fun parse(text: String): List<DamageFinding> {
        val root = MiniJson.parseObjectLenient(text) ?: return emptyList()
        return (root["damages"] as? List<*>).orEmpty()
            .mapNotNull { raw ->
                val m = raw as? Map<*, *> ?: return@mapNotNull null
                val description = (m["description"] as? String)
                    ?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                val x = num(m["x"]) ?: return@mapNotNull null
                val y = num(m["y"]) ?: return@mapNotNull null
                val w = num(m["w"]) ?: return@mapNotNull null
                val h = num(m["h"]) ?: return@mapNotNull null
                DamageFinding(description, clampBox(x, y, w, h))
            }
            .take(MAX_FINDINGS)
    }

    /** Devuelve (dano por componente existente, componentes sinteticos). */
    fun assign(
        findings: List<DamageFinding>,
        components: List<BoardComponent>,
    ): Pair<Map<String, String>, List<BoardComponent>> {
        val damages = LinkedHashMap<String, String>()
        val extras = mutableListOf<BoardComponent>()
        var nextDmg = nextDamageIndex(components.map { it.id })
        findings.forEach { finding ->
            val cx = finding.box.x + finding.box.w / 2f
            val cy = finding.box.y + finding.box.h / 2f
            val host = components
                .filter { c ->
                    cx in c.box.x..(c.box.x + c.box.w) &&
                        cy in c.box.y..(c.box.y + c.box.h)
                }
                .minByOrNull { it.box.w * it.box.h }
            if (host != null) {
                damages.putIfAbsent(host.id, finding.description)
            } else {
                extras += BoardComponent(
                    id = "dmg$nextDmg-spark",
                    designator = "DMG $nextDmg",
                    kind = ComponentKind.OTHER,
                    box = finding.box,
                    visibleDamage = finding.description,
                )
                nextDmg++
            }
        }
        return damages to extras
    }

    /** Proximo indice libre para ids dmgN-spark. */
    fun nextDamageIndex(ids: Collection<String>): Int {
        val used = ids.mapNotNull { DMG_ID.matchEntire(it)?.groupValues?.get(1)?.toInt() }
        return (used.maxOrNull() ?: 0) + 1
    }

    fun clampBox(x: Double, y: Double, w: Double, h: Double): NormBox {
        val cx = x.toFloat().coerceIn(0f, 0.98f)
        val cy = y.toFloat().coerceIn(0f, 0.98f)
        return NormBox(
            cx, cy,
            w.toFloat().coerceIn(0.02f, 1f - cx),
            h.toFloat().coerceIn(0.02f, 1f - cy),
        )
    }

    private fun num(value: Any?): Double? =
        (value as? Number)?.toDouble()?.takeIf { it.isFinite() }
}
