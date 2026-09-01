package retrylint.input

import com.fasterxml.jackson.annotation.JsonProperty

data class TopologyManifest(
    val version: Int,
    val name: String,
    val services: List<ServiceDeclaration>,
    val operations: List<OperationDeclaration>,
    val calls: List<CallDeclaration>,
    val analysis: AnalysisDeclaration,
)

data class ServiceDeclaration(
    val id: String,
    val config: String,
)

data class OperationDeclaration(
    val id: String,
    val service: String,
    val idempotency: Idempotency,
)

data class CallDeclaration(
    val id: String,
    val from: String,
    val to: String,
    val retry: String? = null,
    val timeLimiter: String? = null,
)

data class AnalysisDeclaration(
    val roots: List<String>,
    val amplificationWarning: Int = 9,
    val amplificationError: Int = 27,
    val failOn: FailureThreshold = FailureThreshold.ERROR,
)

enum class Idempotency {
    @JsonProperty("idempotent")
    IDEMPOTENT,

    @JsonProperty("non-idempotent")
    NON_IDEMPOTENT,

    @JsonProperty("unknown")
    UNKNOWN,
}

enum class FailureThreshold {
    @JsonProperty("warning")
    WARNING,

    @JsonProperty("error")
    ERROR,

    @JsonProperty("never")
    NEVER,
}
