package retrylint.model

import java.nio.file.Path
import java.time.Duration

sealed interface Resolution<out T> {
    data class Known<T>(val value: T) : Resolution<T>
    data class Unknown(val reason: String) : Resolution<Nothing>
    data class Unsupported(val reason: String) : Resolution<Nothing>
}

data class ResolvedRetry(
    val name: String?,
    val maxAttempts: Int,
    val fixedWait: Resolution<Duration>,
)

data class ResolvedTimeLimiter(
    val name: String,
    val timeout: Resolution<Duration>,
)

enum class AspectOrder {
    DEFAULT_RETRY_WRAPS_TIME_LIMITER,
}

data class ResolvedServiceConfiguration(
    val serviceId: String,
    val source: Path,
    val retries: Map<String, ResolvedRetry>,
    val timeLimiters: Map<String, ResolvedTimeLimiter>,
    val aspectOrder: Resolution<AspectOrder>,
)

data class ResolvedCallPolicies(
    val callId: String,
    val callerServiceId: String,
    val configurationSource: Path,
    val retry: ResolvedRetry,
    val timeLimiter: ResolvedTimeLimiter?,
    val aspectOrder: Resolution<AspectOrder>,
)

data class ResolvedProjectConfiguration(
    val services: Map<String, ResolvedServiceConfiguration>,
    val calls: Map<String, ResolvedCallPolicies>,
)
