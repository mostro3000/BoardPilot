package ar.com.mt0.boardpilot.domain

/**
 * Modo de Muse Spark en la Meta Model API. CONTRIBUTOR: mucho mas barato, pero Meta
 * puede entrenar con lo enviado y lo respondido, y no se pueden enviar datos personales
 * ni confidenciales (Terms of Service, seccion 6). STANDARD: Meta no entrena sus modelos
 * con el contenido. CONTRIBUTOR es el valor por defecto (decision del participante,
 * 31 oct), pero nada se envia en ese modo hasta que el usuario acepta el aviso.
 */
enum class SparkMode(val modelId: String) {
    STANDARD("muse-spark-1.3"),
    CONTRIBUTOR("muse-spark-1.3-contributor");

    /** El modo con el que se envia de verdad: sin el aviso aceptado, nunca Contributor. */
    fun effectiveFor(noticeAccepted: Boolean): SparkMode =
        if (this == CONTRIBUTOR && !noticeAccepted) STANDARD else this

    companion object {
        val DEFAULT = CONTRIBUTOR

        /** Sin eleccion guardada o con un valor desconocido, el de por defecto. */
        fun fromStored(name: String?): SparkMode =
            entries.firstOrNull { it.name == name } ?: DEFAULT
    }
}
