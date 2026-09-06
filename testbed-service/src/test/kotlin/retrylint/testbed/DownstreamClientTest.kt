package retrylint.testbed

import com.sun.net.httpserver.HttpServer
import io.github.resilience4j.retry.RetryConfig
import io.github.resilience4j.retry.RetryRegistry
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.web.client.RestClient
import java.net.InetSocketAddress
import java.time.Duration
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertEquals
import kotlin.test.assertFails

class DownstreamClientTest {
    private var server: HttpServer? = null

    @AfterEach
    fun stopServer() {
        server?.stop(0)
    }

    @Test
    fun `named Retry policy performs three real HTTP attempts and propagates trace ID`() {
        val requests = AtomicInteger()
        val traces = CopyOnWriteArrayList<String>()
        server = HttpServer.create(InetSocketAddress(0), 0).apply {
            createContext("/invoke") { exchange ->
                requests.incrementAndGet()
                traces += exchange.requestHeaders.getFirst(TRACE_ID_HEADER)
                exchange.sendResponseHeaders(503, -1)
                exchange.close()
            }
            start()
        }
        val retryConfig = RetryConfig.custom<Any>()
            .maxAttempts(3)
            .waitDuration(Duration.ZERO)
            .build()
        val registry = RetryRegistry.of(mapOf("bank-client" to retryConfig))
        val properties = TestbedProperties(
            serviceId = "payments",
            downstreamUrl = "http://localhost:${server!!.address.port}/invoke",
            retryPolicy = "bank-client",
        )
        val client = DownstreamClient(RestClient.builder(), registry, properties)

        assertFails { client.invoke("trace-retry") }

        assertEquals(3, requests.get())
        assertEquals(listOf("trace-retry", "trace-retry", "trace-retry"), traces)
        assertEquals(3, registry.retry("bank-client").retryConfig.maxAttempts)
    }
}
