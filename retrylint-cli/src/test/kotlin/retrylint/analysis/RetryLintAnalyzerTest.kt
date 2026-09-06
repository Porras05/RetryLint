package retrylint.analysis

import retrylint.model.Severity
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RetryLintAnalyzerTest {
    private val analyzer = RetryLintAnalyzer()

    @Test
    fun `all three rules are combined and explicitly sorted`() {
        val report = analyzeFixture("complete-example")

        assertTrue(report.findings.any { it.ruleId == "RL001" })
        assertTrue(report.findings.any { it.ruleId == "RL002" })
        assertTrue(report.findings.any { it.ruleId == "RL003" })
        assertEquals(
            report.findings.sortedWith(
                compareBy(
                    { if (it.severity == Severity.ERROR) 0 else 1 },
                    { it.ruleId },
                    { it.callPath.joinToString("\u0000") },
                    { it.title },
                ),
            ),
            report.findings,
        )
    }

    @Test
    fun `repeated analysis has identical finding and completeness order`() {
        val first = analyzeFixture("rl002-unsupported")
        val second = analyzeFixture("rl002-unsupported")

        assertEquals(first.findings, second.findings)
        assertEquals(first.completenessGaps, second.completenessGaps)
    }

    private fun analyzeFixture(name: String): AnalysisReport = analyzer.analyze(
        Path.of(requireNotNull(javaClass.getResource("/fixtures/$name/retrylint.yml")).toURI()),
    )
}
