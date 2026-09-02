package retrylint.config

import io.github.resilience4j.core.functions.Either
import io.github.resilience4j.retry.Retry
import io.github.resilience4j.retry.RetryConfig
import io.github.resilience4j.timelimiter.TimeLimiterConfig
import java.time.Duration
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertTrue

class Resilience4jContractTest {
    @Test
    fun `RetryLint retry defaults match pinned Resilience4j`() {
        val actual = RetryConfig.ofDefaults()

        assertEquals(Resilience4jDefaults.RETRY_MAX_ATTEMPTS, actual.maxAttempts)
        assertEquals(
            Resilience4jDefaults.RETRY_WAIT_DURATION.toMillis(),
            interval(actual, 1),
        )
    }

    @Test
    fun `maxAttempts includes the initial invocation`() {
        val invocations = AtomicInteger()
        val retry = Retry.of(
            "attempt-contract",
            RetryConfig.custom<Any>()
                .maxAttempts(3)
                .waitDuration(Duration.ZERO)
                .build(),
        )

        assertFails {
            retry.executeCallable<Unit> {
                invocations.incrementAndGet()
                error("always fails")
            }
        }

        assertEquals(3, invocations.get())
    }

    @Test
    fun `fixed wait and RetryConfig inheritance match resolver assumptions`() {
        val base = RetryConfig.custom<Any>()
            .maxAttempts(4)
            .waitDuration(Duration.ofMillis(125))
            .build()
        val derived = RetryConfig.from<Any>(base)
            .maxAttempts(2)
            .build()

        assertEquals(2, derived.maxAttempts)
        assertEquals(125L, interval(derived, 1))
        assertEquals(125L, interval(derived, 2))
    }

    @Test
    fun `RetryLint time-limiter default and inheritance match pinned Resilience4j`() {
        val actualDefault = TimeLimiterConfig.ofDefaults()
        assertEquals(Resilience4jDefaults.TIME_LIMITER_TIMEOUT_DURATION, actualDefault.timeoutDuration)

        val base = TimeLimiterConfig.custom()
            .timeoutDuration(Duration.ofMillis(750))
            .cancelRunningFuture(false)
            .build()
        val derived = TimeLimiterConfig.from(base)
            .timeoutDuration(Duration.ofSeconds(2))
            .build()

        assertEquals(Duration.ofSeconds(2), derived.timeoutDuration)
        assertTrue(!derived.shouldCancelRunningFuture())
    }

    private fun interval(config: RetryConfig, attempt: Int): Long =
        config.getIntervalBiFunction<Any>().apply(
            attempt,
            Either.right<Throwable, Any>(Any()),
        )
}
