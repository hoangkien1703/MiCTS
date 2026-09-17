package com.parallelc.micts.trigger

import kotlinx.coroutines.withTimeoutOrNull

/** A short-lived connection; a connection is NOT proof that the assistant UI is ready. */
interface AssistantConnection : AutoCloseable {
    fun bind(): Boolean
    suspend fun awaitConnected(): Boolean
}

object ConnectionWarmup {
    suspend fun run(
        connection: AssistantConnection,
        isEligible: () -> Boolean,
        wait: suspend (Long) -> Unit,
        record: (String) -> Unit,
        trigger: suspend () -> TriggerResult,
        timeoutMs: Long = 1500L,
    ): TriggerResult {
        try {
            if (!isEligible()) return TriggerResult.CANCELLED
            val connected = if (connection.bind()) {
                withTimeoutOrNull(timeoutMs) { connection.awaitConnected() } == true
            } else false
            record(if (connected) "Google preparation connected" else
                "Google preparation unavailable or timed out; using normal trigger")
            if (connected) wait(200L)
            if (!isEligible()) return TriggerResult.CANCELLED
            val result = trigger()
            // Keep the connection briefly while Google handles the accepted request.
            if (connected && result == TriggerResult.ACCEPTED) wait(500L)
            return result
        } finally {
            connection.close()
        }
    }
}
