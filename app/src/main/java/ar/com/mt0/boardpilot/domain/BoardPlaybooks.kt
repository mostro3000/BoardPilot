package ar.com.mt0.boardpilot.domain

/**
 * Guias por tipo de placa: fallas tipicas y estrategia de medicion que se le
 * dan a Muse Spark. Son datos versionados en resources/playbooks.json, el mismo
 * archivo que usa tools/smoke_spark.py.
 */
object BoardPlaybooks {
    private val data: Map<*, *> by lazy {
        val text = BoardPlaybooks::class.java.getResourceAsStream("/playbooks.json")
            ?.bufferedReader()?.use { it.readText() }
            ?: error("falta playbooks.json")
        MiniJson.parse(text) as Map<*, *>
    }

    val version: String get() = data["version"] as String

    /** Tipos que Muse Spark puede reportar (todos menos UNKNOWN). */
    val detectable: List<BoardType> = BoardType.entries.filter { it != BoardType.UNKNOWN }

    /** Fallas tipicas y estrategia del tipo (vacio para UNKNOWN). */
    fun guide(type: BoardType): String =
        ((data["types"] as? Map<*, *>)?.get(type.name) as? String).orEmpty()

    /**
     * Regla del prompt de diagnostico: con el tipo elegido por el tecnico, sus
     * fallas tipicas; sin elegir, detectarlo primero y usar las de ese tipo.
     */
    fun promptSection(type: BoardType): String = if (type == BoardType.UNKNOWN) {
        "- FIRST identify what kind of board this is from the photo and report \"boardType\" as exactly " +
            "one of " + detectable.joinToString(", ") + ". Then give at most 4 hypotheses ranked by " +
            "likelihood with the typical failures of THAT kind of board for the symptom, adapted to what " +
            "is actually visible:\n" + detectable.joinToString("\n") { "  - ${it.name}: ${guide(it)}" }
    } else {
        "- The technician says this board is ${type.name}: report \"boardType\" as ${type.name} and give " +
            "at most 4 hypotheses ranked by likelihood with its typical failures for the symptom, adapted " +
            "to what is actually visible. ${guide(type)}"
    }
}
