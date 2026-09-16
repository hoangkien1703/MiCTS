package com.parallelc.micts.trigger

enum class TriggerResult { ACCEPTED, REJECTED, CANCELLED }

/** Bounded fallback: never issue another invocation after a request has been accepted. */
object TriggerRequest {
    suspend fun run(
        initialDelayMs: Long,
        isForeground: () -> Boolean,
        wait: suspend (Long) -> Unit,
        fresh: () -> Boolean,
        legacy: () -> Boolean,
    ): TriggerResult {
        wait(initialDelayMs)
        if (!isForeground()) return TriggerResult.CANCELLED
        if (fresh()) return TriggerResult.ACCEPTED
        // Give the visible activity/system service one short settling interval after rejection.
        wait(250L)
        if (!isForeground()) return TriggerResult.CANCELLED
        return if (legacy()) TriggerResult.ACCEPTED else TriggerResult.REJECTED
    }
}
