package retrylint.rules

import retrylint.input.RetryLintProjectLoader
import retrylint.model.Severity
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class UnsafeRetryAnalyzerTest {
    private val analyzer = UnsafeRetryAnalyzer()

    @Test
    fun `decision table reports only unknown and non-idempotent repeated targets`() {
        val result = analyzer.analyze(loadFixture("rl003-decision-table"))

        assertTrue(result.completenessGaps.isEmpty())
        assertEquals(listOf("to-unknown", "to-non-idempotent"), result.findings.map { it.callPath.single() })
        assertEquals(listOf(Severity.WARNING, Severity.ERROR), result.findings.map { it.severity })
    }

    @Test
    fun `unsafe retry finding says operation may be repeated and includes evidence`() {
        val finding = analyzer.analyze(loadFixture("rl003-decision-table"))
            .findings
            .single { it.severity == Severity.ERROR }

        assertEquals("RL003", finding.ruleId)
        assertContains(finding.title, "may be repeated")
        assertContains(finding.message, "may invoke service.non-idempotent up to 3 times")
        assertEquals("to-non-idempotent", finding.evidence["callId"])
        assertEquals("service.non-idempotent", finding.evidence["targetOperation"])
        assertEquals("non-idempotent", finding.evidence["idempotency"])
        assertEquals(3, finding.evidence["maxAttempts"])
        assertTrue(finding.evidence["configurationFile"].toString().endsWith("application.yml"))
    }

    @Test
    fun `unsupported timeout timing does not prevent unsafe retry analysis`() {
        val result = analyzer.analyze(loadFixture("rl002-unsupported"))

        assertEquals(1, result.findings.size)
        assertEquals(Severity.WARNING, result.findings.single().severity)
        assertEquals("b-to-c", result.findings.single().evidence["callId"])
    }

    private fun loadFixture(name: String) = RetryLintProjectLoader().load(
        Path.of(requireNotNull(javaClass.getResource("/fixtures/$name/retrylint.yml")).toURI()),
    )
}
