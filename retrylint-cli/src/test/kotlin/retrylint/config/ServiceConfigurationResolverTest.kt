package retrylint.config

import org.junit.jupiter.api.io.TempDir
import retrylint.model.AspectOrder
import retrylint.model.Resolution
import java.nio.file.Path
import java.time.Duration
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ServiceConfigurationResolverTest {
    @TempDir
    lateinit var temporaryDirectory: Path

    private val resolver = ServiceConfigurationResolver()

    @Test
    fun `uses supported Resilience4j defaults for instances`() {
        val resolved = resolve(
            """
            management:
              endpoints:
                enabled-by-default: false
            resilience4j:
              retry:
                instances:
                  standard: {}
              timelimiter:
                instances:
                  standard: {}
            """,
        )

        assertEquals(3, resolved.retries.getValue("standard").maxAttempts)
        assertEquals(
            Resolution.Known(Duration.ofMillis(500)),
            resolved.retries.getValue("standard").fixedWait,
        )
        assertEquals(
            Resolution.Known(Duration.ofSeconds(1)),
            resolved.timeLimiters.getValue("standard").timeout,
        )
        assertEquals(
            Resolution.Known(AspectOrder.DEFAULT_RETRY_WRAPS_TIME_LIMITER),
            resolved.aspectOrder,
        )
    }

    @Test
    fun `named base chains and instance values merge using both key spellings`() {
        val resolved = resolve(
            """
            resilience4j:
              retry:
                configs:
                  default:
                    maxAttempts: 2
                    wait-duration: 100ms
                  parent:
                    baseConfig: default
                    max-attempts: 3
                  child:
                    base-config: parent
                    waitDuration: 2s
                instances:
                  client:
                    baseConfig: child
                    maxAttempts: 4
              timelimiter:
                configs:
                  default:
                    timeout-duration: 500ms
                  parent:
                    baseConfig: default
                    timeoutDuration: 2s
                instances:
                  client:
                    base-config: parent
                    timeout-duration: 3s
            """,
        )

        val retry = resolved.retries.getValue("client")
        assertEquals(4, retry.maxAttempts)
        assertEquals(Resolution.Known(Duration.ofSeconds(2)), retry.fixedWait)
        assertEquals(
            Resolution.Known(Duration.ofSeconds(3)),
            resolved.timeLimiters.getValue("client").timeout,
        )
    }

    @Test
    fun `unknown base configuration fails clearly`() {
        val error = assertFailsWith<ConfigurationException> {
            resolve(
                """
                resilience4j:
                  retry:
                    instances:
                      client:
                        baseConfig: missing
                """,
            )
        }

        assertTrue(error.message.orEmpty().contains("unknown retry baseConfig 'missing'"))
    }

    @Test
    fun `base configuration cycle fails with its chain`() {
        val error = assertFailsWith<ConfigurationException> {
            resolve(
                """
                resilience4j:
                  retry:
                    configs:
                      first:
                        baseConfig: second
                      second:
                        baseConfig: first
                    instances:
                      client:
                        baseConfig: first
                """,
            )
        }

        assertTrue(error.message.orEmpty().contains("first -> second -> first"))
    }

    @Test
    fun `invalid configured duration fails clearly`() {
        val error = assertFailsWith<ConfigurationException> {
            resolve(
                """
                resilience4j:
                  retry:
                    instances:
                      client:
                        waitDuration: eventually
                """,
            )
        }

        assertTrue(error.message.orEmpty().contains("invalid duration 'eventually'"))
        assertTrue(error.message.orEmpty().contains("retry instance 'client'"))
    }

    @Test
    fun `unsupported backoff remains explicit while attempts still resolve`() {
        val resolved = resolve(
            """
            resilience4j:
              retry:
                configs:
                  default:
                    maxAttempts: 5
                    waitDuration: 100ms
                    enable-exponential-backoff: true
                instances:
                  client:
                    baseConfig: default
                    waitDuration: 50ms
            """,
        )

        val retry = resolved.retries.getValue("client")
        assertEquals(5, retry.maxAttempts)
        val unsupported = assertIs<Resolution.Unsupported>(retry.fixedWait)
        assertTrue(unsupported.reason.contains("exponential backoff"))
    }

    @Test
    fun `randomized wait interval functions and custom aspect order are marked unsupported`() {
        val resolved = resolve(
            """
            resilience4j:
              retry:
                retry-aspect-order: 1
                instances:
                  client:
                    enableRandomizedWait: true
                    interval-function: customInterval
                    intervalBiFunction: customInterval
              timelimiter:
                timeLimiterAspectOrder: 2
                instances:
                  client: {}
            """,
        )

        val wait = assertIs<Resolution.Unsupported>(resolved.retries.getValue("client").fixedWait)
        assertTrue(wait.reason.contains("randomized wait"))
        assertTrue(wait.reason.contains("intervalFunction"))
        assertTrue(wait.reason.contains("intervalBiFunction"))
        val aspect = assertIs<Resolution.Unsupported>(resolved.aspectOrder)
        assertTrue(aspect.reason.contains("custom retry aspect order"))
        assertTrue(aspect.reason.contains("time-limiter aspect order"))
    }

    private fun resolve(yaml: String) =
        resolver.resolve(
            serviceId = "test-service",
            path = temporaryDirectory.resolve("application.yml").also { it.writeText(yaml.trimIndent()) },
        )
}
