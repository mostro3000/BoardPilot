package ar.com.mt0.boardpilot.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReasoningEffortTest {

    @Test
    fun `cada nivel mapea a su valor de API y auto lo omite`() {
        assertNull(ReasoningEffort.AUTO.apiValue)
        assertEquals("minimal", ReasoningEffort.MINIMAL.apiValue)
        assertEquals("low", ReasoningEffort.LOW.apiValue)
        assertEquals("medium", ReasoningEffort.MEDIUM.apiValue)
        assertEquals("high", ReasoningEffort.HIGH.apiValue)
        assertEquals("xhigh", ReasoningEffort.XHIGH.apiValue)
    }

    @Test
    fun `desde lo guardado con fallback a auto`() {
        assertEquals(ReasoningEffort.LOW, ReasoningEffort.fromStored("LOW"))
        assertEquals(ReasoningEffort.AUTO, ReasoningEffort.fromStored(null))
        assertEquals(ReasoningEffort.AUTO, ReasoningEffort.fromStored("turbo"))
        assertEquals(ReasoningEffort.AUTO, ReasoningEffort.DEFAULT)
    }
}
