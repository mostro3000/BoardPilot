package ar.com.mt0.boardpilot.domain

/**
 * Nivel de esfuerzo de razonamiento de Muse Spark, elegido por el usuario
 * en Configuracion segun cuanto quiere gastar en tokens. AUTO omite el
 * parametro y la API decide de forma adaptativa; el resto mapea al
 * `reasoning_effort` oficial. XHIGH es el valor por defecto y el tope que ofrece la
 * app: la API acepta ademas `max`, pero en las pruebas del 31 oct no mejoro nada y
 * agoto los tokens en 2 de 24 diagnosticos.
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
        val DEFAULT = XHIGH

        fun fromStored(name: String?): ReasoningEffort =
            entries.firstOrNull { it.name == name } ?: DEFAULT
    }
}
