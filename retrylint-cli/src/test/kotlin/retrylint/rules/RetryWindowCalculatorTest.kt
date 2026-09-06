package retrylint.rules

import java.time.Duration
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class RetryWindowCalculatorTest {
    @Test
    fun `three attempts with four hundred millisecond timeouts and fifty millisecond waits is thirteen hundred`() {
        val window = RetryWindowCalculator.calculate(
            attempts = 3,
            timeoutPerAttempt = Duration.ofMillis(400),
            fixedWait = Duration.ofMillis(50),
        )

        assertEquals(Duration.ofMillis(1_300), window)
    }

    @Test
    fun `one attempt has no retry wait`() {
        val window = RetryWindowCalculator.calculate(
            attempts = 1,
            timeoutPerAttempt = Duration.ofMillis(400),
            fixedWait = Duration.ofSeconds(10),
        )

        assertEquals(Duration.ofMillis(400), window)
    }

    @Test
    fun `invalid arithmetic inputs are rejected before calculation`() {
        assertFailsWith<IllegalArgumentException> {
            RetryWindowCalculator.calculate(0, Duration.ZERO, Duration.ZERO)
        }
        assertFailsWith<IllegalArgumentException> {
            RetryWindowCalculator.calculate(1, Duration.ofMillis(-1), Duration.ZERO)
        }
        assertFailsWith<IllegalArgumentException> {
            RetryWindowCalculator.calculate(1, Duration.ZERO, Duration.ofMillis(-1))
        }
    }
}
