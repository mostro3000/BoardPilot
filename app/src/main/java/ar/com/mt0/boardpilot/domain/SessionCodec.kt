package ar.com.mt0.boardpilot.domain

import java.util.Base64

/**
 * Serializa una [DiagnosisSession] a texto compacto para persistirla en el
 * dispositivo (rotacion, muerte del proceso o cierre de la app). Los textos
 * libres van en Base64 URL sin relleno; `~` representa null.
 */
object SessionCodec {
    private const val VERSION = "BP1"
    private const val NULL_TOKEN = "~"

    fun encode(session: DiagnosisSession): String = buildString {
        line(VERSION)
        line(session.boardType.name)
        line(session.stage.name)
        line(enc(session.symptom))
        line(if (session.safetyConfirmedForCurrent) "1" else "0")
        line(encNullable(session.verdictComponentId))
        line(encNullable(session.replacement))

        line(session.components.size.toString())
        session.components.forEach { component ->
            line(
                listOf(
                    enc(component.id), enc(component.designator), component.kind.name,
                    component.box.x.toString(), component.box.y.toString(),
                    component.box.w.toString(), component.box.h.toString(),
                    encNullable(component.visibleDamage),
                ).joinToString("|"),
            )
        }

        line(session.hypotheses.size.toString())
        session.hypotheses.forEach { hypothesis ->
            line(
                listOf(
                    enc(hypothesis.id), enc(hypothesis.title), enc(hypothesis.componentId),
                    hypothesis.confidence.toString(), enc(hypothesis.rationale),
                    if (hypothesis.confirmedByMeasurement) "1" else "0",
                ).joinToString("|"),
            )
        }

        line(session.pendingRequests.size.toString())
        session.pendingRequests.forEach { request ->
            line(
                listOf(
                    enc(request.id), enc(request.componentId), request.kind.name,
                    enc(request.instruction), enc(request.normalDescription),
                    enc(request.abnormalDescription), request.gate.name,
                    enc(request.targetHypothesisId),
                ).joinToString("|"),
            )
        }

        line(session.results.size.toString())
        session.results.forEach { result ->
            line(listOf(enc(result.requestId), result.outcome.name, enc(result.note))
                .joinToString("|"))
        }
    }.trimEnd()

    fun decode(text: String): DiagnosisSession {
        val cursor = Cursor(text.lines())
        require(cursor.next() == VERSION) { "version de sesion desconocida" }
        val boardType = BoardType.valueOf(cursor.next())
        val stage = Stage.valueOf(cursor.next())
        val symptom = dec(cursor.next())
        val safetyConfirmed = cursor.next() == "1"
        val verdict = decNullable(cursor.next())
        val replacement = decNullable(cursor.next())

        val components = List(cursor.nextCount()) {
            val fields = cursor.nextFields(8)
            BoardComponent(
                id = dec(fields[0]),
                designator = dec(fields[1]),
                kind = ComponentKind.valueOf(fields[2]),
                box = NormBox(
                    fields[3].toFloat(), fields[4].toFloat(),
                    fields[5].toFloat(), fields[6].toFloat(),
                ),
                visibleDamage = decNullable(fields[7]),
            )
        }
        val hypotheses = List(cursor.nextCount()) {
            val fields = cursor.nextFields(6)
            Hypothesis(
                id = dec(fields[0]),
                title = dec(fields[1]),
                componentId = dec(fields[2]),
                confidence = fields[3].toDouble(),
                rationale = dec(fields[4]),
                confirmedByMeasurement = fields[5] == "1",
            )
        }
        val requests = List(cursor.nextCount()) {
            val fields = cursor.nextFields(8)
            MeasurementRequest(
                id = dec(fields[0]),
                componentId = dec(fields[1]),
                kind = MeasurementKind.valueOf(fields[2]),
                instruction = dec(fields[3]),
                normalDescription = dec(fields[4]),
                abnormalDescription = dec(fields[5]),
                gate = SafetyGate.valueOf(fields[6]),
                targetHypothesisId = dec(fields[7]),
            )
        }
        val results = List(cursor.nextCount()) {
            val fields = cursor.nextFields(3)
            MeasurementResult(
                requestId = dec(fields[0]),
                outcome = MeasurementOutcome.valueOf(fields[1]),
                note = dec(fields[2]),
            )
        }
        return DiagnosisSession(
            boardType = boardType,
            symptom = symptom,
            components = components,
            hypotheses = hypotheses,
            pendingRequests = requests,
            safetyConfirmedForCurrent = safetyConfirmed,
            results = results,
            stage = stage,
            verdictComponentId = verdict,
            replacement = replacement,
        )
    }

    private fun StringBuilder.line(value: String) {
        append(value).append('\n')
    }

    private fun enc(value: String): String =
        Base64.getUrlEncoder().withoutPadding()
            .encodeToString(value.toByteArray(Charsets.UTF_8))

    private fun dec(value: String): String =
        String(Base64.getUrlDecoder().decode(value), Charsets.UTF_8)

    private fun encNullable(value: String?): String = value?.let(::enc) ?: NULL_TOKEN

    private fun decNullable(value: String): String? =
        if (value == NULL_TOKEN) null else dec(value)

    private class Cursor(private val lines: List<String>) {
        private var index = 0

        fun next(): String {
            require(index < lines.size) { "sesion truncada" }
            return lines[index++]
        }

        fun nextCount(): Int = next().toInt().also {
            require(it in 0..10_000) { "cantidad fuera de rango" }
        }

        fun nextFields(expected: Int): List<String> = next().split("|").also {
            require(it.size == expected) { "registro invalido" }
        }
    }
}
