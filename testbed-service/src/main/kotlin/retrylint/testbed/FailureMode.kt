package retrylint.testbed

enum class FailureMode {
    SUCCESS,
    ALWAYS_FAIL,
    FAIL_FIRST_N;

    companion object {
        fun parse(value: String): FailureMode =
            when (value.trim().lowercase()) {
                "success" -> SUCCESS
                "always-fail" -> ALWAYS_FAIL
                "fail-first-n" -> FAIL_FIRST_N
                else -> throw IllegalArgumentException(
                    "Unsupported failure mode '$value'; expected success, always-fail, or fail-first-n",
                )
            }
    }
}

class ConfiguredFailureException(message: String) : RuntimeException(message)

class FailureDecider(private val properties: TestbedProperties) {
    fun failIfConfigured(traceSequence: Long) {
        val shouldFail = when (FailureMode.parse(properties.failureMode)) {
            FailureMode.SUCCESS -> false
            FailureMode.ALWAYS_FAIL -> true
            FailureMode.FAIL_FIRST_N -> traceSequence <= properties.failFirstN
        }
        if (shouldFail) {
            throw ConfiguredFailureException(
                "${properties.serviceId} rejected invocation $traceSequence by ${properties.failureMode}",
            )
        }
    }
}
