package ar.com.mt0.boardpilot.domain

import ar.com.mt0.boardpilot.demo.DemoCaseSmps
import ar.com.mt0.boardpilot.i18n.AppLanguage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RepairProjectTest {

    private fun project(
        id: String = "p1",
        updated: Long = 100L,
        waiting: Boolean = false,
        stage: Stage = Stage.INTAKE,
    ): RepairProject {
        val base = DemoCaseSmps.newSession(AppLanguage.ES)
        return RepairProject(
            id = id,
            title = "Cargador 18650",
            createdAtMillis = 50L,
            updatedAtMillis = updated,
            waiting = waiting,
            waitingNote = if (waiting) "MOSFET de primario" else null,
            started = true,
            liveMapping = true,
            referenceComponentId = "dmg1-spark",
            session = base.copy(stage = stage),
        )
    }

    @Test
    fun `codec hace round-trip de varios proyectos con su sesion`() {
        val projects = listOf(
            project("p1", updated = 300L, waiting = true, stage = Stage.REPAIR),
            project("p2", updated = 200L),
        )
        val decoded = ProjectCodec.decode(ProjectCodec.encode(projects))
        assertEquals(projects, decoded)
    }

    @Test
    fun `codec tolera encabezado desconocido y entradas danadas`() {
        assertTrue(ProjectCodec.decode("").isEmpty())
        assertTrue(ProjectCodec.decode("OTRO\n1\nx").isEmpty())
        val good = ProjectCodec.encode(listOf(project("p1")))
        val mixed = good.replaceFirst("\n1\n", "\n2\n") + "\nbasura|rota"
        assertEquals(1, ProjectCodec.decode(mixed).size)
    }

    @Test
    fun `estado derivado verificada pisa espera y espera pisa curso`() {
        assertEquals(ProjectStatus.IN_PROGRESS, projectStatus(project()))
        assertEquals(
            ProjectStatus.WAITING,
            projectStatus(project(waiting = true, stage = Stage.REPAIR)),
        )
        assertEquals(
            ProjectStatus.REPAIRED,
            projectStatus(project(waiting = true, stage = Stage.VERIFIED)),
        )
    }

    @Test
    fun `titulo automatico recorta y cae al fallback`() {
        assertEquals(
            "Cargador 18650 no enciende, sin LED",
            autoProjectTitle("  Cargador 18650  no enciende,  sin LED ", "X"),
        )
        assertEquals("X", autoProjectTitle("   ", "X"))
        val long = autoProjectTitle("a".repeat(80), "X")
        assertEquals(48, long.length)
        assertTrue(long.endsWith("…"))
    }

    @Test
    fun `orden por actualizacion descendente`() {
        val sorted = sortedProjects(listOf(
            project("viejo", updated = 100L),
            project("nuevo", updated = 300L),
            project("medio", updated = 200L),
        ))
        assertEquals(listOf("nuevo", "medio", "viejo"), sorted.map { it.id })
    }
}
