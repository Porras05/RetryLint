package retrylint.rules

import retrylint.input.RetryLintProjectLoader
import retrylint.model.Resolution
import java.math.BigInteger
import java.nio.file.Path
import java.time.Duration
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RuleHardeningTest {
    @Test
    fun `RL001 fixture boundaries and unreachable nodes remain correct`() {
        val analyzer = RetryAmplificationAnalyzer()
        assertEquals(BigInteger.valueOf(15), analyzer.analyze(load("rl001-between")).maximumPath.multiplier)
        assertEquals(BigInteger.valueOf(28), analyzer.analyze(load("rl001-above")).maximumPath.multiplier)
        assertEquals(BigInteger.ONE, analyzer.analyze(load("rl001-unreachable")).maximumPath.multiplier)
        assertTrue(analyzer.analyze(load("rl001-bigint")).maximumPath.multiplier > BigInteger.valueOf(Long.MAX_VALUE))
    }

    @Test
    fun `RL002 greater equality one-attempt and zero wait boundaries are safe`() {
        val analyzer = TimeoutBudgetAnalyzer()
        assertTrue(analyzer.analyze(load("rl002-safe-greater")).findings.isEmpty())
        assertTrue(analyzer.analyze(load("rl002-equal")).findings.isEmpty())
        assertTrue(analyzer.analyze(load("rl002-one-attempt")).findings.isEmpty())

        val project = load("week5-mixed")
        val calls = project.configuration.calls.toMutableMap()
        val downstream = calls.getValue("b-to-c")
        calls["b-to-c"] = downstream.copy(retry = downstream.retry.copy(fixedWait = Resolution.Known(Duration.ZERO)))
        assertTrue(TimeoutBudgetAnalyzer().analyze(project.copy(configuration = project.configuration.copy(calls = calls)))
            .findings.isNotEmpty())
    }

    @Test
    fun `unknown unsupported and overflowing durations become local completeness gaps`() {
        val project = load("week5-mixed")
        listOf<Resolution<Duration>>(
            Resolution.Unknown("placeholder is outside the supported contract"),
            Resolution.Unsupported("custom timeout source"),
            Resolution.Known(Duration.ofSeconds(Long.MAX_VALUE, 999_999_999)),
        ).forEach { resolution ->
            val calls = project.configuration.calls.toMutableMap()
            val downstream = calls.getValue("b-to-c")
            calls["b-to-c"] = downstream.copy(
                timeLimiter = downstream.timeLimiter?.copy(timeout = resolution),
            )
            val adjusted = project.copy(configuration = project.configuration.copy(calls = calls))
            val result = TimeoutBudgetAnalyzer().analyze(adjusted)
            assertTrue(result.findings.isEmpty())
            assertEquals(1, result.completenessGaps.size)
        }
    }

    @Test
    fun `large valid whole-millisecond duration does not overflow finding formatting`() {
        val project = load("week5-mixed")
        val calls = project.configuration.calls.toMutableMap()
        val downstream = calls.getValue("b-to-c")
        calls["b-to-c"] = downstream.copy(
            retry = downstream.retry.copy(maxAttempts = 1, fixedWait = Resolution.Known(Duration.ZERO)),
            timeLimiter = downstream.timeLimiter?.copy(timeout = Resolution.Known(Duration.ofSeconds(Long.MAX_VALUE))),
        )
        val adjusted = project.copy(configuration = project.configuration.copy(calls = calls))

        val result = TimeoutBudgetAnalyzer().analyze(adjusted)

        assertEquals(1, result.findings.size)
        assertTrue(result.findings.single().title.contains("PT"))
    }

    @Test
    fun `per-operation and total adjacent pair guards are explicit`() {
        val project = load("week5-mixed")
        val perOperation = TimeoutBudgetAnalyzer(maximumAdjacentPairsPerOperation = 0).analyze(project)
        assertTrue(perOperation.completenessGaps.any { it.reason.contains("adjacent call pairs") })

        val total = TimeoutBudgetAnalyzer(maximumTotalAdjacentPairs = 0).analyze(project)
        assertTrue(total.completenessGaps.any { it.reason.contains("total-pair limit") })
    }

    @Test
    fun `RL003 full fixture wording and evidence remain conservative`() {
        val result = UnsafeRetryAnalyzer().analyze(load("rl003-decision-table"))
        assertEquals(2, result.findings.size)
        result.findings.forEach { finding ->
            assertTrue(finding.title.contains("may be repeated"))
            assertTrue(finding.message.contains("may invoke"))
            assertTrue("callId" in finding.evidence)
            assertTrue("targetOperation" in finding.evidence)
            assertTrue("idempotency" in finding.evidence)
            assertTrue("maxAttempts" in finding.evidence)
            assertTrue("configurationFile" in finding.evidence)
        }
    }

    private fun load(name: String) = RetryLintProjectLoader().load(
        Path.of(requireNotNull(javaClass.getResource("/fixtures/$name/retrylint.yml")).toURI()),
    )
}
