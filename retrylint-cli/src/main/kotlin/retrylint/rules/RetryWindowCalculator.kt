package retrylint.rules

import java.time.Duration

object RetryWindowCalculator {
    fun calculate(attempts: Int, timeoutPerAttempt: Duration, fixedWait: Duration): Duration {
        require(attempts >= 1) { "attempts must be at least one" }
        require(!timeoutPerAttempt.isNegative) { "timeout per attempt must not be negative" }
        require(!fixedWait.isNegative) { "fixed wait must not be negative" }

        return timeoutPerAttempt.multipliedBy(attempts.toLong())
            .plus(fixedWait.multipliedBy((attempts - 1).toLong()))
    }
}
