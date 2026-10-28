package ar.com.mt0.boardpilot.domain

/**
 * Candidatos que se mandan a Muse Spark. En placas densas SAM devuelve cientos
 * de cajas y el modelo agota su presupuesto razonando sin responder (visto con
 * una fuente de PC: 116 candidatos, 12000 tokens de razonamiento, respuesta
 * vacia). Se mandan como mucho [MAX]: primero los obligatorios (los de las
 * hipotesis o mediciones en juego), despues los que tienen dano visible y
 * despues los mas grandes, en su orden original. El mapa sigue mostrando todos.
 */
object SparkCandidates {
    const val MAX = 60

    fun select(
        components: List<BoardComponent>,
        mustInclude: Set<String> = emptySet(),
        max: Int = MAX,
    ): List<BoardComponent> {
        if (components.size <= max) return components
        val keep = components
            .sortedWith(
                compareByDescending<BoardComponent> { it.id in mustInclude }
                    .thenByDescending { it.visibleDamage != null }
                    .thenByDescending { it.box.w * it.box.h },
            )
            .take(max)
            .map { it.id }
            .toSet()
        return components.filter { it.id in keep }
    }
}
