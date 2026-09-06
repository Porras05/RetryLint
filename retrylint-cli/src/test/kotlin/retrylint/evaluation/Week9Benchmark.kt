package retrylint.evaluation

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.MapperFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.databind.json.JsonMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.fasterxml.jackson.module.kotlin.readValue
import retrylint.config.ServiceConfigurationResolver
import retrylint.model.Resolution
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import kotlin.io.path.createDirectories
import kotlin.io.path.isDirectory

data class BenchmarkIndex(
    val schemaVersion: Int,
    val retryLintVersion: String,
    val expectedSeedCount: Int,
    val expectedMutantCount: Int,
    val seeds: List<BenchmarkEntry>,
    val mutants: List<BenchmarkEntry>,
)

data class BenchmarkEntry(val id: String, val path: String, val truth: String)

data class BenchmarkTruth(
    val caseId: String,
    val seed: String,
    val mutations: List<String>,
    val expectedStatus: String,
    val expectedFindings: List<FindingKey> = emptyList(),
    val expectedCompletenessGaps: List<GapExpectation> = emptyList(),
    val diagnosticContains: String? = null,
    val rationale: String,
    val reviewed: Boolean,
    val reviewNote: String,
    val falseResultExplanation: String? = null,
)

data class FindingKey(
    val ruleId: String,
    val severity: String,
    val callPath: List<String>,
    val targetOperation: String,
)

data class GapExpectation(
    val ruleId: String,
    val callPath: List<String>,
    val reasonContains: String,
)

data class Metrics(val tp: Int, val fp: Int, val fn: Int) {
    val precision: Double? get() = ratio(tp, tp + fp)
    val recall: Double? get() = ratio(tp, tp + fn)
    val f1: Double?
        get() {
            val p = precision ?: return null
            val r = recall ?: return null
            return if (p + r == 0.0) null else 2 * p * r / (p + r)
        }

    operator fun plus(other: Metrics) = Metrics(tp + other.tp, fp + other.fp, fn + other.fn)

    private fun ratio(numerator: Int, denominator: Int): Double? =
        if (denominator == 0) null else numerator.toDouble() / denominator
}

data class BaselineIssue(val kind: String, val serviceId: String, val message: String)
data class BaselineResult(val issues: List<BaselineIssue>)

data class CaseResult(
    val caseId: String,
    val seed: String,
    val mutations: List<String>,
    val expectedStatus: String,
    val observedStatus: String,
    val exitCode: Int,
    val expectedFindings: List<FindingKey>,
    val observedFindings: List<FindingKey>,
    val expectedCompletenessGaps: List<GapExpectation>,
    val observedCompletenessGaps: List<ObservedGap>,
    val metrics: Metrics,
    val baseline: BaselineResult,
    val classification: String,
    val reviewed: Boolean,
    val reviewNote: String,
    val rationale: String,
    val discrepancyExplanation: String?,
    val stdout: String,
    val stderr: String,
)

data class ObservedGap(val ruleId: String, val callPath: List<String>, val reason: String)

data class RuleMetrics(val ruleId: String, val metrics: Metrics)

data class BenchmarkSummary(
    val schemaVersion: Int,
    val retryLintVersion: String,
    val scoringUnit: String,
    val zeroDenominatorConvention: String,
    val seedCount: Int,
    val mutantCount: Int,
    val totalCaseCount: Int,
    val executedCaseCount: Int,
    val skippedCaseCount: Int,
    val validFullAnalysisCount: Int,
    val invalidRejectedCount: Int,
    val partialAnalysisCount: Int,
    val mutationOperatorDistribution: Map<String, Int>,
    val overall: Metrics,
    val perRule: List<RuleMetrics>,
    val baselineIssueCount: Int,
    val falseResultCount: Int,
    val falseResults: List<String>,
)

data class BenchmarkRun(val summary: BenchmarkSummary, val cases: List<CaseResult>)

class BenchmarkDefinitionLoader(
    private val mapper: ObjectMapper = jsonMapper(),
) {
    fun load(root: Path): Pair<BenchmarkIndex, List<Pair<BenchmarkEntry, BenchmarkTruth>>> {
        val indexPath = root.resolve("benchmark.json")
        require(Files.isRegularFile(indexPath)) { "missing benchmark index: $indexPath" }
        val index: BenchmarkIndex = mapper.readValue(indexPath.toFile())
        require(index.schemaVersion == 1) { "unsupported benchmark schema ${index.schemaVersion}" }
        require(index.retryLintVersion == "0.1.0") { "benchmark must target RetryLint 0.1.0" }
        require(index.seeds.size == index.expectedSeedCount) { "seed count does not match index declaration" }
        require(index.mutants.size == index.expectedMutantCount) { "mutant count does not match index declaration" }
        require(index.expectedSeedCount == 10) { "Week 9 requires exactly 10 safe seeds" }
        require(index.expectedMutantCount in 20..35) { "Week 9 requires approximately 30 reviewed mutants" }

        val entries = index.seeds + index.mutants
        val duplicateIds = entries.groupingBy { it.id }.eachCount().filterValues { it > 1 }.keys
        require(duplicateIds.isEmpty()) { "duplicate benchmark case IDs: ${duplicateIds.sorted()}" }
        val duplicatePaths = entries.groupingBy { it.path }.eachCount().filterValues { it > 1 }.keys
        require(duplicatePaths.isEmpty()) { "duplicate benchmark paths: ${duplicatePaths.sorted()}" }

        val loaded = entries.map { entry ->
            val caseDirectory = safeResolve(root, entry.path)
            require(caseDirectory.isDirectory()) { "missing benchmark case directory for '${entry.id}': $caseDirectory" }
            val truthPath = safeResolve(caseDirectory, entry.truth)
            require(Files.isRegularFile(truthPath)) { "missing truth file for '${entry.id}': $truthPath" }
            val truth: BenchmarkTruth = mapper.readValue(truthPath.toFile())
            require(truth.caseId == entry.id) { "truth caseId '${truth.caseId}' does not match index '${entry.id}'" }
            require(truth.reviewed) { "benchmark case '${entry.id}' has unreviewed truth" }
            require(truth.reviewNote.isNotBlank()) { "benchmark case '${entry.id}' has no review note" }
            require(truth.expectedStatus in VALID_STATUSES) {
                "benchmark case '${entry.id}' has invalid expectedStatus '${truth.expectedStatus}'"
            }
            if (entry in index.seeds) {
                require(truth.seed == entry.id) { "safe seed '${entry.id}' must identify itself as its seed" }
                require(truth.expectedStatus == "valid") { "safe seed '${entry.id}' must be valid" }
                require(truth.expectedFindings.isEmpty()) { "safe seed '${entry.id}' must have no expected findings" }
                require(truth.expectedCompletenessGaps.isEmpty()) { "safe seed '${entry.id}' must be fully analyzable" }
            } else {
                require(index.seeds.any { it.id == truth.seed }) {
                    "mutant '${entry.id}' references unknown seed '${truth.seed}'"
                }
                require(truth.mutations.isNotEmpty()) { "mutant '${entry.id}' has no mutation operator" }
            }
            require(truth.expectedFindings.distinct().size == truth.expectedFindings.size) {
                "benchmark case '${entry.id}' contains duplicate expected findings"
            }
            entry to truth
        }

        verifyDirectoryInventory(root.resolve("seeds"), index.seeds.map { safeResolve(root, it.path) })
        verifyDirectoryInventory(root.resolve("mutants"), index.mutants.map { safeResolve(root, it.path) })
        return index to loaded
    }

    private fun verifyDirectoryInventory(parent: Path, expected: List<Path>) {
        require(parent.isDirectory()) { "missing benchmark directory: $parent" }
        val actual = Files.list(parent).use { stream -> stream.filter { it.isDirectory() }.map { it.normalize() }.toList() }
        val expectedSet = expected.map { it.normalize() }.toSet()
        val unexpected = actual.filterNot { it in expectedSet }.map { it.fileName.toString() }.sorted()
        val missing = expectedSet.filterNot { it in actual }.map { it.fileName.toString() }.sorted()
        require(unexpected.isEmpty()) { "unexpected benchmark case directories: $unexpected" }
        require(missing.isEmpty()) { "indexed benchmark case directories are missing: $missing" }
    }

    private fun safeResolve(parent: Path, child: String): Path {
        val result = parent.resolve(child).normalize()
        require(result.startsWith(parent.normalize())) { "benchmark path escapes its parent: '$child'" }
        return result
    }

    companion object {
        val VALID_STATUSES = setOf("valid", "invalid", "partially-analyzable")
    }
}

class LocalOnlyBaseline(
    private val yamlMapper: ObjectMapper = ObjectMapper(YAMLFactory()),
    private val resolver: ServiceConfigurationResolver = ServiceConfigurationResolver(),
) {
    fun analyze(manifestPath: Path): BaselineResult {
        val issues = mutableListOf<BaselineIssue>()
        val root = try {
            yamlMapper.readTree(manifestPath.toFile())
        } catch (exception: Exception) {
            return BaselineResult(listOf(BaselineIssue("invalid-manifest", "", normalizedMessage(exception))))
        }
        val services = root.path("services")
        if (!services.isArray) return BaselineResult(listOf(BaselineIssue("invalid-manifest", "", "services is not an array")))
        services.forEach { service ->
            val id = service.path("id").asText("")
            val config = service.path("config").asText("")
            if (id.isBlank() || config.isBlank()) {
                issues += BaselineIssue("invalid-service", id, "service id/config is missing")
                return@forEach
            }
            val configPath = manifestPath.parent.resolve(config).normalize()
            try {
                val resolved = resolver.resolve(id, configPath)
                resolved.retries.values.forEach { retry ->
                    if (retry.fixedWait is Resolution.Unsupported) {
                        issues += BaselineIssue("unsupported-local-retry-timing", id, retry.fixedWait.reason)
                    }
                }
                if (resolved.aspectOrder is Resolution.Unsupported) {
                    issues += BaselineIssue("unsupported-local-aspect-order", id, resolved.aspectOrder.reason)
                }
            } catch (exception: Exception) {
                issues += BaselineIssue("invalid-local-configuration", id, normalizedMessage(exception))
            }
        }
        return BaselineResult(issues.sortedWith(compareBy({ it.serviceId }, { it.kind }, { it.message })))
    }

    private fun normalizedMessage(exception: Exception): String =
        (exception.message ?: exception::class.simpleName.orEmpty()).replace('\\', '/')
}

class Week9BenchmarkRunner(
    private val definitionLoader: BenchmarkDefinitionLoader = BenchmarkDefinitionLoader(),
    private val mapper: ObjectMapper = jsonMapper(),
    private val baseline: LocalOnlyBaseline = LocalOnlyBaseline(),
) {
    fun run(root: Path): BenchmarkRun {
        val normalizedRoot = root.toAbsolutePath().normalize()
        val repo = normalizedRoot.parent.parent
        verifyFrozenCore(repo)
        val (index, definitions) = definitionLoader.load(normalizedRoot)
        val materializedRoot = repo.resolve("retrylint-cli/build/week9-cases")
        recreateDirectory(materializedRoot)

        val seedPaths = index.seeds.associate { it.id to normalizedRoot.resolve(it.path).normalize() }
        val cases = definitions.map { (entry, truth) ->
            val source = if (entry in index.seeds) {
                normalizedRoot.resolve(entry.path).normalize()
            } else {
                materializeMutant(
                    seedPaths.getValue(truth.seed),
                    normalizedRoot.resolve(entry.path).normalize(),
                    materializedRoot.resolve(entry.id),
                )
            }
            executeCase(entry, truth, source.resolve("retrylint.yml"), repo)
        }

        val falseCases = cases.filter { it.metrics.fp > 0 || it.metrics.fn > 0 }
        falseCases.forEach { result ->
            require(!result.discrepancyExplanation.isNullOrBlank()) {
                "false result '${result.caseId}' must have a checked-in explanation"
            }
        }
        val invalidCount = cases.count { it.observedStatus == "invalid" }
        val partialCount = cases.count { it.observedStatus == "partially-analyzable" }
        val validCount = cases.count { it.observedStatus == "valid" }
        val scoringCases = cases.filter { it.expectedStatus == "valid" }
        val overall = scoringCases.fold(Metrics(0, 0, 0)) { total, case -> total + case.metrics }
        val rules = listOf("RL001", "RL002", "RL003")
        val perRule = rules.map { rule ->
            RuleMetrics(rule, scoringCases.fold(Metrics(0, 0, 0)) { total, case ->
                total + classify(
                    case.expectedFindings.filter { it.ruleId == rule },
                    case.observedFindings.filter { it.ruleId == rule },
                )
            })
        }
        val summary = BenchmarkSummary(
            schemaVersion = 1,
            retryLintVersion = index.retryLintVersion,
            scoringUnit = "(ruleId, severity, ordered callPath, targetOperation)",
            zeroDenominatorConvention = "undefined metrics are serialized as null",
            seedCount = index.seeds.size,
            mutantCount = index.mutants.size,
            totalCaseCount = cases.size,
            executedCaseCount = cases.size,
            skippedCaseCount = 0,
            validFullAnalysisCount = validCount,
            invalidRejectedCount = invalidCount,
            partialAnalysisCount = partialCount,
            mutationOperatorDistribution = index.mutants
                .flatMap { entry -> definitions.single { it.first == entry }.second.mutations }
                .groupingBy { it }.eachCount().toSortedMap(),
            overall = overall,
            perRule = perRule,
            baselineIssueCount = cases.sumOf { it.baseline.issues.size },
            falseResultCount = falseCases.size,
            falseResults = falseCases.map { "${it.caseId}: ${it.discrepancyExplanation}" },
        )
        require(cases.none { it.classification == "status-mismatch" || it.classification == "gap-mismatch" }) {
            "benchmark status/completeness mismatch; inspect generated raw results"
        }
        return BenchmarkRun(summary, cases)
    }

    fun write(run: BenchmarkRun, root: Path) {
        val results = root.resolve("results").also { it.createDirectories() }
        mapper.writeValue(results.resolve("raw-results.json").toFile(), run.cases)
        mapper.writeValue(results.resolve("summary.json").toFile(), run.summary)
        Files.writeString(results.resolve("summary.md"), renderSummary(run.summary), StandardCharsets.UTF_8)
        Files.writeString(results.resolve("mutant-table.md"), renderMutantTable(run.cases), StandardCharsets.UTF_8)
    }

    private fun executeCase(entry: BenchmarkEntry, truth: BenchmarkTruth, manifest: Path, repo: Path): CaseResult {
        val java = Path.of(System.getProperty("java.home"), "bin", if (System.getProperty("os.name").startsWith("Windows")) "java.exe" else "java")
        val process = ProcessBuilder(
            java.toString(), "-cp", System.getProperty("java.class.path"), "retrylint.cli.MainKt",
            "analyze", manifest.toString(), "--format", "json", "--fail-on", "never",
        ).directory(repo.toFile()).start()
        val stdout = process.inputStream.bufferedReader().readText()
        val stderr = process.errorStream.bufferedReader().readText()
        val exitCode = process.waitFor()
        val parsed = if (exitCode == 0) mapper.readTree(stdout) else null
        val observedFindings = parsed?.path("findings")?.map(::findingKey).orEmpty().sortedWith(findingComparator)
        val observedGaps = parsed?.path("completenessGaps")?.map { node ->
            ObservedGap(node.path("ruleId").asText(), stringList(node.path("callPath")), node.path("reason").asText())
        }.orEmpty().sortedWith(compareBy({ it.ruleId }, { it.callPath.joinToString("\u0000") }, { it.reason }))
        val observedStatus = classifyStatus(exitCode, observedGaps.size)
        val statusMatches = observedStatus == truth.expectedStatus &&
            (truth.diagnosticContains == null || stderr.contains(truth.diagnosticContains, ignoreCase = true))
        val gapsMatch = gapsMatch(truth.expectedCompletenessGaps, observedGaps)
        val metrics = if (truth.expectedStatus == "valid" && exitCode == 0) {
            classify(truth.expectedFindings, observedFindings)
        } else Metrics(0, 0, 0)
        val classification = when {
            !statusMatches -> "status-mismatch"
            !gapsMatch -> "gap-mismatch"
            metrics.fp > 0 || metrics.fn > 0 -> "false-result"
            truth.expectedStatus == "invalid" -> "expected-rejection"
            truth.expectedStatus == "partially-analyzable" -> "expected-partial"
            else -> "matched"
        }
        return CaseResult(
            caseId = entry.id,
            seed = truth.seed,
            mutations = truth.mutations,
            expectedStatus = truth.expectedStatus,
            observedStatus = observedStatus,
            exitCode = exitCode,
            expectedFindings = truth.expectedFindings.sortedWith(findingComparator),
            observedFindings = observedFindings,
            expectedCompletenessGaps = truth.expectedCompletenessGaps,
            observedCompletenessGaps = observedGaps,
            metrics = metrics,
            baseline = baseline.analyze(manifest),
            classification = classification,
            reviewed = truth.reviewed,
            reviewNote = truth.reviewNote,
            rationale = truth.rationale,
            discrepancyExplanation = truth.falseResultExplanation,
            stdout = normalizeOutput(stdout, repo, manifest.parent),
            stderr = normalizeOutput(stderr, repo, manifest.parent),
        )
    }

    private fun verifyFrozenCore(repo: Path) {
        val diff = ProcessBuilder("git", "diff", "--quiet", "v0.1.0", "--", "retrylint-cli/src/main")
            .directory(repo.toFile()).start()
        require(diff.waitFor() == 0) { "frozen analyzer core differs from v0.1.0; benchmark aborted" }
        val untracked = ProcessBuilder(
            "git", "ls-files", "--others", "--exclude-standard", "--", "retrylint-cli/src/main",
        ).directory(repo.toFile()).start()
        val untrackedFiles = untracked.inputStream.bufferedReader().readText().trim()
        require(untracked.waitFor() == 0 && untrackedFiles.isBlank()) {
            "frozen analyzer core contains untracked files: $untrackedFiles"
        }
    }

    private fun materializeMutant(seed: Path, mutant: Path, destination: Path): Path {
        copyTree(seed, destination)
        val overlay = mutant.resolve("overlay")
        require(overlay.isDirectory()) { "mutant overlay is missing: $overlay" }
        copyTree(overlay, destination)
        return destination
    }

    private fun copyTree(source: Path, destination: Path) {
        Files.walk(source).use { stream -> stream.forEach { path ->
            val target = destination.resolve(source.relativize(path).toString())
            if (Files.isDirectory(path)) target.createDirectories()
            else {
                target.parent.createDirectories()
                Files.copy(path, target, StandardCopyOption.REPLACE_EXISTING)
            }
        } }
    }

    private fun recreateDirectory(directory: Path) {
        if (Files.exists(directory)) {
            Files.walk(directory).sorted(Comparator.reverseOrder()).forEach(Files::delete)
        }
        directory.createDirectories()
    }

    private fun findingKey(node: JsonNode) = FindingKey(
        ruleId = node.path("ruleId").asText(),
        severity = node.path("severity").asText(),
        callPath = stringList(node.path("callPath")),
        targetOperation = node.path("evidence").path("targetOperation").asText(),
    )

    private fun stringList(node: JsonNode): List<String> = node.map(JsonNode::asText)

    private fun normalizeOutput(value: String, repo: Path, caseDirectory: Path): String = value
        .replace(caseDirectory.toAbsolutePath().normalize().toString(), "<case>")
        .replace(repo.toAbsolutePath().normalize().toString(), "<repo>")
        .replace('\\', '/')
        .trim()

    private fun renderSummary(summary: BenchmarkSummary): String = buildString {
        appendLine("# RetryLint Week 9 mutation benchmark")
        appendLine()
        appendLine("- RetryLint: ${summary.retryLintVersion}")
        appendLine("- Corpus: ${summary.seedCount} safe seeds + ${summary.mutantCount} mutants = ${summary.totalCaseCount} cases")
        appendLine("- Executed/skipped: ${summary.executedCaseCount}/${summary.skippedCaseCount}")
        appendLine("- Valid / invalid / partial: ${summary.validFullAnalysisCount} / ${summary.invalidRejectedCount} / ${summary.partialAnalysisCount}")
        appendLine("- Scoring key: `${summary.scoringUnit}`")
        appendLine("- TP / FP / FN: ${summary.overall.tp} / ${summary.overall.fp} / ${summary.overall.fn}")
        appendLine("- Precision / recall / F1: ${metric(summary.overall.precision)} / ${metric(summary.overall.recall)} / ${metric(summary.overall.f1)}")
        appendLine("- Baseline local issues: ${summary.baselineIssueCount}; architecture-level findings: none by design")
        appendLine()
        appendLine("## Per-rule metrics")
        appendLine()
        appendLine("| Rule | TP | FP | FN | Precision | Recall | F1 |")
        appendLine("|---|---:|---:|---:|---:|---:|---:|")
        summary.perRule.forEach { row ->
            appendLine("| ${row.ruleId} | ${row.metrics.tp} | ${row.metrics.fp} | ${row.metrics.fn} | ${metric(row.metrics.precision)} | ${metric(row.metrics.recall)} | ${metric(row.metrics.f1)} |")
        }
        appendLine()
        appendLine("## Mutation operators")
        appendLine()
        summary.mutationOperatorDistribution.forEach { (operator, count) -> appendLine("- $operator: $count") }
        appendLine()
        appendLine("## False results")
        appendLine()
        if (summary.falseResults.isEmpty()) appendLine("None.") else summary.falseResults.forEach { appendLine("- $it") }
        appendLine()
        appendLine("Invalid/rejected and partially analyzed cases are reported separately and do not enter TP/FP/FN. Completeness gaps are not findings. Undefined zero-denominator metrics are `null` in JSON and `n/a` here.")
    }

    private fun renderMutantTable(cases: List<CaseResult>): String = buildString {
        appendLine("# Week 9 mutant review table")
        appendLine()
        appendLine("| Case | Seed | Mutation(s) | Expected status | Expected findings | Observed RetryLint | Observed baseline | Classification | Reviewed |")
        appendLine("|---|---|---|---|---|---|---|---|---|")
        cases.filter { it.caseId != it.seed }.forEach { result ->
            val expected = result.expectedFindings.joinToString("<br>") { "${it.ruleId}/${it.severity}:${it.callPath.joinToString("→")}:${it.targetOperation}" }.ifBlank { "none" }
            val observed = result.observedFindings.joinToString("<br>") { "${it.ruleId}/${it.severity}:${it.callPath.joinToString("→")}:${it.targetOperation}" }.ifBlank { result.observedStatus }
            val baselineText = result.baseline.issues.joinToString("<br>") { "${it.kind}:${it.serviceId}" }.ifBlank { "none" }
            appendLine("| ${result.caseId} | ${result.seed} | ${result.mutations.joinToString(", ")} | ${result.expectedStatus} | $expected | $observed | $baselineText | ${result.classification} | ${result.reviewed} |")
        }
    }

    private fun metric(value: Double?): String = value?.let { "%.3f".format(java.util.Locale.ROOT, it) } ?: "n/a"

    companion object {
        val findingComparator = compareBy<FindingKey>(
            { it.ruleId }, { it.severity }, { it.callPath.joinToString("\u0000") }, { it.targetOperation },
        )

        fun classify(expected: List<FindingKey>, observed: List<FindingKey>): Metrics {
            val expectedSet = expected.toSet()
            val observedSet = observed.toSet()
            return Metrics(
                tp = expectedSet.intersect(observedSet).size,
                fp = observedSet.subtract(expectedSet).size,
                fn = expectedSet.subtract(observedSet).size,
            )
        }

        fun classifyStatus(exitCode: Int, completenessGapCount: Int): String = when {
            exitCode != 0 -> "invalid"
            completenessGapCount > 0 -> "partially-analyzable"
            else -> "valid"
        }

        fun gapsMatch(expected: List<GapExpectation>, observed: List<ObservedGap>): Boolean =
            expected.size == observed.size && expected.all { wanted ->
                observed.any { actual ->
                    actual.ruleId == wanted.ruleId && actual.callPath == wanted.callPath &&
                        actual.reason.contains(wanted.reasonContains, ignoreCase = true)
                }
            }
    }
}

fun jsonMapper(): ObjectMapper = JsonMapper.builder()
    .addModule(KotlinModule.Builder().build())
    .enable(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY)
    .enable(SerializationFeature.INDENT_OUTPUT)
    .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
    .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
    .serializationInclusion(JsonInclude.Include.ALWAYS)
    .build()
