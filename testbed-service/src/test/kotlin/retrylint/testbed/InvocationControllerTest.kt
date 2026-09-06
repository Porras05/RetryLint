package retrylint.testbed

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class InvocationControllerTest {
    @Test
    fun `provided trace ID is counted once and propagated downstream`() {
        val properties = TestbedProperties(serviceId = "orders")
        val counter = InvocationCounter(properties)
        var propagated: String? = null
        val service = InvocationService(properties, counter, DownstreamInvoker { propagated = it })
        val controller = InvocationController(properties, service)

        val response = controller.invoke("trace-123")

        assertEquals(200, response.statusCode.value())
        assertEquals("trace-123", propagated)
        assertEquals("trace-123", response.headers.getFirst(TRACE_ID_HEADER))
        assertEquals(1, counter.snapshot("trace-123").traceCount)
    }

    @Test
    fun `missing trace ID is generated and returned`() {
        val properties = TestbedProperties(serviceId = "checkout")
        val counter = InvocationCounter(properties)
        var propagated: String? = null
        val service = InvocationService(properties, counter, DownstreamInvoker { propagated = it })
        val controller = InvocationController(properties, service)

        val response = controller.invoke(null)
        val generated = response.headers.getFirst(TRACE_ID_HEADER)

        assertNotNull(generated)
        assertTrue(generated.isNotBlank())
        assertEquals(generated, propagated)
        assertEquals(1, counter.snapshot(generated).traceCount)
    }
}
