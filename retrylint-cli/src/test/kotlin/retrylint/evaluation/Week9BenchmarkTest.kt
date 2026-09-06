package retrylint.evaluation

import java.nio.file.Files
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class Week9BenchmarkTest {
    @Test
    fun `finding key matching is rule severity path and target aware`() {
        val expected = FindingKey("RL003", "warning", listOf("a-to-b"), "b")
        assertEquals(Metrics(1, 0, 0), Week9BenchmarkRunner.classify(listOf(expected), listOf(expected)))
        val wrongRule = expected.copy(ruleId = "RL001")
        assertEquals(Metrics(0, 1, 1), Week9BenchmarkRunner.classify(listOf(expected), listOf(wrongRule)))
    }

    @Test
    fun `metric arithmetic and zero denominators are explicit`() {
        val metrics = Metrics(tp = 3, fp = 1, fn = 2)
        assertEquals(0.75, metrics.precision)
        assertEquals(0.6, metrics.recall)
        assertEquals(2.0 * 0.75 * 0.6 / 1.35, metrics.f1)
        assertNull(Metrics(0, 0, 0).precision)
        assertNull(Metrics(0, 0, 0).recall)
        assertNull(Metrics(0, 0, 0).f1)
    }

    @Test
    fun `gap matching distinguishes partial analysis from a finding`() {
        val expected = listOf(GapExpectation("RL002", listOf("a", "b"), "exponential backoff"))
        val observed = listOf(ObservedGap("RL002", listOf("a", "b"), "fixed wait unsupported: exponential backoff"))
        assertTrue(Week9BenchmarkRunner.gapsMatch(expected, observed))
        assertFalse(Week9BenchmarkRunner.gapsMatch(expected, emptyList()))
    }

    @Test
    fun `loader rejects duplicate IDs before execution`() {
        val root = minimalDefinition(reviewed = true, duplicate = true)
        val error = assertFailsWith<IllegalArgumentException> { BenchmarkDefinitionLoader().load(root) }
        assertTrue(error.message.orEmpty().contains("duplicate benchmark case IDs"))
    }

    @Test
    fun `loader rejects missing truth file`() {
        val root = minimalDefinition(reviewed = true)
        Files.delete(root.resolve("seeds/seed01/truth.json"))
        val error = assertFailsWith<IllegalArgumentException> { BenchmarkDefinitionLoader().load(root) }
        assertTrue(error.message.orEmpty().contains("missing truth file"))
    }

    @Test
    fun `loader rejects a missing indexed case directory`() {
        val root = minimalDefinition(reviewed = true)
        Files.walk(root.resolve("mutants/mut20"))
            .sorted(Comparator.reverseOrder())
            .forEach(Files::delete)
        val error = assertFailsWith<IllegalArgumentException> { BenchmarkDefinitionLoader().load(root) }
        assertTrue(error.message.orEmpty().contains("missing benchmark case directory"))
    }

    @Test
    fun `loader rejects unreviewed truth`() {
        val root = minimalDefinition(reviewed = false)
        val error = assertFailsWith<IllegalArgumentException> { BenchmarkDefinitionLoader().load(root) }
        assertTrue(error.message.orEmpty().contains("unreviewed truth"))
    }

    @Test
    fun `loader preserves explicit index ordering`() {
        val root = minimalDefinition(reviewed = true)
        val (_, loaded) = BenchmarkDefinitionLoader().load(root)
        assertEquals((1..10).map { "seed%02d".format(it) } + (1..20).map { "mut%02d".format(it) }, loaded.map { it.first.id })
    }

    @Test
    fun `invalid and partial statuses remain outside finding classification`() {
        assertEquals("invalid", Week9BenchmarkRunner.classifyStatus(exitCode = 2, completenessGapCount = 0))
        assertEquals("partially-analyzable", Week9BenchmarkRunner.classifyStatus(exitCode = 0, completenessGapCount = 1))
        assertEquals("valid", Week9BenchmarkRunner.classifyStatus(exitCode = 0, completenessGapCount = 0))
        assertEquals(Metrics(0, 0, 0), Week9BenchmarkRunner.classify(emptyList(), emptyList()))
    }

    private fun minimalDefinition(reviewed: Boolean, duplicate: Boolean = false): java.nio.file.Path {
        val root = Files.createTempDirectory("retrylint-week9-loader-")
        val seeds = (1..10).map { "seed%02d".format(it) }
        val mutants = (1..20).map { "mut%02d".format(it) }
        (seeds + mutants).forEach { id ->
            val kind = if (id.startsWith("seed")) "seeds" else "mutants"
            val directory = root.resolve("$kind/$id").also { it.createDirectories() }
            val seed = if (kind == "seeds") id else "seed01"
            directory.resolve("truth.json").writeText(
                """{"caseId":"$id","seed":"$seed","mutations":["test"],"expectedStatus":"valid","expectedFindings":[],"expectedCompletenessGaps":[],"rationale":"test","reviewed":$reviewed,"reviewNote":"reviewed for test"}""",
            )
            directory.resolve("retrylint.yml").writeText("version: 1")
            if (kind == "mutants") directory.resolve("overlay").createDirectories()
        }
        val seedEntries = seeds.map { id -> mapOf("id" to id, "path" to "seeds/$id", "truth" to "truth.json") }
        val mutantEntries = mutants.mapIndexed { index, id ->
            mapOf("id" to if (duplicate && index == 0) "seed01" else id, "path" to "mutants/$id", "truth" to "truth.json")
        }
        jsonMapper().writeValue(
            root.resolve("benchmark.json").toFile(),
            mapOf(
                "schemaVersion" to 1,
                "retryLintVersion" to "0.1.0",
                "expectedSeedCount" to 10,
                "expectedMutantCount" to 20,
                "seeds" to seedEntries,
                "mutants" to mutantEntries,
            ),
        )
        return root
    }
}
