package com.parallelc.micts.trigger

/** Main-thread-only gate for duplicate destruction callbacks, stale taps, and screen locking. */
class SidebarRequestGate {
    private var generation = 0L
    private var claimed = false

    fun reserve(): Long {
        claimed = false
        return ++generation
    }

    fun claim(ticket: Long): Boolean {
        if (ticket != generation || claimed) return false
        claimed = true
        return true
    }

    fun canRun(ticket: Long, ageMs: Long, interactive: Boolean, locked: Boolean): Boolean =
        ticket == generation && claimed && ageMs in 0L..5000L && interactive && !locked
}
