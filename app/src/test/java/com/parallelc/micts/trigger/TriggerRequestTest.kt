package com.parallelc.micts.trigger

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class TriggerRequestTest {
    @Test fun acceptedRequestNeverTriggersAgain() = runBlocking {
        var calls = 0
        val result = TriggerRequest.run(120L, { true }, {}, { true }, { calls++; true })
        assertEquals(TriggerResult.ACCEPTED, result)
        assertEquals(0, calls)
    }

    @Test fun rejectedFreshRequestCanRecoverWithLegacy() = runBlocking {
        val events = mutableListOf<String>()
        val result = TriggerRequest.run(400L, { true }, { events += "wait:$it" },
            { events += "fresh"; false }, { events += "legacy"; true })
        assertEquals(TriggerResult.ACCEPTED, result)
        assertEquals(listOf("wait:400", "fresh", "wait:250", "legacy"), events)
    }

    @Test fun bothRejectedReportsFailureWithoutLooping() = runBlocking {
        var calls = 0
        val result = TriggerRequest.run(120L, { true }, {}, { calls++; false }, { calls++; false })
        assertEquals(TriggerResult.REJECTED, result)
        assertEquals(2, calls)
    }

    @Test fun leavingBeforeFirstRequestDoesNotLaunchAssistant() = runBlocking {
        val result = TriggerRequest.run(120L, { false }, {},
            { error("must not launch") }, { error("must not launch") })
        assertEquals(TriggerResult.CANCELLED, result)
    }

    @Test fun leavingDuringRecoveryDoesNotLaunchOverAnotherApp() = runBlocking {
        var foreground = true
        val result = TriggerRequest.run(120L, { foreground },
            { if (it == 250L) foreground = false }, { false }, { error("must not launch") })
        assertEquals(TriggerResult.CANCELLED, result)
    }

    @Test fun lifecycleCancellationIsNotSwallowed() {
        try {
            runBlocking {
                TriggerRequest.run(120L, { true }, { throw CancellationException() },
                    { error("must not launch") }, { error("must not launch") })
            }
            fail("Cancellation should propagate")
        } catch (_: CancellationException) { }
    }
}
