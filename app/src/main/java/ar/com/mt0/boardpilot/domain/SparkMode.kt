package ar.com.mt0.boardpilot.domain

/**
 * Modo de Muse Spark en la Meta Model API. STANDARD: Meta no entrena sus modelos
 * con el contenido. CONTRIBUTOR: mucho mas barato, pero Meta puede entrenar con lo
 * enviado y lo respondido, y no se pueden enviar datos personales ni confidenciales
 * (Terms of Service, seccion 6). Por eso STANDARD es siempre el valor por defecto y
 * CONTRIBUTOR solo se activa si el usuario acepta el aviso en Configuracion.
 */
enum class SparkMode(val modelId: String) {
    STANDARD("muse-spark-1.3"),
    CONTRIBUTOR("muse-spark-1.3-contributor");

    companion object {
        val DEFAULT = STANDARD

        /** Un valor guardado desconocido nunca activa Contributor: vuelve al de por defecto. */
        fun fromStored(name: String?): SparkMode =
            entries.firstOrNull { it.name == name } ?: DEFAULT
    }
}
