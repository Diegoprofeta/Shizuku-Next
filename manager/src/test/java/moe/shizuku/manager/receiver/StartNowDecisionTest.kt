package moe.shizuku.manager.receiver

import org.junit.Assert.assertEquals
import org.junit.Test

/** When an unattended start may be asked for; see [decideStartNow]. */
class StartNowDecisionTest {

    @Test
    fun `an unlocked phone with a wanted start goes ahead`() {
        assertEquals(
            StartNowDecision.START,
            decideStartNow(userUnlocked = true, startWanted = true, manuallyStopped = false)
        )
    }

    @Test
    fun `a locked phone waits for the unlock`() {
        assertEquals(
            StartNowDecision.DEFER_UNTIL_UNLOCK,
            decideStartNow(userUnlocked = false, startWanted = true, manuallyStopped = false)
        )
    }

    @Test
    fun `a start nobody wanted is skipped whether or not the phone is unlocked`() {
        assertEquals(
            StartNowDecision.SKIP_NOT_WANTED,
            decideStartNow(userUnlocked = true, startWanted = false, manuallyStopped = false)
        )
        assertEquals(
            StartNowDecision.SKIP_NOT_WANTED,
            decideStartNow(userUnlocked = false, startWanted = false, manuallyStopped = false)
        )
    }

    @Test
    fun `a deliberate stop beats everything else`() {
        for (unlocked in listOf(true, false)) {
            for (wanted in listOf(true, false)) {
                assertEquals(
                    StartNowDecision.SKIP_STOPPED,
                    decideStartNow(unlocked, wanted, manuallyStopped = true)
                )
            }
        }
    }
}
