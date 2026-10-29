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
                    if (hypothesis.confirmedByMeasurement) "1" else "0", enc(hypothesis.fix),
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
                    enc(request.targetHypothesisId), encProbes(request.probes), request.zone.name,
                ).joinToString("|"),
            )
        }

        line(session.results.size.toString())
        session.results.forEach { result ->
            line(
                listOf(
                    enc(result.requestId), result.outcome.name, enc(result.note),
                    enc(result.componentId),
                ).joinToString("|"),
            )
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
            // Las sesiones guardadas antes de "fix" traen 6 campos.
            val fields = cursor.nextFields(6, 7)
            Hypothesis(
                id = dec(fields[0]),
                title = dec(fields[1]),
                componentId = dec(fields[2]),
                confidence = fields[3].toDouble(),
                rationale = dec(fields[4]),
                confirmedByMeasurement = fields[5] == "1",
                fix = fields.getOrNull(6)?.let(::dec).orEmpty(),
            )
        }
        val requests = List(cursor.nextCount()) {
            // Antes de la guia de puntas: 8 campos; antes de la zona de riesgo: 9.
            val fields = cursor.nextFields(8, 9, 10)
            MeasurementRequest(
                id = dec(fields[0]),
                componentId = dec(fields[1]),
                kind = MeasurementKind.valueOf(fields[2]),
                instruction = dec(fields[3]),
                normalDescription = dec(fields[4]),
                abnormalDescription = dec(fields[5]),
                gate = SafetyGate.valueOf(fields[6]),
                targetHypothesisId = dec(fields[7]),
                probes = fields.getOrNull(8)?.let(::decProbes),
                // Sin zona guardada, la mas estricta.
                zone = fields.getOrNull(9)?.let(HazardZone::valueOf) ?: HazardZone.PRIMARY,
            )
        }
        val results = List(cursor.nextCount()) {
            // Y los resultados, 3 (sin el componente medido).
            val fields = cursor.nextFields(3, 4)
            MeasurementResult(
                requestId = dec(fields[0]),
                outcome = MeasurementOutcome.valueOf(fields[1]),
                note = dec(fields[2]),
                componentId = fields.getOrNull(3)?.let(::dec).orEmpty(),
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

    /**
     * Guia de puntas en un solo campo: "roja;negra;nota" dentro de Base64, cada
     * punta "componente,x,y,terminal" (o `~`). Base64 URL no usa `,`, `;` ni `~`.
     */
    private fun encProbes(guide: ProbeGuide?): String {
        guide ?: return NULL_TOKEN
        fun point(p: ProbePoint?): String = p?.let {
            listOf(enc(it.componentId), it.x.toString(), it.y.toString(), enc(it.terminal))
                .joinToString(",")
        } ?: NULL_TOKEN
        return enc(listOf(point(guide.red), point(guide.black), enc(guide.note)).joinToString(";"))
    }

    private fun decProbes(value: String): ProbeGuide? {
        if (value == NULL_TOKEN) return null
        val parts = dec(value).split(";")
        require(parts.size == 3) { "guia de puntas invalida" }
        fun point(text: String): ProbePoint? = if (text == NULL_TOKEN) null else {
            val f = text.split(",")
            require(f.size == 4) { "punta invalida" }
            ProbePoint(dec(f[0]), f[1].toFloat(), f[2].toFloat(), dec(f[3]))
        }
        return ProbeGuide(point(parts[0]), point(parts[1]), dec(parts[2]))
    }

    private class Cursor(private val lines: List<String>) {
        private var index = 0

        fun next(): String {
            require(index < lines.size) { "sesion truncada" }
            return lines[index++]
        }

        fun nextCount(): Int = next().toInt().also {
            require(it in 0..10_000) { "cantidad fuera de rango" }
        }

        fun nextFields(vararg allowed: Int): List<String> = next().split("|").also {
            require(it.size in allowed) { "registro invalido" }
        }
    }
}
