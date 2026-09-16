package com.parallelc.micts.trigger

import org.junit.Assert.*
import org.junit.Test

class SidebarRequestGateTest {
    @Test fun cannotTriggerBeforeLauncherIsDestroyedAndClaimed() {
        val gate = SidebarRequestGate()
        val ticket = gate.reserve()
        assertFalse(gate.canRun(ticket, 250L, true, false))
        assertTrue(gate.claim(ticket))
        assertTrue(gate.canRun(ticket, 250L, true, false))
    }

    @Test fun duplicateDestructionCannotScheduleTwoRequests() {
        val gate = SidebarRequestGate()
        val ticket = gate.reserve()
        assertTrue(gate.claim(ticket))
        assertFalse(gate.claim(ticket))
    }

    @Test fun newerTapInvalidatesPendingAndLateCallbacks() {
        val gate = SidebarRequestGate()
        val old = gate.reserve()
        gate.claim(old)
        val latest = gate.reserve()
        assertFalse(gate.claim(old))
        assertFalse(gate.canRun(old, 300L, true, false))
        assertTrue(gate.claim(latest))
        assertTrue(gate.canRun(latest, 300L, true, false))
    }

    @Test fun noDelayedTriggerAfterLockOrScreenOff() {
        val gate = SidebarRequestGate()
        val ticket = gate.reserve()
        gate.claim(ticket)
        assertFalse(gate.canRun(ticket, 300L, true, true))
        assertFalse(gate.canRun(ticket, 300L, false, false))
    }

    @Test fun frozenOrStaleRequestIsDiscarded() {
        val gate = SidebarRequestGate()
        val ticket = gate.reserve()
        gate.claim(ticket)
        assertTrue(gate.canRun(ticket, 3000L, true, false))
        assertFalse(gate.canRun(ticket, 3001L, true, false))
        assertFalse(gate.canRun(ticket, -1L, true, false))
    }
}
