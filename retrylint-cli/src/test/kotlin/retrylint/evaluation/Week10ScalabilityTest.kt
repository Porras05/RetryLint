package retrylint.evaluation

import retrylint.analysis.RetryLintAnalyzer
import retrylint.input.RetryLintProjectLoader
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class Week10ScalabilityTest {
    private val generator = SyntheticDagGenerator()

    @Test
    fun `requested case order and fixed seeds are deterministic`() {
        val configuration = ScalabilityConfiguration()
        assertEquals(listOf(10, 100, 500, 1_000), configuration.graphSizes)
        assertEquals(20, configuration.measuredRuns)
        assertEquals(configuration.graphSizes.toSet(), configuration.generationSeeds.keys)
    }

    @Test
    fun `generator creates exact sparse graph counts at every planned size`() {
        val root = Files.createTempDirectory("retrylint-week10-counts-")
        listOf(10, 100, 500, 1_000).forEach { size ->
            val project = generator.generate(root, size, 12_345L + size)
            assertEquals(size, project.operationCount)
            assertEquals(2 * size - 3, project.callCount)
            assertEquals(4, project.serviceCount)
            assertEquals(1, project.rootCount)
            assertTrue(project.manifestBytes <= retrylint.input.InputLimits.MAX_MANIFEST_BYTES)
        }
    }

    @Test
    fun `all generated edges are forward so graph is acyclic`() {
        val project = generator.generate(Files.createTempDirectory("retrylint-week10-dag-"), 500, 99L)
        assertTrue(project.edges.all { it.fromIndex < it.toIndex })
        assertEquals(project.callCount, project.edges.map { it.id }.distinct().size)
    }

    @Test
    fun `same size and seed produce byte-identical YAML`() {
        val first = generator.generate(Files.createTempDirectory("retrylint-week10-first-"), 100, 42L)
        val second = generator.generate(Files.createTempDirectory("retrylint-week10-second-"), 100, 42L)
        assertEquals(first.manifestSha256, second.manifestSha256)
        assertEquals(Files.readString(first.manifestPath), Files.readString(second.manifestPath))
    }

    @Test
    fun `different seed changes seeded edges without changing counts`() {
        val first = generator.generate(Files.createTempDirectory("retrylint-week10-seed-a-"), 100, 1L)
        val second = generator.generate(Files.createTempDirectory("retrylint-week10-seed-b-"), 100, 2L)
        assertNotEquals(first.manifestSha256, second.manifestSha256)
        assertEquals(first.callCount, second.callCount)
    }

    @Test
    fun `generated project is valid RetryLint input with controlled semantics`() {
        val project = generator.generate(Files.createTempDirectory("retrylint-week10-valid-"), 100, 7L)
        val loaded = RetryLintProjectLoader().load(project.manifestPath)
        assertEquals(100, loaded.topology.topologicalOrder.size)
        val report = RetryLintAnalyzer().analyze(project.manifestPath)
        ScalabilityCorrectness.verify(project, report)
        assertTrue(report.findings.isEmpty())
        assertTrue(report.completenessGaps.isEmpty())
    }

    @Test
    fun `correctness verification rejects wrong expected count`() {
        val project = generator.generate(Files.createTempDirectory("retrylint-week10-wrong-"), 10, 5L)
        val report = RetryLintAnalyzer().analyze(project.manifestPath)
        val wrong = project.copy(expectedFindingCount = 1)
        assertFailsWith<IllegalArgumentException> { ScalabilityCorrectness.verify(wrong, report) }
    }

    @Test
    fun `statistics calculate even median mean nearest-rank p95 and extrema`() {
        val values = (1L..20L).toList()
        val statistics = ScalabilityStatistics.calculate(values)
        assertEquals(1L, statistics.minimumNanoseconds)
        assertEquals(10.5, statistics.medianNanoseconds)
        assertEquals(10.5, statistics.meanNanoseconds)
        assertEquals(19L, statistics.p95Nanoseconds)
        assertEquals(20L, statistics.maximumNanoseconds)
        assertEquals(values, statistics.rawNanoseconds)
    }

    @Test
    fun `statistics calculate odd median and retain input order`() {
        val values = listOf(9L, 1L, 5L)
        val statistics = ScalabilityStatistics.calculate(values)
        assertEquals(5.0, statistics.medianNanoseconds)
        assertEquals(values, statistics.rawNanoseconds)
    }

    @Test
    fun `scalability result serialization retains raw timings and case order`() {
        val mapper = jsonMapper()
        val timing = ScalabilityStatistics.calculate(listOf(3L, 1L, 2L))
        val cases = listOf(10, 100).map { size -> sampleCase(size, timing) }
        val result = ScalabilityResult(
            schemaVersion = 1,
            benchmarkName = "test",
            measurementBoundary = "analysis",
            percentileConvention = "nearest-rank",
            runRetentionPolicy = "all",
            environment = sampleEnvironment(),
            configuration = ScalabilityConfiguration(graphSizes = listOf(10, 100), generationSeeds = mapOf(10 to 1, 100 to 2)),
            cases = cases,
            medianScalingRatios = listOf(ScalingRatio(10, 100, 1.0)),
        )
        val json = mapper.readTree(mapper.writeValueAsString(result))
        assertEquals(listOf(10, 100), json.path("cases").map { it.path("operationCount").asInt() })
        assertEquals(listOf(3L, 1L, 2L), json.path("cases")[0].path("timings").path("rawNanoseconds").map { it.asLong() })
    }

    private fun sampleCase(size: Int, timings: DurationStatistics) = ScalabilityCaseResult(
        operationCount = size,
        callCount = 2 * size - 3,
        serviceCount = 4,
        rootCount = 1,
        generationSeed = size.toLong(),
        graphShape = "sparse",
        retryPolicy = "once",
        timeLimiterPolicy = "safe",
        manifestBytes = 1,
        manifestSha256 = "hash",
        warmupRunCount = 3,
        measuredRunCount = 3,
        correctnessCheckCount = 8,
        preflightExitCode = 0,
        observedFindingCount = 0,
        observedCompletenessGapCount = 0,
        expectedFindingCount = 0,
        expectedCompletenessGapCount = 0,
        status = "success",
        timings = timings,
    )

    private fun sampleEnvironment() = ScalabilityEnvironment(
        retryLintVersion = "0.1.0",
        analyzerTag = "v0.1.0",
        analyzerCommit = "commit",
        repositoryHead = "head",
        javaVersion = "21",
        javaVendor = "vendor",
        jvmName = "vm",
        jvmVersion = "21",
        operatingSystem = "os",
        operatingSystemVersion = "version",
        architecture = "arch",
        availableProcessors = 1,
        maximumHeapBytes = 1,
    )
}
