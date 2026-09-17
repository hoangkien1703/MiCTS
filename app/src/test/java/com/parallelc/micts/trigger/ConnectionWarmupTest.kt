package com.parallelc.micts.trigger

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class ConnectionWarmupTest {
    private class FakeConnection(
        val events: MutableList<String>,
        val bound: Boolean = true,
        val connect: suspend () -> Boolean = { true },
    ) : AssistantConnection {
        override fun bind(): Boolean { events += "bind"; return bound }
        override suspend fun awaitConnected(): Boolean { events += "connect"; return connect() }
        override fun close() { events += "close" }
    }

    @Test fun connectsBeforeTriggerAndReleasesAfterAcceptedHandoff() = runBlocking {
        val events = mutableListOf<String>()
        val result = ConnectionWarmup.run(FakeConnection(events), { true },
            { events += "wait:$it" }, {}, { events += "trigger"; TriggerResult.ACCEPTED })
        assertEquals(TriggerResult.ACCEPTED, result)
        assertEquals(listOf("bind", "connect", "wait:200", "trigger", "wait:500", "close"), events)
    }

    @Test fun unavailableServiceStillTriggersOnceAndCleansUp() = runBlocking {
        val events = mutableListOf<String>()
        val result = ConnectionWarmup.run(FakeConnection(events, bound = false), { true },
            { error("No delay for unavailable service") }, {},
            { events += "trigger"; TriggerResult.ACCEPTED })
        assertEquals(TriggerResult.ACCEPTED, result)
        assertEquals(listOf("bind", "trigger", "close"), events)
    }

    @Test fun nullBindingDoesNotBlockNormalTrigger() = runBlocking {
        val events = mutableListOf<String>()
        ConnectionWarmup.run(FakeConnection(events, connect = { false }), { true }, {}, {},
            { events += "trigger"; TriggerResult.REJECTED })
        assertEquals(listOf("bind", "connect", "trigger", "close"), events)
    }

    @Test fun connectionTimeoutStillTriggersAndReleases() = runBlocking {
        val events = mutableListOf<String>()
        val result = ConnectionWarmup.run(FakeConnection(events, connect = { awaitCancellation() }),
            { true }, {}, {}, { events += "trigger"; TriggerResult.ACCEPTED }, timeoutMs = 1L)
        assertEquals(TriggerResult.ACCEPTED, result)
        assertEquals(listOf("bind", "connect", "trigger", "close"), events)
    }

    @Test fun cancellationDuringConnectionReleasesWithoutTrigger() {
        val events = mutableListOf<String>()
        try {
            runBlocking {
                ConnectionWarmup.run(FakeConnection(events, connect = { throw CancellationException() }),
                    { true }, {}, {}, { error("Must not trigger") })
            }
            fail("Cancellation should propagate")
        } catch (_: CancellationException) { }
        assertEquals(listOf("bind", "connect", "close"), events)
    }

    @Test fun lockOrNewTapDuringConnectionPreventsCapture() = runBlocking {
        val events = mutableListOf<String>()
        var eligible = true
        val result = ConnectionWarmup.run(FakeConnection(events, connect = { eligible = false; true }),
            { eligible }, {}, {}, { error("Must not capture after lock or replacement") })
        assertEquals(TriggerResult.CANCELLED, result)
        assertEquals(listOf("bind", "connect", "close"), events)
    }

    @Test fun staleRequestNeverStartsGoogle() = runBlocking {
        val events = mutableListOf<String>()
        val result = ConnectionWarmup.run(FakeConnection(events), { false }, {}, {},
            { error("Must not trigger") })
        assertEquals(TriggerResult.CANCELLED, result)
        assertEquals(listOf("close"), events)
    }

    @Test fun cancellationAfterAcceptedRequestReleasesWithoutRetriggering() {
        val events = mutableListOf<String>()
        try {
            runBlocking {
                ConnectionWarmup.run(FakeConnection(events), { true },
                    { if (it == 500L) throw CancellationException() }, {},
                    { events += "trigger"; TriggerResult.ACCEPTED })
            }
            fail("Cancellation should propagate")
        } catch (_: CancellationException) { }
        assertEquals(listOf("bind", "connect", "trigger", "close"), events)
    }
}
