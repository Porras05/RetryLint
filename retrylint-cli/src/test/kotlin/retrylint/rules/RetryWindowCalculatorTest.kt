package retrylint.rules

import java.time.Duration
import kotlin.test.Test
import kotlin.test.assertEquals

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
}
