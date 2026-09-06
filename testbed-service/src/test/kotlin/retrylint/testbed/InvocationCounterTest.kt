package retrylint.testbed

import kotlin.test.Test
import kotlin.test.assertEquals

class InvocationCounterTest {
    @Test
    fun `counts totals and traces then resets synchronously`() {
        val counter = InvocationCounter(TestbedProperties(serviceId = "payments"))

        counter.record("trace-a")
        counter.record("trace-a")
        counter.record("trace-b")

        assertEquals(3, counter.snapshot().total)
        assertEquals(2, counter.snapshot("trace-a").traceCount)
        assertEquals(mapOf("trace-a" to 2L, "trace-b" to 1L), counter.snapshot().byTrace)

        val reset = counter.reset()
        assertEquals(0, reset.total)
        assertEquals(emptyMap(), reset.byTrace)
        assertEquals(0, counter.snapshot("trace-a").traceCount)
    }

    @Test
    fun `admin reads and resets do not count as business invocations`() {
        val counter = InvocationCounter(TestbedProperties(serviceId = "orders"))
        val admin = TestbedAdminController(counter)

        repeat(3) { admin.counters() }
        admin.reset()

        assertEquals(0, admin.counters().total)
    }
}
