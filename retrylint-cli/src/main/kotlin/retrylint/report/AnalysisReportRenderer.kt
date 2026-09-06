package retrylint.report

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import retrylint.analysis.AnalysisReport
import retrylint.model.CompletenessGap
import retrylint.model.Finding
import java.math.BigInteger
import java.nio.file.Path
import java.time.Duration

enum class OutputFormat {
    TEXT,
    JSON,
}

interface AnalysisReportRenderer {
    fun render(report: AnalysisReport, verbose: Boolean = false): String
}

class TextAnalysisReportRenderer : AnalysisReportRenderer {
    override fun render(report: AnalysisReport, verbose: Boolean): String = buildString {
        val topology = report.project.topology
        appendLine("RetryLint — ${report.project.manifest.name}")
        appendLine(
            "${topology.servicesById.size} services, ${topology.operationsById.size} operations, " +
                "${topology.callsById.size} calls",
        )
        if (verbose) {
            appendLine("Topological order: ${topology.topologicalOrder.joinToString(" -> ")}")
            appendLine(
                "Configuration files: " + report.project.configuration.services.values
                    .map { it.source.toString() }
                    .sorted()
                    .joinToString(", "),
            )
        }

        report.findings.forEach { finding ->
            appendLine()
            appendLine("${finding.severity} ${finding.ruleId} — ${finding.title}")
            appendLine(finding.message)
            if (finding.callPath.isNotEmpty()) appendLine("Calls: ${finding.callPath.joinToString(" -> ")}")
            appendLine("Evidence:")
            finding.evidence.forEach { (key, value) ->
                appendLine("  $key: ${formatTextValue(value)}")
            }
        }

        appendLine()
        if (report.completenessGaps.isEmpty()) {
            appendLine("Completeness: RL001 complete, RL002 complete, RL003 complete")
        } else {
            appendLine("Completeness gaps:")
            report.completenessGaps.forEach { appendLine(formatGap(it)) }
        }
        appendLine("${report.errorCount} errors, ${report.warningCount} warnings")
    }.trimEnd()

    private fun formatGap(gap: CompletenessGap): String {
        val operation = gap.operationId?.let { " at $it" }.orEmpty()
        val calls = if (gap.callPath.isEmpty()) "" else " [${gap.callPath.joinToString(" -> ")}]"
        return "- ${gap.ruleId}$operation$calls: ${gap.reason}"
    }
}

class JsonAnalysisReportRenderer(
    private val mapper: ObjectMapper = ObjectMapper()
        .registerKotlinModule()
        .enable(SerializationFeature.INDENT_OUTPUT),
) : AnalysisReportRenderer {
    override fun render(report: AnalysisReport, verbose: Boolean): String =
        mapper.writeValueAsString(report.toJsonReport())
}

data class JsonAnalysisReport(
    val schemaVersion: Int,
    val project: JsonProjectSummary,
    val findings: List<JsonFinding>,
    val completenessGaps: List<JsonCompletenessGap>,
    val summary: JsonAnalysisSummary,
)

data class JsonProjectSummary(val name: String, val services: Int, val operations: Int, val calls: Int)

data class JsonFinding(
    val ruleId: String,
    val severity: String,
    val title: String,
    val message: String,
    val callPath: List<String>,
    val evidence: Map<String, Any?>,
)

data class JsonCompletenessGap(
    val ruleId: String,
    val operationId: String?,
    val callPath: List<String>,
    val reason: String,
)

data class JsonAnalysisSummary(val errors: Int, val warnings: Int, val completenessGaps: Int)

private fun AnalysisReport.toJsonReport(): JsonAnalysisReport {
    val topology = project.topology
    return JsonAnalysisReport(
        schemaVersion = 1,
        project = JsonProjectSummary(
            name = project.manifest.name,
            services = topology.servicesById.size,
            operations = topology.operationsById.size,
            calls = topology.callsById.size,
        ),
        findings = findings.map { it.toJsonFinding() },
        completenessGaps = completenessGaps.map {
            JsonCompletenessGap(it.ruleId, it.operationId, it.callPath, it.reason)
        },
        summary = JsonAnalysisSummary(errorCount, warningCount, completenessGaps.size),
    )
}

private fun Finding.toJsonFinding() = JsonFinding(
    ruleId = ruleId,
    severity = severity.name.lowercase(),
    title = title,
    message = message,
    callPath = callPath,
    evidence = evidence.mapValues { normalizeJsonValue(it.value) },
)

private fun normalizeJsonValue(value: Any?): Any? = when (value) {
    null -> null
    is BigInteger -> value.toString()
    is Duration -> value.toString()
    is Path -> value.toString()
    is Map<*, *> -> value.entries.associate { it.key.toString() to normalizeJsonValue(it.value) }
    is Iterable<*> -> value.map(::normalizeJsonValue)
    is Array<*> -> value.map(::normalizeJsonValue)
    is String, is Number, is Boolean -> value
    else -> value.toString()
}

private fun formatTextValue(value: Any?): String = when (value) {
    is Duration -> value.toMillis().toString() + " ms"
    is Iterable<*> -> value.joinToString(", ") { formatTextValue(it) }
    is Array<*> -> value.joinToString(", ") { formatTextValue(it) }
    else -> value.toString()
}
