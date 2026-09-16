package com.parallelc.micts.trigger

enum class TriggerResult { ACCEPTED, REJECTED, CANCELLED }

/** Bounded fallback: never issue another invocation after a request has been accepted. */
object TriggerRequest {
    suspend fun run(
        initialDelayMs: Long,
        isEligible: () -> Boolean,
        wait: suspend (Long) -> Unit,
        primary: () -> Boolean,
        fallback: () -> Boolean,
    ): TriggerResult {
        wait(initialDelayMs)
        if (!isEligible()) return TriggerResult.CANCELLED
        if (primary()) return TriggerResult.ACCEPTED
        // Give the system one short settling interval after a rejected request.
        wait(250L)
        if (!isEligible()) return TriggerResult.CANCELLED
        return if (fallback()) TriggerResult.ACCEPTED else TriggerResult.REJECTED
    }
}
