package retrylint.testbed

import kotlin.test.Test
import kotlin.test.assertFailsWith

class FailureDeciderTest {
    @Test
    fun `always-fail rejects every invocation`() {
        val decider = FailureDecider(TestbedProperties(serviceId = "bank", failureMode = "always-fail"))

        assertFailsWith<ConfiguredFailureException> { decider.failIfConfigured(1) }
        assertFailsWith<ConfiguredFailureException> { decider.failIfConfigured(99) }
    }

    @Test
    fun `fail-first-n succeeds after deterministic prefix`() {
        val decider = FailureDecider(
            TestbedProperties(serviceId = "bank", failureMode = "fail-first-n", failFirstN = 2),
        )

        assertFailsWith<ConfiguredFailureException> { decider.failIfConfigured(1) }
        assertFailsWith<ConfiguredFailureException> { decider.failIfConfigured(2) }
        decider.failIfConfigured(3)
    }
}
