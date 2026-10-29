package ar.com.mt0.boardpilot.domain

import ar.com.mt0.boardpilot.demo.GoldenCaseSmps
import ar.com.mt0.boardpilot.i18n.AppLanguage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class RepairProjectTest {

    private fun project(
        id: String = "p1",
        updated: Long = 100L,
        waiting: Boolean = false,
        stage: Stage = Stage.INTAKE,
    ): RepairProject {
        val base = GoldenCaseSmps.newSession(AppLanguage.ES)
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
    fun `avanzar la sesion levanta la pausa y sin cambios la conserva`() {
        val waiting = project(waiting = true, stage = Stage.VERDICT)
        val same = waiting.withProgress(waiting.session, true, true, 500L)
        assertTrue(same.waiting)
        assertEquals("MOSFET de primario", same.waitingNote)
        assertEquals(500L, same.updatedAtMillis)

        val advanced = waiting.withProgress(
            waiting.session.copy(stage = Stage.REPAIR), true, true, 600L)
        assertFalse(advanced.waiting)
        assertNull(advanced.waitingNote)
        assertEquals(Stage.REPAIR, advanced.session.stage)
    }

    @Test
    fun `pausa con nota limpia, sin nota y reanudar`() {
        val paused = project().paused("  MOSFET   2N60 ", 700L)
        assertTrue(paused.waiting)
        assertEquals("MOSFET 2N60", paused.waitingNote)
        assertEquals(ProjectStatus.WAITING, projectStatus(paused))
        assertNull(project().paused("   ", 700L).waitingNote)

        val resumed = paused.resumed(800L)
        assertFalse(resumed.waiting)
        assertNull(resumed.waitingNote)
        assertEquals(800L, resumed.updatedAtMillis)
    }

    @Test
    fun `renombrar ignora vacio y normaliza espacios`() {
        val p = project()
        assertSame(p, p.renamed("   ", 900L))
        val renamed = p.renamed("  Cargador   del taller ", 900L)
        assertEquals("Cargador del taller", renamed.title)
        assertEquals(900L, renamed.updatedAtMillis)
    }

    @Test
    fun `titulo automatico solo reemplaza el titulo por defecto`() {
        val defaults = setOf("Nueva reparación", "New repair")
        val fresh = project().let {
            it.copy(title = "New repair", session = it.session.copy(symptom = "Fuente USB muerta"))
        }
        assertEquals("Fuente USB muerta", fresh.withAutoTitle(defaults).title)
        val named = project().copy(title = "Cargador del taller")
        assertEquals("Cargador del taller", named.withAutoTitle(defaults).title)
    }

    @Test
    fun `proyecto sin tocar se descarta y cualquier avance lo conserva`() {
        val defaults = setOf("Nueva reparación", "New repair")
        val fresh = project(updated = 50L).copy(title = "New repair", started = false)
        assertTrue(fresh.isUntouched(defaults))
        assertFalse(fresh.copy(updatedAtMillis = 60L).isUntouched(defaults)) // foto nueva
        assertFalse(fresh.renamed("Fuente del taller", 60L).isUntouched(defaults))
        assertFalse(fresh.copy(started = true).isUntouched(defaults))
    }

    @Test
    fun `migracion de la sesion unica anterior`() {
        val session = GoldenCaseSmps.newSession(AppLanguage.ES).copy(
            symptom = "Fuente USB muerta", stage = Stage.HYPOTHESES)
        val text = SessionCodec.encode(session)
        val fresh = GoldenCaseSmps.newSession(AppLanguage.ES)
        fun migrate(
            sessionText: String?, started: Boolean, hasPhoto: Boolean,
        ) = legacyProject(
            sessionText, started, liveMapping = true, referenceComponentId = "dmg1-spark",
            hasPhoto = hasPhoto, freshSession = fresh, id = "p1", nowMillis = 42L,
            fallbackTitle = "Nueva reparación",
        )

        val migrated = migrate(text, started = true, hasPhoto = true)!!
        assertEquals("Fuente USB muerta", migrated.title)
        assertEquals(session, migrated.session)
        assertTrue(migrated.started && migrated.liveMapping)
        assertEquals("dmg1-spark", migrated.referenceComponentId)

        // Solo quedaba la foto (sesion sin iniciar, o ilegible): se
        // conserva sin iniciar y sin la referencia de un veredicto viejo.
        listOf(
            migrate(null, started = false, hasPhoto = true)!!,
            migrate(text, started = false, hasPhoto = true)!!,
            migrate("BASURA", started = true, hasPhoto = true)!!,
        ).forEach { photoOnly ->
            assertEquals("Nueva reparación", photoOnly.title)
            assertEquals(fresh, photoOnly.session)
            assertFalse(photoOnly.started || photoOnly.liveMapping)
            assertNull(photoOnly.referenceComponentId)
        }
        // Sin diagnostico retomable ni foto: no se crea proyecto.
        assertNull(migrate(text, started = false, hasPhoto = false))
        assertNull(migrate("BASURA", started = true, hasPhoto = false))
        assertNull(migrate(null, started = true, hasPhoto = false))
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

    @Test
    fun `un proyecto mapeado con el demo vuelve al ingreso y uno real queda igual`() {
        val fresh = newDiagnosisSession()
        val demo = project(waiting = true, stage = Stage.MEASURING).copy(liveMapping = false)
        val reset = demo.withoutDemoSession(fresh)
        assertEquals(fresh, reset.session)
        assertFalse(reset.started || reset.waiting)
        assertNull(reset.referenceComponentId)
        assertEquals(listOf(demo.id, demo.title), listOf(reset.id, reset.title))
        assertEquals(demo.updatedAtMillis, reset.updatedAtMillis)

        // Mapeo real, o iniciado pero sin mapear todavia: no se tocan.
        listOf(
            project(stage = Stage.MEASURING),
            project(stage = Stage.INTAKE).copy(liveMapping = false),
            project(stage = Stage.MAPPING).copy(started = false, liveMapping = false),
        ).forEach { assertSame(it, it.withoutDemoSession(fresh)) }
    }
}
