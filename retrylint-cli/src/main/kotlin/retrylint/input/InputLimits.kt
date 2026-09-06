package retrylint.input

/** Hard limits for the frozen v0.1 input contract, sized far above the four-service testbed. */
object InputLimits {
    const val MAX_MANIFEST_BYTES: Long = 2L * 1024 * 1024
    const val MAX_SERVICE_CONFIG_BYTES: Long = 2L * 1024 * 1024
    const val MAX_SERVICES = 256
    const val MAX_OPERATIONS = 10_000
    const val MAX_CALLS = 20_000
    const val MAX_ROOTS = 256
    const val MAX_IDENTIFIER_LENGTH = 256
    const val MAX_PROJECT_NAME_LENGTH = 256
    const val MAX_CONFIG_PATH_LENGTH = 1_024
    const val MAX_NAMED_POLICIES_PER_SECTION = 2_048
    const val MAX_BASE_CONFIG_DEPTH = 8
    const val MAX_ADJACENT_PAIRS_PER_OPERATION: Long = 10_000
    const val MAX_TOTAL_ADJACENT_PAIRS: Long = 100_000
}
