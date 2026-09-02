package retrylint.config

import java.time.Duration

object Resilience4jDefaults {
    const val RETRY_MAX_ATTEMPTS = 3
    val RETRY_WAIT_DURATION: Duration = Duration.ofMillis(500)
    val TIME_LIMITER_TIMEOUT_DURATION: Duration = Duration.ofSeconds(1)
}
