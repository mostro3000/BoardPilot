package ar.com.mt0.boardpilot.domain

/**
 * Racha de toques sobre el titulo, patron de la familia MT (BoletasTicketsMT,
 * PetriNets, OBD2MT/EVA): 6 toques seguidos con menos de 1,5 s entre uno y
 * otro abren el huevo de pascua. Un toque aislado lo resuelve la UI con una
 * pausa corta (abre "Acerca de" si no vienen mas).
 */
class TitleTapDetector(
    private val requiredTaps: Int = REQUIRED_TAPS,
    private val windowMs: Long = WINDOW_MS,
) {
    private var taps = 0
    private var lastTapMs = 0L

    /** Registra un toque en [nowMs]; devuelve true solo en el toque que completa la racha. */
    fun onTap(nowMs: Long): Boolean {
        if (lastTapMs == 0L || nowMs - lastTapMs > windowMs) taps = 0
        lastTapMs = nowMs
        taps++
        return if (taps >= requiredTaps) {
            taps = 0
            true
        } else {
            false
        }
    }

    fun reset() {
        taps = 0
        lastTapMs = 0L
    }

    companion object {
        const val REQUIRED_TAPS = 6
        const val WINDOW_MS = 1500L
    }
}
