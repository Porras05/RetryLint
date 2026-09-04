package retrylint.rules

import retrylint.graph.TopologyValidator
import retrylint.input.AnalysisDeclaration
import retrylint.input.CallDeclaration
import retrylint.input.FailureThreshold
import retrylint.input.Idempotency
import retrylint.input.LoadedRetryLintProject
import retrylint.input.OperationDeclaration
import retrylint.input.ServiceDeclaration
import retrylint.input.TopologyManifest
import retrylint.model.AspectOrder
import retrylint.model.Resolution
import retrylint.model.ResolvedCallPolicies
import retrylint.model.ResolvedProjectConfiguration
import retrylint.model.ResolvedRetry
import retrylint.model.ResolvedServiceConfiguration
import retrylint.model.Severity
import java.math.BigInteger
import java.nio.file.Path
import java.time.Duration
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RetryAmplificationAnalyzerTest {
    private val analyzer = RetryAmplificationAnalyzer()

    @Test
    fun `equal paths retain the first path in manifest call order`() {
        val project = project(
            operations = listOf("root", "left", "right", "target"),
            calls = listOf(
                CallSpec("root-left", "root", "left", 3),
                CallSpec("root-right", "root", "right", 3),
                CallSpec("left-target", "left", "target", 3),
                CallSpec("right-target", "right", "target", 3),
            ),
            roots = listOf("root"),
        )

        val result = analyzer.analyze(project)

        assertEquals(BigInteger.valueOf(9), result.maximumPath.multiplier)
        assertEquals(listOf("root-left", "left-target"), result.maximumPath.edges.map { it.callId })
    }

    @Test
    fun `multiple roots select the root producing the greatest multiplier`() {
        val project = project(
            operations = listOf("root-a", "root-b", "target"),
            calls = listOf(
                CallSpec("a-target", "root-a", "target", 2),
                CallSpec("b-target", "root-b", "target", 5),
            ),
            roots = listOf("root-a", "root-b"),
        )

        val result = analyzer.analyze(project)

        assertEquals("root-b", result.maximumPath.rootOperation)
        assertEquals(BigInteger.valueOf(5), result.maximumPath.multiplier)
    }

    @Test
    fun `operations unreachable from configured roots are ignored`() {
        val project = project(
            operations = listOf("root", "target", "unreachable", "unreachable-target"),
            calls = listOf(
                CallSpec("root-target", "root", "target", 2),
                CallSpec("huge-unreachable", "unreachable", "unreachable-target", 1_000),
            ),
            roots = listOf("root"),
        )

        val result = analyzer.analyze(project)

        assertEquals("target", result.maximumPath.targetOperation)
        assertEquals(BigInteger.valueOf(2), result.maximumPath.multiplier)
        assertNull(result.finding)
    }

    @Test
    fun `omitted retry contributes one attempt`() {
        val project = project(
            operations = listOf("root", "middle", "target"),
            calls = listOf(
                CallSpec("without-retry", "root", "middle", 1, retryName = null),
                CallSpec("three-attempts", "middle", "target", 3),
            ),
            roots = listOf("root"),
        )

        val result = analyzer.analyze(project)

        assertEquals(BigInteger.valueOf(3), result.maximumPath.multiplier)
        assertEquals("1 x 3 = 3", result.maximumPath.expression)
    }

    @Test
    fun `large attempt products cannot overflow long arithmetic`() {
        val attempts = Int.MAX_VALUE
        val project = project(
            operations = listOf("root", "one", "two", "target"),
            calls = listOf(
                CallSpec("first", "root", "one", attempts),
                CallSpec("second", "one", "two", attempts),
                CallSpec("third", "two", "target", attempts),
            ),
            roots = listOf("root"),
        )

        val result = analyzer.analyze(project)

        val expected = attempts.toBigInteger().pow(3)
        assertEquals(expected, result.maximumPath.multiplier)
        assertTrue(result.maximumPath.multiplier > BigInteger.valueOf(Long.MAX_VALUE))
    }

    @Test
    fun `finding contains responsible path attempts expression and configuration evidence`() {
        val project = project(
            operations = listOf("root", "one", "two", "target"),
            calls = listOf(
                CallSpec("first", "root", "one", 3),
                CallSpec("second", "one", "two", 3),
                CallSpec("third", "two", "target", 3),
            ),
            roots = listOf("root"),
        )

        val finding = requireNotNull(analyzer.analyze(project).finding)

        assertEquals("RL001", finding.ruleId)
        assertEquals(Severity.ERROR, finding.severity)
        assertEquals("Retry amplification of 27x", finding.title)
        assertEquals(listOf("first", "second", "third"), finding.callPath)
        assertEquals("root", finding.evidence["rootOperation"])
        assertEquals("target", finding.evidence["targetOperation"])
        assertEquals(listOf("root", "one", "two", "target"), finding.evidence["operationPath"])
        assertEquals("3 x 3 x 3 = 27", finding.evidence["expression"])
        assertEquals(listOf(3, 3, 3), finding.evidence["attempts"])
        assertEquals(BigInteger.valueOf(27), finding.evidence["multiplier"])
        assertTrue(finding.message.contains("Three layers independently own retries on this path."))
        assertEquals(
            listOf("first", "second", "third").map { Path.of("configs", "$it.yml").toString() },
            finding.evidence["configurationFiles"],
        )
    }

    private fun project(
        operations: List<String>,
        calls: List<CallSpec>,
        roots: List<String>,
        warningThreshold: Int = 9,
        errorThreshold: Int = 27,
    ): LoadedRetryLintProject {
        val manifest = TopologyManifest(
            version = 1,
            name = "unit-test",
            services = listOf(ServiceDeclaration("service", "application.yml")),
            operations = operations.map { OperationDeclaration(it, "service", Idempotency.IDEMPOTENT) },
            calls = calls.map { CallDeclaration(it.id, it.from, it.to, it.retryName) },
            analysis = AnalysisDeclaration(
                roots = roots,
                amplificationWarning = warningThreshold,
                amplificationError = errorThreshold,
                failOn = FailureThreshold.ERROR,
            ),
        )
        val topology = TopologyValidator().validate(manifest)
        val service = ResolvedServiceConfiguration(
            serviceId = "service",
            source = Path.of("application.yml"),
            retries = emptyMap(),
            timeLimiters = emptyMap(),
            aspectOrder = Resolution.Known(AspectOrder.DEFAULT_RETRY_WRAPS_TIME_LIMITER),
        )
        val resolvedCalls = calls.associate { call ->
            call.id to ResolvedCallPolicies(
                callId = call.id,
                callerServiceId = "service",
                configurationSource = Path.of("configs", "${call.id}.yml"),
                retry = ResolvedRetry(
                    name = call.retryName,
                    maxAttempts = call.attempts,
                    fixedWait = Resolution.Known(Duration.ZERO),
                ),
                timeLimiter = null,
                aspectOrder = Resolution.Known(AspectOrder.DEFAULT_RETRY_WRAPS_TIME_LIMITER),
            )
        }
        return LoadedRetryLintProject(
            manifest = manifest,
            topology = topology,
            configuration = ResolvedProjectConfiguration(
                services = mapOf("service" to service),
                calls = resolvedCalls,
            ),
        )
    }

    private data class CallSpec(
        val id: String,
        val from: String,
        val to: String,
        val attempts: Int,
        val retryName: String? = id,
    )
}
