package retrylint.rules

import retrylint.input.Idempotency
import retrylint.input.RetryLintProjectLoader
import retrylint.model.Resolution
import retrylint.model.Severity
import java.nio.file.Path
import java.time.Duration
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TimeoutBudgetAnalyzerTest {
    private val analyzer = TimeoutBudgetAnalyzer()

    @Test
    fun `eight hundred millisecond budget conflicts with thirteen hundred millisecond retry window`() {
        val result = analyzer.analyze(loadFixture("week5-mixed"))

        assertEquals(1, result.findings.size)
        assertTrue(result.completenessGaps.isEmpty())
        val finding = result.findings.single()
        assertEquals("RL002", finding.ruleId)
        assertEquals(Severity.ERROR, finding.severity)
        assertEquals(listOf("a-to-b", "b-to-c"), finding.callPath)
        assertEquals(Duration.ofMillis(800), finding.evidence["incomingTimeout"])
        assertEquals(3, finding.evidence["attempts"])
        assertEquals(Duration.ofMillis(400), finding.evidence["timeoutPerAttempt"])
        assertEquals(Duration.ofMillis(50), finding.evidence["fixedWait"])
        assertEquals(Duration.ofMillis(1_300), finding.evidence["retryWindow"])
        assertEquals(Duration.ofMillis(500), finding.evidence["overrun"])
        assertContains(finding.message, "configured worst-case retry window")
    }

    @Test
    fun `incompatible timeout is warning when target is idempotent`() {
        val project = loadFixture("week5-mixed")
        val operations = project.topology.operationsById.toMutableMap()
        operations["service.c"] = operations.getValue("service.c").copy(idempotency = Idempotency.IDEMPOTENT)
        val adjusted = project.copy(topology = project.topology.copy(operationsById = operations))

        val finding = analyzer.analyze(adjusted).findings.single()

        assertEquals(Severity.WARNING, finding.severity)
    }

    @Test
    fun `equal timeout budget does not produce a finding`() {
        val result = analyzer.analyze(loadFixture("rl002-equal"))

        assertTrue(result.findings.isEmpty())
        assertTrue(result.completenessGaps.isEmpty())
    }

    @Test
    fun `unsupported exponential wait records gap instead of guessing`() {
        val result = analyzer.analyze(loadFixture("rl002-unsupported"))

        assertEquals(1, result.findings.size)
        assertEquals(listOf("x-to-y", "y-to-z"), result.findings.single().callPath)
        assertEquals(1, result.completenessGaps.size)
        val gap = result.completenessGaps.single()
        assertEquals("RL002", gap.ruleId)
        assertEquals(listOf("a-to-b", "b-to-c"), gap.callPath)
        assertContains(gap.reason, "exponential backoff")
    }

    @Test
    fun `custom aspect order records an explicit completeness gap`() {
        val project = loadFixture("week5-mixed")
        val calls = project.configuration.calls.toMutableMap()
        calls["b-to-c"] = calls.getValue("b-to-c").copy(
            aspectOrder = Resolution.Unsupported("custom retry aspect order is not supported"),
        )
        val adjusted = project.copy(configuration = project.configuration.copy(calls = calls))

        val result = analyzer.analyze(adjusted)

        assertTrue(result.findings.isEmpty())
        assertEquals(1, result.completenessGaps.size)
        assertContains(result.completenessGaps.single().reason, "downstream aspect order unsupported")
    }

    @Test
    fun `missing time limiters record gaps instead of zero durations`() {
        val result = analyzer.analyze(loadFixture("rl001-safe"))

        assertTrue(result.findings.isEmpty())
        assertTrue(result.completenessGaps.isNotEmpty())
        assertContains(result.completenessGaps.first().reason, "has no time limiter")
    }

    private fun loadFixture(name: String) = RetryLintProjectLoader().load(
        Path.of(requireNotNull(javaClass.getResource("/fixtures/$name/retrylint.yml")).toURI()),
    )
}
