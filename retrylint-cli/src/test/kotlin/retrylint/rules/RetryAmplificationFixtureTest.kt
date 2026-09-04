package retrylint.rules

import retrylint.input.RetryLintProjectLoader
import retrylint.model.Severity
import java.math.BigInteger
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class RetryAmplificationFixtureTest {
    private val analyzer = RetryAmplificationAnalyzer()

    @Test
    fun `complete example resolves three by three by three to error at twenty seven`() {
        val result = analyzeFixture("complete-example")

        assertEquals(BigInteger.valueOf(27), result.maximumPath.multiplier)
        assertEquals(
            listOf("checkout-to-orders", "orders-to-payments", "payments-to-bank"),
            result.maximumPath.edges.map { it.callId },
        )
        assertEquals(Severity.ERROR, result.finding?.severity)
    }

    @Test
    fun `one by one by three stays below warning threshold`() {
        val result = analyzeFixture("rl001-safe")

        assertEquals(BigInteger.valueOf(3), result.maximumPath.multiplier)
        assertEquals("1 x 1 x 3 = 3", result.maximumPath.expression)
        assertNull(result.finding)
    }

    @Test
    fun `branching fixture deterministically chooses maximum path and warning boundary`() {
        val result = analyzeFixture("rl001-branching")

        assertEquals(BigInteger.valueOf(9), result.maximumPath.multiplier)
        assertEquals(
            listOf("root-to-right", "right-to-target"),
            result.maximumPath.edges.map { it.callId },
        )
        assertEquals(Severity.WARNING, result.finding?.severity)
    }

    private fun analyzeFixture(name: String): RetryAmplificationResult {
        val manifestPath = Path.of(
            requireNotNull(javaClass.getResource("/fixtures/$name/retrylint.yml")).toURI(),
        )
        return analyzer.analyze(RetryLintProjectLoader().load(manifestPath))
    }
}
