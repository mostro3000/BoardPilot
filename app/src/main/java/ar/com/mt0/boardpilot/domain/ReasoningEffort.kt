package ar.com.mt0.boardpilot.domain

/**
 * Nivel de esfuerzo de razonamiento de Muse Spark, elegido por el usuario
 * en Configuracion segun cuanto quiere gastar en tokens. AUTO omite el
 * parametro y la API decide de forma adaptativa; el resto mapea al
 * `reasoning_effort` oficial (xhigh es el maximo aceptado).
 */
enum class ReasoningEffort {
    AUTO, MINIMAL, LOW, MEDIUM, HIGH, XHIGH;

    /** Valor para la API; null = no mandar el parametro (adaptativo). */
    val apiValue: String?
        get() = when (this) {
            AUTO -> null
            MINIMAL -> "minimal"
            LOW -> "low"
            MEDIUM -> "medium"
            HIGH -> "high"
            XHIGH -> "xhigh"
        }

    companion object {
        val DEFAULT = AUTO

        fun fromStored(name: String?): ReasoningEffort =
            entries.firstOrNull { it.name == name } ?: DEFAULT
    }
}
