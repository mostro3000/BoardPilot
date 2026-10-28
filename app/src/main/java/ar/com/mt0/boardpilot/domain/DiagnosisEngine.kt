package ar.com.mt0.boardpilot.domain

/**
 * Maquina de estados del diagnostico. Logica pura, deterministica y testeable.
 *
 * Reglas centrales (ver CLAUDE.md):
 * - Nunca se registra una medicion sin confirmar antes su compuerta de seguridad.
 * - Un veredicto exige evidencia de medicion real que confirme la hipotesis lider
 *   y confianza >= umbral; si la evidencia no alcanza, se pide mas evidencia.
 */
class DiagnosisEngine(
    initial: DiagnosisSession,
    private val verdictThreshold: Double = 0.80,
) {
    var session: DiagnosisSession = initial
        private set

    sealed interface SubmitResult {
        data object Advanced : SubmitResult
        data class Rejected(val reason: RejectReason) : SubmitResult
    }

    /** Ajusta el sintoma declarado por el tecnico antes de mapear la placa. */
    fun updateSymptom(symptom: String) {
        check(session.stage == Stage.INTAKE) { "El sintoma solo se ajusta al ingreso" }
        val clean = symptom.trim()
        if (clean.isNotEmpty()) session = session.copy(symptom = clean)
    }

    fun loadMapping(components: List<BoardComponent>) {
        check(session.stage == Stage.INTAKE) { "El mapeo solo se carga desde INTAKE" }
        session = session.copy(components = components, stage = Stage.MAPPING)
    }

    /** Suma candidatos (p. ej. dano visible que SAM no encuadro) sin duplicar ids. */
    fun addComponents(extra: List<BoardComponent>) {
        check(session.stage == Stage.MAPPING) { "Solo se agregan candidatos tras mapear" }
        val known = session.components.map { it.id }.toSet()
        val fresh = extra.filterNot { it.id in known }
        if (fresh.isNotEmpty()) {
            session = session.copy(components = session.components + fresh)
        }
    }

    /** Marca dano visible sobre componentes ya mapeados. */
    fun applyVisibleDamage(damages: Map<String, String>) {
        check(session.stage == Stage.MAPPING) { "El dano se marca sobre el mapeo" }
        if (damages.isEmpty()) return
        session = session.copy(
            components = session.components.map { component ->
                damages[component.id]?.let { component.copy(visibleDamage = it) }
                    ?: component
            },
        )
    }

    fun loadHypotheses(
        hypotheses: List<Hypothesis>,
        requests: List<MeasurementRequest>,
    ) {
        check(session.stage == Stage.MAPPING) { "Primero hay que mapear la placa" }
        require(hypotheses.isNotEmpty()) { "Se requiere al menos una hipotesis" }
        session = session.copy(
            hypotheses = normalize(hypotheses),
            pendingRequests = requests,
            stage = Stage.HYPOTHESES,
        )
    }

    /**
     * Mediciones nuevas (pedidas a Muse Spark cuando no vino ninguna o se
     * agotaron sin veredicto): solo sobre las hipotesis cargadas.
     */
    fun addRequests(requests: List<MeasurementRequest>) {
        check(session.stage == Stage.HYPOTHESES) { "Las mediciones nuevas se suman sobre las hipotesis" }
        val known = session.hypotheses.map { it.id }.toSet()
        val fresh = requests.filter { it.targetHypothesisId in known }
        if (fresh.isNotEmpty()) {
            session = session.copy(pendingRequests = session.pendingRequests + fresh)
        }
    }

    /** Guia de puntas sugerida para mediciones pendientes (por id); no cambia la etapa. */
    fun attachProbes(guides: Map<String, ProbeGuide>) {
        if (guides.isEmpty()) return
        session = session.copy(
            pendingRequests = session.pendingRequests.map { request ->
                guides[request.id]?.let { request.copy(probes = it) } ?: request
            },
        )
    }

    fun startMeasuring() {
        check(session.stage == Stage.HYPOTHESES) { "No hay hipotesis cargadas" }
        check(session.pendingRequests.isNotEmpty()) { "El playbook no trae mediciones" }
        session = session.copy(stage = Stage.MEASURING, safetyConfirmedForCurrent = false)
    }

    fun confirmSafety() {
        check(session.stage == Stage.MEASURING) { "No hay una medicion en curso" }
        check(session.currentRequest != null) { "No queda ninguna medicion pendiente" }
        session = session.copy(safetyConfirmedForCurrent = true)
    }

    fun submitResult(result: MeasurementResult): SubmitResult {
        val current = session.currentRequest
            ?: return SubmitResult.Rejected(RejectReason.NO_PENDING_REQUEST)
        if (result.requestId != current.id) {
            return SubmitResult.Rejected(RejectReason.WRONG_REQUEST)
        }
        if (!session.safetyConfirmedForCurrent) {
            return SubmitResult.Rejected(RejectReason.SAFETY_NOT_CONFIRMED)
        }
        val updated = applyEvidence(session.hypotheses, current, result.outcome)
        val nextStage = decideStage(updated, session.pendingRequests.drop(1))
        val leader = updated.firstOrNull()
        session = session.copy(
            hypotheses = updated,
            pendingRequests = session.pendingRequests.drop(1),
            safetyConfirmedForCurrent = false,
            // El resultado guarda que se midio y sobre que componente: para pedir las
            // siguientes mediciones y para marcarlo en el mapa de evidencia.
            results = session.results + result.copy(
                note = result.note.ifBlank { "${current.kind} ${current.componentId}: ${current.instruction}" },
                componentId = current.componentId,
            ),
            stage = nextStage,
            verdictComponentId = if (nextStage == Stage.VERDICT) leader?.componentId else null,
        )
        return SubmitResult.Advanced
    }

    fun markRepaired(replacement: String) {
        check(session.stage == Stage.VERDICT) { "Todavia no hay veredicto" }
        session = session.copy(stage = Stage.REPAIR, replacement = replacement)
    }

    fun markVerified(powersOn: Boolean) {
        check(session.stage == Stage.REPAIR) { "Primero hay que registrar la reparacion" }
        session = if (powersOn) {
            session.copy(stage = Stage.VERIFIED)
        } else {
            // No encendio: vuelve a medicion si el playbook tenia pasos, o queda en veredicto.
            session.copy(stage = Stage.VERDICT, verdictComponentId = session.verdictComponentId)
        }
    }

    private fun decideStage(
        hypotheses: List<Hypothesis>,
        remaining: List<MeasurementRequest>,
    ): Stage {
        val leader = hypotheses.firstOrNull() ?: return Stage.HYPOTHESES
        val proven = leader.confirmedByMeasurement && leader.confidence >= verdictThreshold
        return when {
            proven -> Stage.VERDICT
            remaining.isNotEmpty() -> Stage.MEASURING
            leader.confirmedByMeasurement -> Stage.VERDICT
            else -> Stage.HYPOTHESES // Sin evidencia suficiente: pedir mas (foto/mediciones).
        }
    }

    private fun applyEvidence(
        hypotheses: List<Hypothesis>,
        request: MeasurementRequest,
        outcome: MeasurementOutcome,
    ): List<Hypothesis> {
        if (outcome == MeasurementOutcome.UNCLEAR) return normalize(hypotheses)
        val adjusted = hypotheses.map { h ->
            when {
                h.id != request.targetHypothesisId -> h
                outcome == MeasurementOutcome.ABNORMAL ->
                    h.copy(confidence = h.confidence * 4.0, confirmedByMeasurement = true)
                else -> h.copy(confidence = h.confidence * 0.15)
            }
        }
        return normalize(adjusted)
    }

    private fun normalize(hypotheses: List<Hypothesis>): List<Hypothesis> {
        val total = hypotheses.sumOf { it.confidence }.takeIf { it > 0 } ?: return hypotheses
        return hypotheses
            .map { it.copy(confidence = it.confidence / total) }
            .sortedByDescending { it.confidence }
    }
}
