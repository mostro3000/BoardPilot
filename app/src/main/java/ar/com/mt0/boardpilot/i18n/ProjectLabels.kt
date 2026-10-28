package ar.com.mt0.boardpilot.i18n

import ar.com.mt0.boardpilot.domain.ProjectStatus
import ar.com.mt0.boardpilot.domain.RepairProject
import ar.com.mt0.boardpilot.domain.projectStatus
import java.time.Instant
import java.time.ZoneId

/**
 * Cuándo se tocó un proyecto, como en una lista de chats: "recién",
 * "hace 5 min", "hace 2 h" (mismo día), "ayer" y, más atrás, la fecha
 * (con año solo si no es el actual). Un reloj adelantado cuenta como recién.
 */
fun relativeTime(
    nowMillis: Long,
    thenMillis: Long,
    zone: ZoneId,
    strings: AppStrings,
): String {
    val minutes = (nowMillis - thenMillis).coerceAtLeast(0L) / 60_000L
    val today = Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate()
    val day = Instant.ofEpochMilli(thenMillis).atZone(zone).toLocalDate()
    return when {
        minutes < 1 -> strings.justNow
        minutes < 60 -> strings.minutesAgo(minutes)
        day == today -> strings.hoursAgo(minutes / 60)
        day == today.minusDays(1) -> strings.yesterday
        else -> strings.shortDate(
            day.dayOfMonth, day.monthValue, day.year.takeIf { it != today.year },
        )
    }
}

/**
 * Renglón de estado bajo el título en la lista, p. ej.
 * "⏸ En espera · falta: MOSFET — hace 2 h" o "▶ En curso · Veredicto — ayer".
 */
fun projectSubtitle(project: RepairProject, strings: AppStrings, whenText: String): String {
    val detail = when (projectStatus(project)) {
        ProjectStatus.REPAIRED -> strings.statusRepaired
        ProjectStatus.WAITING -> strings.statusWaiting +
            (project.waitingNote?.let { " · " + strings.missingPart(it) } ?: "")
        ProjectStatus.IN_PROGRESS ->
            strings.statusInProgress + " · " + strings.stageName(project.session.stage)
    }
    return "$detail — $whenText"
}
