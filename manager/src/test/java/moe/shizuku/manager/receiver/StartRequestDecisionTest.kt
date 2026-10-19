package moe.shizuku.manager.receiver

import org.junit.Assert.assertEquals
import org.junit.Test

/** Who may start while another start is under way; see [decideStart]. */
class StartRequestDecisionTest {

    @Test
    fun `a plain start during another one joins it`() {
        assertEquals(
            StartDecision.JOIN_IN_FLIGHT,
            decideStart(binderAlive = false, forceStart = false, userInitiated = false, startInFlight = true)
        )
    }

    @Test
    fun `a forced start nobody tapped joins it too`() {
        assertEquals(
            StartDecision.JOIN_IN_FLIGHT,
            decideStart(binderAlive = false, forceStart = true, userInitiated = false, startInFlight = true)
        )
    }

    @Test
    fun `a restart somebody tapped replaces it`() {
        assertEquals(
            StartDecision.START,
            decideStart(binderAlive = false, forceStart = true, userInitiated = true, startInFlight = true)
        )
    }

    @Test
    fun `a running server ignores a plain start`() {
        assertEquals(
            StartDecision.IGNORE_RUNNING,
            decideStart(binderAlive = true, forceStart = false, userInitiated = true, startInFlight = true)
        )
    }

    @Test
    fun `a start with nothing in flight goes ahead`() {
        assertEquals(
            StartDecision.START,
            decideStart(binderAlive = false, forceStart = false, userInitiated = false, startInFlight = false)
        )
        assertEquals(
            StartDecision.START,
            decideStart(binderAlive = true, forceStart = true, userInitiated = false, startInFlight = false)
        )
    }
}
