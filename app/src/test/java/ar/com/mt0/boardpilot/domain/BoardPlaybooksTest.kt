package ar.com.mt0.boardpilot.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BoardPlaybooksTest {

    @Test
    fun `cada tipo detectable tiene su guia y UNKNOWN no`() {
        assertTrue(BoardPlaybooks.version.isNotBlank())
        assertFalse(BoardType.UNKNOWN in BoardPlaybooks.detectable)
        BoardPlaybooks.detectable.forEach { type ->
            assertTrue("$type sin guia", BoardPlaybooks.guide(type).length > 60)
        }
        assertEquals("", BoardPlaybooks.guide(BoardType.UNKNOWN))
    }

    @Test
    fun `sin tipo elegido el prompt pide detectarlo entre todos`() {
        val detect = BoardPlaybooks.promptSection(BoardType.UNKNOWN)
        assertTrue(detect.startsWith("- FIRST identify"))
        BoardPlaybooks.detectable.forEach { assertTrue("falta $it", "  - ${it.name}: " in detect) }
        // Con el tipo elegido, solo su guia.
        val tv = BoardPlaybooks.promptSection(BoardType.TV_MONITOR)
        assertTrue("TV_MONITOR" in tv && BoardPlaybooks.guide(BoardType.TV_MONITOR) in tv)
        assertFalse(BoardPlaybooks.guide(BoardType.SMPS) in tv)
    }
}
