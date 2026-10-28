package ar.com.mt0.boardpilot.domain

/**
 * Giro (grados, horario) y espejo horizontal posterior que enderezan una foto
 * segun su orientacion EXIF: el orden es girar y despues espejar.
 */
data class ExifFix(val degrees: Float, val mirror: Boolean) {
    val isIdentity: Boolean get() = degrees == 0f && !mirror

    /** Con giros de 90 grados el ancho y el alto de la foto se intercambian. */
    val swapsSides: Boolean get() = degrees == 90f || degrees == -90f
}

/**
 * Como se carga una foto de galeria o camara para el diagnostico: derecha
 * segun su EXIF y con el lado mayor en el objetivo (sin agrandar las chicas).
 */
object PhotoLoading {
    /** Valores de la etiqueta Orientation del estandar EXIF (1 = derecha). */
    const val NORMAL = 1
    const val FLIP_HORIZONTAL = 2
    const val ROTATE_180 = 3
    const val FLIP_VERTICAL = 4
    const val TRANSPOSE = 5
    const val ROTATE_90 = 6
    const val TRANSVERSE = 7
    const val ROTATE_270 = 8

    /**
     * Muestreo de decodificacion (potencia de 2) que deja el lado mayor en al
     * menos [target] px; el ajuste fino se hace despues con [scaleFor]. Antes
     * una foto de 4000 px con objetivo 1600 terminaba en 1000 px.
     */
    fun sampleSize(longest: Int, target: Int): Int {
        var sample = 1
        while (longest / (sample * 2) >= target) sample *= 2
        return sample
    }

    /** Escala final para que el lado mayor quede en [target] px (nunca agranda). */
    fun scaleFor(longest: Int, target: Int): Float =
        if (longest <= target) 1f else target.toFloat() / longest.toFloat()

    /** Correccion para la etiqueta Orientation; valores desconocidos no tocan la foto. */
    fun exifFix(orientation: Int): ExifFix = when (orientation) {
        FLIP_HORIZONTAL -> ExifFix(0f, mirror = true)
        ROTATE_180 -> ExifFix(180f, mirror = false)
        FLIP_VERTICAL -> ExifFix(180f, mirror = true)
        TRANSPOSE -> ExifFix(90f, mirror = true)
        ROTATE_90 -> ExifFix(90f, mirror = false)
        TRANSVERSE -> ExifFix(-90f, mirror = true)
        ROTATE_270 -> ExifFix(-90f, mirror = false)
        else -> ExifFix(0f, mirror = false)
    }
}
