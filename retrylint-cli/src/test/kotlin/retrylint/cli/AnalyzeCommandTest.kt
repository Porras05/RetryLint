package retrylint.cli

import com.fasterxml.jackson.databind.ObjectMapper
import picocli.CommandLine
import retrylint.analysis.AnalysisService
import java.io.PrintWriter
import java.io.StringWriter
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.jupiter.api.io.TempDir

class AnalyzeCommandTest {
    @TempDir
    lateinit var temporaryDirectory: Path

    @Test
    fun `default text report contains all rules and evidence`() {
        val result = executeFixture("complete-example")

        assertEquals(1, result.exitCode)
        assertContains(result.output, "RetryLint — checkout-demo")
        assertContains(result.output, "ERROR RL001")
        assertContains(result.output, "RL002")
        assertContains(result.output, "RL003")
        assertContains(result.output, "3 x 3 x 3 = 27")
        assertContains(result.output, "Calls:")
        assertContains(result.output, "configurationFiles:")
        assertEquals("", result.error)
    }

    @Test
    fun `json report is valid JSON with the same rule facts`() {
        val result = executeFixture("complete-example", "--format", "json", "--fail-on", "never")

        assertEquals(0, result.exitCode)
        val root = ObjectMapper().readTree(result.output)
        assertEquals(1, root["schemaVersion"].intValue())
        assertEquals("checkout-demo", root["project"]["name"].textValue())
        val ruleIds = root["findings"].map { it["ruleId"].textValue() }.toSet()
        assertEquals(setOf("RL001", "RL002", "RL003"), ruleIds)
        val amplification = root["findings"].first { it["ruleId"].textValue() == "RL001" }
        assertEquals("27", amplification["evidence"]["multiplier"].textValue())
        assertFalse(result.output.contains("RetryLint —"))
    }

    @Test
    fun `mixed fixture renders RL002 and RL003`() {
        val result = executeFixture("week5-mixed", "--fail-on", "never")

        assertEquals(0, result.exitCode)
        assertContains(result.output, "RL002")
        assertContains(result.output, "1300 ms")
        assertContains(result.output, "RL003")
        assertContains(result.output, "may invoke service.c up to 3 times")
    }

    @Test
    fun `unsupported timing reports its gap while supported findings continue`() {
        val result = executeFixture("rl002-unsupported", "--format", "json", "--fail-on", "never")
        val root = ObjectMapper().readTree(result.output)

        assertEquals(0, result.exitCode)
        assertTrue(root["completenessGaps"].any { it["ruleId"].textValue() == "RL002" })
        assertTrue(root["completenessGaps"].any { it["reason"].textValue().contains("exponential") })
        assertTrue(root["findings"].any { it["ruleId"].textValue() == "RL002" })
        assertTrue(root["findings"].any { it["ruleId"].textValue() == "RL003" })
    }

    @Test
    fun `safe fixture completes without findings`() {
        val result = executeFixture("rl001-safe")

        assertEquals(0, result.exitCode)
        assertContains(result.output, "0 errors, 0 warnings")
        assertFalse(result.output.contains("ERROR RL"))
        assertFalse(result.output.contains("WARNING RL"))
    }

    @Test
    fun `failure thresholds distinguish warnings errors and never`() {
        assertEquals(0, executeFixture("rl001-branching").exitCode)
        assertEquals(1, executeFixture("rl001-branching", "--fail-on", "warning").exitCode)
        assertEquals(1, executeFixture("complete-example", "--fail-on", "error").exitCode)
        assertEquals(0, executeFixture("complete-example", "--fail-on", "never").exitCode)
    }

    @Test
    fun `output option writes deterministic UTF-8 JSON without report text on stdout`() {
        val destination = temporaryDirectory.resolve("report.json")
        val first = executeFixture(
            "week5-mixed",
            "--format", "json",
            "--output", destination.toString(),
            "--fail-on", "never",
        )
        val firstContents = destination.readText(StandardCharsets.UTF_8)
        val second = executeFixture(
            "week5-mixed",
            "--format", "json",
            "--output", destination.toString(),
            "--fail-on", "never",
        )

        assertEquals(0, first.exitCode)
        assertEquals("", first.output)
        assertEquals(0, second.exitCode)
        assertEquals(firstContents, destination.readText(StandardCharsets.UTF_8))
        ObjectMapper().readTree(firstContents)

        val textDestination = temporaryDirectory.resolve("report.txt")
        val textResult = executeFixture(
            "rl001-safe",
            "--output", textDestination.toString(),
            "--no-color",
            "--verbose",
        )
        assertEquals(0, textResult.exitCode)
        assertEquals("", textResult.output)
        val textContents = textDestination.readText(StandardCharsets.UTF_8)
        assertTrue(textContents.startsWith("RetryLint — rl001-safe"))
        assertContains(textContents, "Topological order:")
    }

    @Test
    fun `invalid topology malformed input and invalid configuration return code two`() {
        assertEquals(2, executeFixture("cycle").exitCode)
        assertContains(executeFixture("cycle").error, "INVALID_TOPOLOGY")

        val malformed = temporaryDirectory.resolve("malformed.yml").also { it.writeText("version: [") }
        assertEquals(2, executePath(malformed).exitCode)

        val invalidConfiguration = createInvalidConfigurationFixture()
        val result = executePath(invalidConfiguration)
        assertEquals(2, result.exitCode)
        assertContains(result.error, "INVALID_CONFIGURATION")
    }

    @Test
    fun `invalid options and unwritable output return code two`() {
        assertEquals(2, executeFixture("rl001-safe", "--format", "xml").exitCode)
        assertEquals(2, executeFixture("rl001-safe", "--fail-on", "sometimes").exitCode)
        assertEquals(2, executeFixture("rl001-safe", "--unknown-option").exitCode)
        val directoryDestination = temporaryDirectory.resolve("directory").also(Files::createDirectory)
        val result = executeFixture("rl001-safe", "--output", directoryDestination.toString())
        assertEquals(2, result.exitCode)
        assertContains(result.error, "INVALID_INPUT")
    }

    @Test
    fun `unexpected failures return code three`() {
        val output = StringWriter()
        val error = StringWriter()
        val command = AnalyzeCommand(AnalysisService { error("unexpected test failure") })
        val commandLine = CommandLine(command)
            .setOut(PrintWriter(output, true))
            .setErr(PrintWriter(error, true))

        val exitCode = commandLine.execute(fixturePath("rl001-safe").toString())

        assertEquals(3, exitCode)
        assertContains(error.toString(), "INTERNAL_ERROR")
    }

    @Test
    fun `version command reports the application version`() {
        val result = executeRoot("version")

        assertEquals(0, result.exitCode)
        assertEquals("RetryLint 0.1.0-SNAPSHOT", result.output.trim())
    }

    private fun createInvalidConfigurationFixture(): Path {
        temporaryDirectory.resolve("application.yml").writeText(
            """
            resilience4j:
              retry:
                instances:
                  actual: {}
            """.trimIndent(),
        )
        return temporaryDirectory.resolve("retrylint.yml").also {
            it.writeText(
                """
                version: 1
                name: invalid-configuration
                services:
                  - id: service
                    config: application.yml
                operations:
                  - id: service.a
                    service: service
                    idempotency: idempotent
                  - id: service.b
                    service: service
                    idempotency: idempotent
                calls:
                  - id: a-to-b
                    from: service.a
                    to: service.b
                    retry: missing
                analysis:
                  roots: [service.a]
                """.trimIndent(),
            )
        }
    }

    private fun executeFixture(name: String, vararg options: String): CommandResult =
        executeRoot("analyze", fixturePath(name).toString(), *options)

    private fun executePath(path: Path): CommandResult = executeRoot("analyze", path.toString())

    private fun fixturePath(name: String): Path =
        Path.of(requireNotNull(javaClass.getResource("/fixtures/$name/retrylint.yml")).toURI())

    private fun executeRoot(vararg arguments: String): CommandResult {
        val output = StringWriter()
        val error = StringWriter()
        val commandLine = CommandLine(RetryLintCommand())
            .setOut(PrintWriter(output, true))
            .setErr(PrintWriter(error, true))
        val exitCode = commandLine.execute(*arguments)
        return CommandResult(exitCode, output.toString(), error.toString())
    }

    private data class CommandResult(val exitCode: Int, val output: String, val error: String)
}
