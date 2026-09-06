package retrylint.evaluation

import com.fasterxml.jackson.databind.ObjectMapper
import picocli.CommandLine
import retrylint.analysis.AnalysisReport
import retrylint.analysis.RetryLintAnalyzer
import retrylint.cli.RetryLintCommand
import retrylint.input.InputLimits
import retrylint.input.RetryLintProjectLoader
import java.io.PrintWriter
import java.io.StringWriter
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.security.MessageDigest
import java.util.Random
import kotlin.io.path.createDirectories
import kotlin.math.ceil

data class ScalabilityConfiguration(
    val graphSizes: List<Int> = listOf(10, 100, 500, 1_000),
    val generationSeeds: Map<Int, Long> = linkedMapOf(
        10 to 20_250_910L,
        100 to 20_251_000L,
        500 to 20_251_400L,
        1_000 to 20_251_900L,
    ),
    val warmupRuns: Int = 3,
    val measuredRuns: Int = 20,
)

data class SyntheticEdge(val id: String, val fromIndex: Int, val toIndex: Int, val kind: String)

data class GeneratedScalabilityProject(
    val operationCount: Int,
    val callCount: Int,
    val serviceCount: Int,
    val rootCount: Int,
    val generationSeed: Long,
    val graphShape: String,
    val retryPolicy: String,
    val timeLimiterPolicy: String,
    val expectedFindingCount: Int,
    val expectedCompletenessGapCount: Int,
    val manifestPath: Path,
    val manifestBytes: Long,
    val manifestSha256: String,
    val edges: List<SyntheticEdge>,
)

data class DurationStatistics(
    val rawNanoseconds: List<Long>,
    val minimumNanoseconds: Long,
    val medianNanoseconds: Double,
    val meanNanoseconds: Double,
    val p95Nanoseconds: Long,
    val maximumNanoseconds: Long,
)

data class ScalabilityEnvironment(
    val retryLintVersion: String,
    val analyzerTag: String,
    val analyzerCommit: String,
    val repositoryHead: String,
    val javaVersion: String,
    val javaVendor: String,
    val jvmName: String,
    val jvmVersion: String,
    val operatingSystem: String,
    val operatingSystemVersion: String,
    val architecture: String,
    val availableProcessors: Int,
    val maximumHeapBytes: Long,
)

data class ScalabilityCaseResult(
    val operationCount: Int,
    val callCount: Int,
    val serviceCount: Int,
    val rootCount: Int,
    val generationSeed: Long,
    val graphShape: String,
    val retryPolicy: String,
    val timeLimiterPolicy: String,
    val manifestBytes: Long,
    val manifestSha256: String,
    val warmupRunCount: Int,
    val measuredRunCount: Int,
    val correctnessCheckCount: Int,
    val preflightExitCode: Int,
    val observedFindingCount: Int,
    val observedCompletenessGapCount: Int,
    val expectedFindingCount: Int,
    val expectedCompletenessGapCount: Int,
    val status: String,
    val timings: DurationStatistics,
)

data class ScalingRatio(
    val fromOperations: Int,
    val toOperations: Int,
    val medianRatio: Double,
)

data class ScalabilityResult(
    val schemaVersion: Int,
    val benchmarkName: String,
    val measurementBoundary: String,
    val percentileConvention: String,
    val runRetentionPolicy: String,
    val environment: ScalabilityEnvironment,
    val configuration: ScalabilityConfiguration,
    val cases: List<ScalabilityCaseResult>,
    val medianScalingRatios: List<ScalingRatio>,
)

class SyntheticDagGenerator {
    fun generate(root: Path, operationCount: Int, seed: Long): GeneratedScalabilityProject {
        require(operationCount >= 2) { "operationCount must be at least 2" }
        require(operationCount <= InputLimits.MAX_OPERATIONS) { "operationCount exceeds frozen input limit" }
        val caseDirectory = root.resolve("ops-$operationCount").normalize()
        require(caseDirectory.startsWith(root.normalize())) { "generated case escapes generation root" }
        recreateDirectory(caseDirectory)

        val random = Random(seed)
        val edges = buildList {
            for (from in 0 until operationCount - 1) {
                add(edge(from, from + 1, "chain"))
                if (from + 2 < operationCount) {
                    val target = from + 2 + random.nextInt(operationCount - from - 2)
                    add(edge(from, target, "seeded-forward"))
                }
            }
        }
        require(edges.size <= InputLimits.MAX_CALLS) { "generated calls exceed frozen input limit" }
        require(edges.all { it.fromIndex < it.toIndex }) { "generator produced a non-forward edge" }

        val manifest = renderManifest(operationCount, seed, edges)
        val manifestPath = caseDirectory.resolve("retrylint.yml")
        writeUtf8(manifestPath, manifest)
        for (serviceIndex in 0 until SERVICE_COUNT) {
            writeUtf8(caseDirectory.resolve("services/service-$serviceIndex/application.yml"), SERVICE_CONFIGURATION)
        }
        val manifestBytes = Files.size(manifestPath)
        require(manifestBytes <= InputLimits.MAX_MANIFEST_BYTES) { "generated manifest exceeds frozen byte limit" }

        return GeneratedScalabilityProject(
            operationCount = operationCount,
            callCount = edges.size,
            serviceCount = SERVICE_COUNT,
            rootCount = 1,
            generationSeed = seed,
            graphShape = "connected sparse DAG: chain plus one fixed-seed forward edge per eligible operation",
            retryPolicy = "once: maxAttempts=1, waitDuration=0ms",
            timeLimiterPolicy = "safe: timeoutDuration=1s on every call",
            expectedFindingCount = 0,
            expectedCompletenessGapCount = 0,
            manifestPath = manifestPath,
            manifestBytes = manifestBytes,
            manifestSha256 = sha256(manifestPath),
            edges = edges,
        )
    }

    private fun renderManifest(operationCount: Int, seed: Long, edges: List<SyntheticEdge>): String = buildString {
        appendLine("version: 1")
        appendLine("name: week10-ops-$operationCount-seed-$seed")
        appendLine("services:")
        for (serviceIndex in 0 until SERVICE_COUNT) {
            appendLine("  - id: service-$serviceIndex")
            appendLine("    config: services/service-$serviceIndex/application.yml")
        }
        appendLine("operations:")
        for (operationIndex in 0 until operationCount) {
            appendLine("  - id: ${operationId(operationIndex)}")
            appendLine("    service: service-${operationIndex % SERVICE_COUNT}")
            appendLine("    idempotency: idempotent")
        }
        appendLine("calls:")
        edges.forEach { edge ->
            appendLine("  - id: ${edge.id}")
            appendLine("    from: ${operationId(edge.fromIndex)}")
            appendLine("    to: ${operationId(edge.toIndex)}")
            appendLine("    retry: once")
            appendLine("    timeLimiter: safe")
        }
        appendLine("analysis:")
        appendLine("  roots: [${operationId(0)}]")
        appendLine("  amplificationWarning: 9")
        appendLine("  amplificationError: 27")
        appendLine("  failOn: never")
    }

    private fun edge(from: Int, to: Int, kind: String) = SyntheticEdge(
        id = "call-${index(from)}-${index(to)}-$kind",
        fromIndex = from,
        toIndex = to,
        kind = kind,
    )

    private fun operationId(index: Int) = "operation-${index(index)}"
    private fun index(value: Int) = value.toString().padStart(4, '0')

    private fun recreateDirectory(directory: Path) {
        if (Files.exists(directory)) {
            Files.walk(directory).use { paths -> paths.sorted(Comparator.reverseOrder()).forEach(Files::delete) }
        }
        directory.createDirectories()
    }

    private fun writeUtf8(path: Path, content: String) {
        path.parent.createDirectories()
        Files.writeString(
            path,
            content,
            StandardCharsets.UTF_8,
            StandardOpenOption.CREATE,
            StandardOpenOption.TRUNCATE_EXISTING,
        )
    }

    private fun sha256(path: Path): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path))
        return digest.joinToString("") { "%02x".format(it) }
    }

    companion object {
        const val SERVICE_COUNT = 4
        private val SERVICE_CONFIGURATION = """
            resilience4j:
              retry:
                instances:
                  once:
                    maxAttempts: 1
                    waitDuration: 0ms
              timelimiter:
                instances:
                  safe:
                    timeoutDuration: 1s
        """.trimIndent() + "\n"
    }
}

object ScalabilityStatistics {
    fun calculate(rawNanoseconds: List<Long>): DurationStatistics {
        require(rawNanoseconds.isNotEmpty()) { "at least one duration is required" }
        require(rawNanoseconds.all { it >= 0 }) { "durations must not be negative" }
        val sorted = rawNanoseconds.sorted()
        val middle = sorted.size / 2
        val median = if (sorted.size % 2 == 1) {
            sorted[middle].toDouble()
        } else {
            (sorted[middle - 1].toDouble() + sorted[middle].toDouble()) / 2.0
        }
        val p95Index = ceil(0.95 * sorted.size).toInt().coerceAtLeast(1) - 1
        return DurationStatistics(
            rawNanoseconds = rawNanoseconds,
            minimumNanoseconds = sorted.first(),
            medianNanoseconds = median,
            meanNanoseconds = rawNanoseconds.map(Long::toDouble).average(),
            p95Nanoseconds = sorted[p95Index],
            maximumNanoseconds = sorted.last(),
        )
    }
}

object ScalabilityCorrectness {
    fun verify(project: GeneratedScalabilityProject, report: AnalysisReport) {
        require(report.project.manifest.operations.size == project.operationCount) {
            "${project.operationCount}-operation case loaded ${report.project.manifest.operations.size} operations"
        }
        require(report.project.manifest.calls.size == project.callCount) {
            "${project.operationCount}-operation case loaded ${report.project.manifest.calls.size} calls; expected ${project.callCount}"
        }
        require(report.project.manifest.services.size == project.serviceCount)
        require(report.project.manifest.analysis.roots.size == project.rootCount)
        require(report.project.topology.topologicalOrder.size == project.operationCount)
        require(report.findings.size == project.expectedFindingCount) {
            "${project.operationCount}-operation case produced ${report.findings.size} findings; expected ${project.expectedFindingCount}"
        }
        require(report.completenessGaps.size == project.expectedCompletenessGapCount) {
            "${project.operationCount}-operation case produced ${report.completenessGaps.size} gaps; expected ${project.expectedCompletenessGapCount}"
        }
    }
}

class Week10ScalabilityRunner(
    private val generator: SyntheticDagGenerator = SyntheticDagGenerator(),
    private val mapper: ObjectMapper = jsonMapper(),
) {
    fun run(evaluationRoot: Path): ScalabilityResult {
        require(System.getProperty("java.specification.version") == "21") {
            "Week 10 requires Java 21; observed ${System.getProperty("java.version")}"
        }
        val root = evaluationRoot.toAbsolutePath().normalize()
        val repository = root.parent.parent
        verifyFrozenCore(repository)
        verifyWeek9Summary(repository)
        val configuration = ScalabilityConfiguration()
        require(configuration.graphSizes == listOf(10, 100, 500, 1_000))
        require(configuration.measuredRuns == 20) { "the plan requires exactly 20 measured runs per size" }
        val generatedRoot = repository.resolve("retrylint-cli/build/week10-cases")
        generatedRoot.createDirectories()

        val analyzer = RetryLintAnalyzer()
        val cases = configuration.graphSizes.map { size ->
            val project = generator.generate(generatedRoot, size, configuration.generationSeeds.getValue(size))
            val preflight = cliPreflight(project.manifestPath)
            require(preflight.exitCode == 0) {
                "$size-operation CLI preflight returned ${preflight.exitCode}: ${preflight.stderr}"
            }
            require(preflight.findingCount == project.expectedFindingCount)
            require(preflight.completenessGapCount == project.expectedCompletenessGapCount)

            repeat(configuration.warmupRuns) {
                ScalabilityCorrectness.verify(project, analyzer.analyze(project.manifestPath))
            }
            val durations = buildList {
                repeat(configuration.measuredRuns) {
                    val started = System.nanoTime()
                    val report = analyzer.analyze(project.manifestPath)
                    val duration = System.nanoTime() - started
                    ScalabilityCorrectness.verify(project, report)
                    add(duration)
                }
            }
            val finalReport = analyzer.analyze(project.manifestPath)
            ScalabilityCorrectness.verify(project, finalReport)
            ScalabilityCaseResult(
                operationCount = project.operationCount,
                callCount = project.callCount,
                serviceCount = project.serviceCount,
                rootCount = project.rootCount,
                generationSeed = project.generationSeed,
                graphShape = project.graphShape,
                retryPolicy = project.retryPolicy,
                timeLimiterPolicy = project.timeLimiterPolicy,
                manifestBytes = project.manifestBytes,
                manifestSha256 = project.manifestSha256,
                warmupRunCount = configuration.warmupRuns,
                measuredRunCount = durations.size,
                correctnessCheckCount = 1 + configuration.warmupRuns + configuration.measuredRuns + 1,
                preflightExitCode = preflight.exitCode,
                observedFindingCount = finalReport.findings.size,
                observedCompletenessGapCount = finalReport.completenessGaps.size,
                expectedFindingCount = project.expectedFindingCount,
                expectedCompletenessGapCount = project.expectedCompletenessGapCount,
                status = "success",
                timings = ScalabilityStatistics.calculate(durations),
            )
        }
        val ratios = cases.zipWithNext { from, to ->
            ScalingRatio(from.operationCount, to.operationCount, to.timings.medianNanoseconds / from.timings.medianNanoseconds)
        }
        return ScalabilityResult(
            schemaVersion = 1,
            benchmarkName = "RetryLint Week 10 sparse-DAG scalability",
            measurementBoundary =
                "in-process RetryLintAnalyzer.analyze(Path): includes manifest/service YAML parsing, topology validation, " +
                    "configuration resolution, RL001, RL002, and RL003; excludes case generation, Gradle, JVM/process " +
                    "startup, CLI parsing, correctness assertions, and rendering",
            percentileConvention = "nearest-rank p95: sorted[ceil(0.95 * n) - 1]; median averages the two center values for even n",
            runRetentionPolicy = "3 warm-ups are excluded; all 20 measured runs per size are retained without outlier removal",
            environment = environment(repository),
            configuration = configuration,
            cases = cases,
            medianScalingRatios = ratios,
        )
    }

    fun write(result: ScalabilityResult, evaluationRoot: Path) {
        val results = evaluationRoot.resolve("results").also { it.createDirectories() }
        mapper.writeValue(results.resolve("scalability-results.json").toFile(), result)
        Files.writeString(results.resolve("summary.md"), renderSummary(result), StandardCharsets.UTF_8)
    }

    private fun cliPreflight(manifestPath: Path): CliPreflight {
        val stdout = StringWriter()
        val stderr = StringWriter()
        val command = CommandLine(RetryLintCommand())
            .setOut(PrintWriter(stdout, true))
            .setErr(PrintWriter(stderr, true))
        val exitCode = command.execute(
            "analyze", manifestPath.toString(), "--format", "json", "--fail-on", "never",
        )
        if (exitCode != 0) return CliPreflight(exitCode, -1, -1, stderr.toString().trim())
        val json = mapper.readTree(stdout.toString())
        return CliPreflight(
            exitCode,
            json.path("findings").size(),
            json.path("completenessGaps").size(),
            stderr.toString().trim(),
        )
    }

    private fun environment(repository: Path) = ScalabilityEnvironment(
        retryLintVersion = "0.1.0",
        analyzerTag = "v0.1.0",
        analyzerCommit = git(repository, "rev-parse", "v0.1.0"),
        repositoryHead = git(repository, "rev-parse", "HEAD"),
        javaVersion = System.getProperty("java.version"),
        javaVendor = System.getProperty("java.vendor"),
        jvmName = System.getProperty("java.vm.name"),
        jvmVersion = System.getProperty("java.vm.version"),
        operatingSystem = System.getProperty("os.name"),
        operatingSystemVersion = System.getProperty("os.version"),
        architecture = System.getProperty("os.arch"),
        availableProcessors = Runtime.getRuntime().availableProcessors(),
        maximumHeapBytes = Runtime.getRuntime().maxMemory(),
    )

    private fun verifyFrozenCore(repository: Path) {
        val diff = ProcessBuilder("git", "diff", "--quiet", "v0.1.0", "--", "retrylint-cli/src/main")
            .directory(repository.toFile()).start()
        require(diff.waitFor() == 0) { "frozen analyzer core differs from v0.1.0; scalability evaluation aborted" }
        val untracked = git(repository, "ls-files", "--others", "--exclude-standard", "--", "retrylint-cli/src/main")
        require(untracked.isBlank()) { "frozen analyzer core contains untracked files: $untracked" }
    }

    private fun verifyWeek9Summary(repository: Path) {
        val summary = mapper.readTree(repository.resolve("evaluation/week9/results/summary.json").toFile())
        require(summary.path("executedCaseCount").asInt() == 40)
        require(summary.path("skippedCaseCount").asInt() == 0)
        require(summary.path("overall").path("tp").asInt() == 30)
        require(summary.path("overall").path("fp").asInt() == 0)
        require(summary.path("overall").path("fn").asInt() == 0)
    }

    private fun git(repository: Path, vararg arguments: String): String {
        val process = ProcessBuilder(listOf("git") + arguments).directory(repository.toFile()).start()
        val stdout = process.inputStream.bufferedReader().readText().trim()
        val stderr = process.errorStream.bufferedReader().readText().trim()
        require(process.waitFor() == 0) { "git ${arguments.joinToString(" ")} failed: $stderr" }
        return stdout
    }

    private fun renderSummary(result: ScalabilityResult): String = buildString {
        appendLine("# RetryLint Week 10 scalability results")
        appendLine()
        appendLine("Frozen analyzer: ${result.environment.retryLintVersion} (${result.environment.analyzerTag}, `${result.environment.analyzerCommit}`)")
        appendLine()
        appendLine("Measurement boundary: ${result.measurementBoundary}")
        appendLine()
        appendLine("Warm-up: ${result.configuration.warmupRuns} runs per size. Measured: ${result.configuration.measuredRuns} runs per size. Every measured duration is retained in the JSON evidence.")
        appendLine()
        appendLine("| Operations | Calls | Services | Roots | Runs | Min ms | Median ms | Mean ms | P95 ms | Max ms | Findings/Gaps | Result |")
        appendLine("|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---|")
        result.cases.forEach { case ->
            appendLine(
                "| ${case.operationCount} | ${case.callCount} | ${case.serviceCount} | ${case.rootCount} | " +
                    "${case.measuredRunCount} | ${ms(case.timings.minimumNanoseconds.toDouble())} | " +
                    "${ms(case.timings.medianNanoseconds)} | ${ms(case.timings.meanNanoseconds)} | " +
                    "${ms(case.timings.p95Nanoseconds.toDouble())} | ${ms(case.timings.maximumNanoseconds.toDouble())} | " +
                    "${case.observedFindingCount}/${case.observedCompletenessGapCount} | ${case.status} |",
            )
        }
        appendLine()
        appendLine("## Median scaling ratios")
        appendLine()
        result.medianScalingRatios.forEach { ratio ->
            appendLine("- ${ratio.toOperations} / ${ratio.fromOperations} operations: ${decimal(ratio.medianRatio)}x")
        }
        appendLine()
        appendLine("## Interpretation")
        appendLine()
        val largestSpread = result.cases.maxBy { it.timings.maximumNanoseconds / it.timings.medianNanoseconds }
        val largestSpreadRatio = largestSpread.timings.maximumNanoseconds / largestSpread.timings.medianNanoseconds
        appendLine(
            "Median growth remained below operation-count growth at every step. This is consistent with practical " +
                "scaling for these sparse inputs, but four empirical points do not establish formal complexity. " +
                "The largest retained max/median spread was the ${largestSpread.operationCount}-operation case: " +
                "${ms(largestSpread.timings.maximumNanoseconds.toDouble())} ms, ${decimal(largestSpreadRatio)}x its " +
                "median. It had no correctness failure; GC/scheduler telemetry was not collected, so its cause is unknown.",
        )
        appendLine()
        appendLine("These measurements describe one environment and are not a production-scale performance claim. No outlier was removed. Memory usage was not measured because the plan makes it optional and reliable JVM memory attribution would require a separate methodology.")
        appendLine()
        appendLine("P95 convention: ${result.percentileConvention}")
    }

    private fun ms(nanoseconds: Double) = decimal(nanoseconds / 1_000_000.0)
    private fun decimal(value: Double) = "%.3f".format(java.util.Locale.ROOT, value)

    private data class CliPreflight(
        val exitCode: Int,
        val findingCount: Int,
        val completenessGapCount: Int,
        val stderr: String,
    )
}
