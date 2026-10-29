package ar.com.mt0.boardpilot.i18n

import ar.com.mt0.boardpilot.demo.GoldenCaseSmps
import ar.com.mt0.boardpilot.domain.RepairProject
import ar.com.mt0.boardpilot.domain.Stage
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class ProjectLabelsTest {

    private val zone = ZoneId.of("America/Argentina/Buenos_Aires")
    private val now = millis(2026, 10, 28, 12, 30)

    private fun millis(y: Int, m: Int, d: Int, h: Int, min: Int): Long =
        ZonedDateTime.of(y, m, d, h, min, 0, 0, zone).toInstant().toEpochMilli()

    private fun es(then: Long) = relativeTime(now, then, zone, EsStrings)
    private fun en(then: Long) = relativeTime(now, then, zone, EnStrings)

    @Test
    fun `tiempo relativo como lista de chats`() {
        assertEquals("recién", es(now - 20_000L))
        assertEquals("recién", es(now + 3_600_000L)) // reloj adelantado
        assertEquals("hace 5 min", es(millis(2026, 10, 28, 12, 25)))
        assertEquals("hace 2 h", es(millis(2026, 10, 28, 10, 15)))
        assertEquals("ayer", es(millis(2026, 10, 27, 23, 50)))
        assertEquals("12 oct", es(millis(2026, 10, 12, 9, 0)))
        assertEquals("3 dic 2025", es(millis(2025, 12, 3, 9, 0)))
    }

    @Test
    fun `tiempo relativo en ingles`() {
        assertEquals("just now", en(now))
        assertEquals("5 min ago", en(millis(2026, 10, 28, 12, 25)))
        assertEquals("2 h ago", en(millis(2026, 10, 28, 10, 15)))
        assertEquals("yesterday", en(millis(2026, 10, 27, 8, 0)))
        assertEquals("Oct 12", en(millis(2026, 10, 12, 9, 0)))
        assertEquals("Dec 3, 2025", en(millis(2025, 12, 3, 9, 0)))
    }

    @Test
    fun `renglon de estado por proyecto`() {
        val base = RepairProject(
            id = "p1", title = "Cargador", createdAtMillis = 0L, updatedAtMillis = 0L,
            started = true, session = GoldenCaseSmps.newSession(AppLanguage.ES)
                .copy(stage = Stage.MEASURING),
        )
        assertEquals(
            "▶ En curso · Mediciones guiadas — ayer",
            projectSubtitle(base, EsStrings, "ayer"),
        )
        assertEquals(
            "⏸ En espera · falta: MOSFET — hace 2 h",
            projectSubtitle(base.copy(waiting = true, waitingNote = "MOSFET"),
                EsStrings, "hace 2 h"),
        )
        assertEquals(
            "⏸ Waiting — Oct 12",
            projectSubtitle(base.copy(waiting = true), EnStrings, "Oct 12"),
        )
        assertEquals(
            "✅ Reparada — 12 oct",
            projectSubtitle(base.copy(session = base.session.copy(stage = Stage.VERIFIED)),
                EsStrings, "12 oct"),
        )
    }
}
