package retrylint.config

import retrylint.input.RetryLintProjectLoader
import retrylint.model.Resolution
import java.nio.file.Path
import java.time.Duration
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ProjectConfigurationResolverTest {
    @Test
    fun `loads each service file and links policies from the caller service`() {
        val manifestPath = resourcePath("/fixtures/complete-example/retrylint.yml")

        val project = RetryLintProjectLoader().load(manifestPath)

        assertEquals(setOf("checkout", "orders", "payments", "bank"), project.configuration.services.keys)
        assertTrue(
            project.configuration.services.getValue("checkout").source.endsWith(
                Path.of("services", "checkout", "application.yml"),
            ),
        )

        val checkoutCall = project.configuration.calls.getValue("checkout-to-orders")
        assertEquals("checkout", checkoutCall.callerServiceId)
        assertEquals(3, checkoutCall.retry.maxAttempts)
        assertEquals(Resolution.Known(Duration.ofMillis(100)), checkoutCall.retry.fixedWait)
        assertEquals(Resolution.Known(Duration.ofMillis(500)), checkoutCall.timeLimiter?.timeout)

        val ordersCall = project.configuration.calls.getValue("orders-to-payments")
        assertEquals("orders", ordersCall.callerServiceId)
        assertEquals(3, ordersCall.retry.maxAttempts)
        assertEquals(Resolution.Known(Duration.ofMillis(200)), ordersCall.retry.fixedWait)
        assertEquals(Resolution.Known(Duration.ofMillis(600)), ordersCall.timeLimiter?.timeout)

        val paymentsCall = project.configuration.calls.getValue("payments-to-bank")
        assertEquals("payments", paymentsCall.callerServiceId)
        assertEquals(3, paymentsCall.retry.maxAttempts)
        assertEquals(Resolution.Known(Duration.ofMillis(50)), paymentsCall.retry.fixedWait)
        assertEquals(Resolution.Known(Duration.ofMillis(300)), paymentsCall.timeLimiter?.timeout)
    }

    private fun resourcePath(name: String): Path =
        Path.of(requireNotNull(javaClass.getResource(name)).toURI())
}
