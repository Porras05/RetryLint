package retrylint.analysis

import retrylint.input.LoadedRetryLintProject
import retrylint.input.RetryLintProjectLoader
import retrylint.model.CompletenessGap
import retrylint.model.Finding
import retrylint.model.Severity
import retrylint.rules.RetryAmplificationAnalyzer
import retrylint.rules.TimeoutBudgetAnalyzer
import retrylint.rules.UnsafeRetryAnalyzer
import java.nio.file.Path

data class AnalysisReport(
    val project: LoadedRetryLintProject,
    val findings: List<Finding>,
    val completenessGaps: List<CompletenessGap>,
) {
    val errorCount: Int = findings.count { it.severity == Severity.ERROR }
    val warningCount: Int = findings.count { it.severity == Severity.WARNING }
}

fun interface AnalysisService {
    fun analyze(manifestPath: Path): AnalysisReport
}

class RetryLintAnalyzer(
    private val projectLoader: RetryLintProjectLoader = RetryLintProjectLoader(),
    private val amplificationAnalyzer: RetryAmplificationAnalyzer = RetryAmplificationAnalyzer(),
    private val timeoutBudgetAnalyzer: TimeoutBudgetAnalyzer = TimeoutBudgetAnalyzer(),
    private val unsafeRetryAnalyzer: UnsafeRetryAnalyzer = UnsafeRetryAnalyzer(),
) : AnalysisService {
    override fun analyze(manifestPath: Path): AnalysisReport {
        val project = projectLoader.load(manifestPath)
        val amplification = amplificationAnalyzer.analyze(project)
        val timeoutBudget = timeoutBudgetAnalyzer.analyze(project)
        val unsafeRetry = unsafeRetryAnalyzer.analyze(project)

        return AnalysisReport(
            project = project,
            findings = (listOfNotNull(amplification.finding) + timeoutBudget.findings + unsafeRetry.findings)
                .sortedWith(findingComparator),
            completenessGaps = (timeoutBudget.completenessGaps + unsafeRetry.completenessGaps)
                .sortedWith(completenessGapComparator),
        )
    }

    private companion object {
        val findingComparator = compareBy<Finding>(
            { if (it.severity == Severity.ERROR) 0 else 1 },
            { it.ruleId },
            { it.callPath.joinToString("\u0000") },
            { it.title },
        )
        val completenessGapComparator = compareBy<CompletenessGap>(
            { it.ruleId },
            { it.operationId.orEmpty() },
            { it.callPath.joinToString("\u0000") },
            { it.reason },
        )
    }
}
