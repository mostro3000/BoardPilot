package ar.com.mt0.boardpilot.domain

/** Un dano visible localizado por el pre-escaneo dedicado de Muse Spark. */
data class DamageFinding(val description: String, val box: NormBox)

/**
 * Parsea el pre-escaneo de dano (pregunta directa sobre la foto limpia, entera
 * y por sectores ampliados), une lo que vio cada pasada y lo asigna a los
 * candidatos SAM:
 * - si el centro del dano cae dentro de un componente mapeado, ese
 *   componente recibe la marca de dano visible;
 * - si no, se crea un candidato sintetico DMG numerado sin chocar ids.
 */
object SparkDamageParser {
    private const val MAX_FINDINGS = 3

    /** Fraccion de la caja menor que deben compartir dos hallazgos para ser el mismo dano. */
    private const val SAME_DAMAGE_OVERLAP = 0.5f

    /**
     * Sectores 2x2 con solape que se escanean ampliados, ademas de la foto entera.
     * En las pruebas del 31 oct la zona carbonizada de una fuente paso de 1/4 a 4/4
     * sin falsos positivos en 9 fotos; una foto entera vio algo que sus sectores no.
     */
    val SCAN_SECTORS = listOf(
        NormBox(0f, 0f, 0.6f, 0.6f),
        NormBox(0.4f, 0f, 0.6f, 0.6f),
        NormBox(0f, 0.4f, 0.6f, 0.6f),
        NormBox(0.4f, 0.4f, 0.6f, 0.6f),
    )

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
                val box = validBox(x, y, w, h) ?: return@mapNotNull null
                DamageFinding(description, box)
            }
            .take(MAX_FINDINGS)
    }

    /** Hallazgo de un sector en coordenadas de la foto entera. */
    fun fromSector(finding: DamageFinding, sector: NormBox): DamageFinding = finding.copy(
        box = NormBox(
            sector.x + finding.box.x * sector.w,
            sector.y + finding.box.y * sector.h,
            finding.box.w * sector.w,
            finding.box.h * sector.h,
        ),
    )

    /**
     * Une las pasadas del pre-escaneo (la primera, la foto entera): hallazgos que
     * se solapan son el mismo dano, con el texto del primero y la caja mayor (un
     * sector puede ver solo parte de la pieza). Quedan primero los que vieron mas
     * pasadas y, a igual cantidad, los que aparecieron antes.
     */
    fun merge(passes: List<List<DamageFinding>>): List<DamageFinding> {
        class Group(var finding: DamageFinding, var seen: Int = 1)

        val groups = mutableListOf<Group>()
        passes.flatten().forEach { found ->
            val group = groups.firstOrNull { sameDamage(it.finding.box, found.box) }
            if (group == null) {
                groups += Group(found)
            } else {
                group.seen++
                if (area(found.box) > area(group.finding.box)) {
                    group.finding = group.finding.copy(box = found.box)
                }
            }
        }
        return groups.sortedByDescending { it.seen }.take(MAX_FINDINGS).map { it.finding }
    }

    private fun area(box: NormBox): Float = box.w * box.h

    private fun sameDamage(a: NormBox, b: NormBox): Boolean {
        val w = minOf(a.x + a.w, b.x + b.w) - maxOf(a.x, b.x)
        val h = minOf(a.y + a.h, b.y + b.h) - maxOf(a.y, b.y)
        return w > 0f && h > 0f && w * h >= SAME_DAMAGE_OVERLAP * minOf(area(a), area(b))
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

    /**
     * Caja del modelo, o null si no es una caja normalizada creible. Antes se
     * "arreglaba" clampeando: porcentajes 0-100 (55, 56, 23, 15) terminaban en
     * una cajita valida en la esquina y fabricaban un dano fantasma. Una caja
     * que se sale apenas de la foto se recorta al borde; una fuera de escala,
     * con tamano cero o negativo se descarta entera.
     */
    fun validBox(x: Double, y: Double, w: Double, h: Double): NormBox? {
        if (x !in 0.0..1.0 || y !in 0.0..1.0) return null
        if (w <= 0.0 || h <= 0.0 || w > 1.0 || h > 1.0) return null
        val width = minOf(w, 1.0 - x)
        val height = minOf(h, 1.0 - y)
        if (width <= 0.0 || height <= 0.0) return null
        return NormBox(x.toFloat(), y.toFloat(), width.toFloat(), height.toFloat())
    }

    private fun num(value: Any?): Double? =
        (value as? Number)?.toDouble()?.takeIf { it.isFinite() }
}
