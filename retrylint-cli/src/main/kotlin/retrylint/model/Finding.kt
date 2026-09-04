package retrylint.model

enum class Severity {
    WARNING,
    ERROR,
}

data class Finding(
    val ruleId: String,
    val severity: Severity,
    val title: String,
    val message: String,
    val callPath: List<String>,
    val evidence: Map<String, Any>,
)
