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

    @Test fun rejectedApplicationRequestCanRecoverWithGesture() = runBlocking {
        val events = mutableListOf<String>()
        val result = TriggerRequest.run(400L, { true }, { events += "wait:$it" },
            { events += "application"; false }, { events += "gesture"; true })
        assertEquals(TriggerResult.ACCEPTED, result)
        assertEquals(listOf("wait:400", "application", "wait:250", "gesture"), events)
    }

    @Test fun bothRejectedReportsFailureWithoutLooping() = runBlocking {
        var calls = 0
        val result = TriggerRequest.run(120L, { true }, {}, { calls++; false }, { calls++; false })
        assertEquals(TriggerResult.REJECTED, result)
        assertEquals(2, calls)
    }

    @Test fun ineligibleRequestDoesNotLaunchAssistant() = runBlocking {
        val result = TriggerRequest.run(120L, { false }, {},
            { error("must not launch") }, { error("must not launch") })
        assertEquals(TriggerResult.CANCELLED, result)
    }

    @Test fun lockingDuringRecoveryDoesNotLaunchAssistant() = runBlocking {
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
