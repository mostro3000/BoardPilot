package ar.com.mt0.boardpilot.domain

/**
 * Maquina de estados del diagnostico. Logica pura, deterministica y testeable.
 *
 * Reglas centrales:
 * - Nunca se registra una medicion sin confirmar antes su compuerta de seguridad.
 * - Las mediciones con tension solo se ofrecen cuando la placa se puede enchufar
 *   ([safeMeasurementQueue]), tambien en sesiones guardadas por versiones anteriores.
 * - Un veredicto exige evidencia de medicion real que confirme la hipotesis lider
 *   y confianza >= umbral; si la evidencia no alcanza, se pide mas evidencia. La
 *   unica excepcion la decide el tecnico: una pieza con dano a la vista.
 * - Las confianzas son probabilidades de causas que compiten: lo que falta para
 *   sumar 1 es la chance de otra causa, que crece cuando se descartan hipotesis.
 */
private const val ABNORMAL_FACTOR = 4.0
private const val NORMAL_FACTOR = 0.15

/** Piso de una hipotesis con una lectura anormal o con dano a la vista: pesa mas que lo estimado antes. */
private const val EVIDENCE_FLOOR = 0.6

class DiagnosisEngine(
    initial: DiagnosisSession,
    private val verdictThreshold: Double = 0.80,
) {
    var session: DiagnosisSession = initial.withSafeQueue()
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
            pendingRequests = safeMeasurementQueue(requests, session.components, session.results),
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
        session = session.copy(
            pendingRequests = safeMeasurementQueue(
                session.pendingRequests + fresh, session.components, session.results,
            ),
        )
    }

    /**
     * Lo que responde Muse Spark al seguir analizando: causas nuevas si las anteriores
     * quedaron descartadas, dano que recien ahora se ve y las siguientes mediciones. El
     * dano visible vuelve a poner en juego la hipotesis de esa pieza.
     */
    fun applyPlan(plan: SparkPlan) {
        check(session.stage == Stage.HYPOTHESES) { "El plan se aplica sobre las hipotesis" }
        val known = session.hypotheses.map { it.id }.toSet()
        val merged = (session.hypotheses + plan.hypotheses.filterNot { it.id in known }).map { h ->
            if (h.componentId in plan.damages) {
                h.copy(confidence = maxOf(h.confidence, EVIDENCE_FLOOR), ruledOut = false)
            } else {
                h
            }
        }
        session = session.copy(
            hypotheses = normalize(merged),
            components = session.components.map { component ->
                plan.damages[component.id]?.let { component.copy(visibleDamage = it) } ?: component
            },
        )
        addRequests(plan.requests)
    }

    /**
     * Veredicto sin lectura anormal, a pedido del tecnico: la pieza tiene dano a la
     * vista y el multimetro no siempre lo confirma (un capacitor seco mide normal).
     */
    fun acceptVisibleDamage(componentId: String) {
        check(session.stage == Stage.HYPOTHESES) { "Se decide sobre las hipotesis" }
        require(session.components.any { it.id == componentId && !it.visibleDamage.isNullOrBlank() }) {
            "La pieza no tiene dano visible"
        }
        session = session.copy(stage = Stage.VERDICT, verdictComponentId = componentId)
    }

    /**
     * Tipo de placa: el que elige el tecnico al ingreso o el que detecta Muse
     * Spark antes de cargar las hipotesis.
     */
    fun updateBoardType(type: BoardType) {
        check(session.stage == Stage.INTAKE || session.stage == Stage.MAPPING) {
            "El tipo de placa se fija antes de las hipotesis"
        }
        session = session.copy(boardType = type)
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
        // El resultado guarda que se midio, sobre que componente y con que compuerta:
        // para pedir las siguientes mediciones, para marcarlo en el mapa de evidencia
        // y para saber si la placa ya se puede enchufar.
        val results = session.results + result.copy(
            note = result.note.ifBlank { "${current.kind} ${current.componentId}: ${current.instruction}" },
            componentId = current.componentId,
            gate = current.gate,
        )
        val remaining = safeMeasurementQueue(session.pendingRequests.drop(1), session.components, results)
        val nextStage = decideStage(updated, remaining)
        val leader = updated.firstOrNull()
        session = session.copy(
            hypotheses = updated,
            pendingRequests = remaining,
            safetyConfirmedForCurrent = false,
            results = results,
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

    /** Sesion cargada con mediciones de antes de esta regla: la compuerta se reconfirma si cambia la actual. */
    private fun DiagnosisSession.withSafeQueue(): DiagnosisSession {
        val queue = safeMeasurementQueue(pendingRequests, components, results)
        if (queue == pendingRequests) return this
        return copy(
            pendingRequests = queue,
            safetyConfirmedForCurrent = safetyConfirmedForCurrent && queue.firstOrNull() == currentRequest,
            stage = if (stage == Stage.MEASURING && queue.isEmpty()) Stage.HYPOTHESES else stage,
        )
    }

    private fun decideStage(
        hypotheses: List<Hypothesis>,
        remaining: List<MeasurementRequest>,
    ): Stage {
        val leader = hypotheses.firstOrNull() ?: return Stage.HYPOTHESES
        // Entre las hipotesis: "otra causa" no compite con una lectura anormal.
        val share = leader.confidence / hypotheses.sumOf { it.confidence }.coerceAtLeast(1e-9)
        val proven = leader.confirmedByMeasurement && share >= verdictThreshold
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
                outcome == MeasurementOutcome.ABNORMAL -> h.copy(
                    confidence = maxOf(h.confidence * ABNORMAL_FACTOR, EVIDENCE_FLOOR),
                    confirmedByMeasurement = true,
                    ruledOut = false,
                )
                else -> h.copy(
                    confidence = h.confidence * NORMAL_FACTOR,
                    ruledOut = !h.confirmedByMeasurement,
                )
            }
        }
        return normalize(adjusted)
    }

    /** De mayor a menor; solo se achican si suman mas de 1 (el modelo no siempre respeta eso). */
    private fun normalize(hypotheses: List<Hypothesis>): List<Hypothesis> {
        val total = hypotheses.sumOf { it.confidence }
        val scaled = if (total > 1.0) {
            hypotheses.map { it.copy(confidence = it.confidence / total) }
        } else {
            hypotheses
        }
        return scaled.sortedByDescending { it.confidence }
    }
}
