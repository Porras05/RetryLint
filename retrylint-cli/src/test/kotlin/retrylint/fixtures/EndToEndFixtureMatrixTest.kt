package retrylint.fixtures

import com.fasterxml.jackson.databind.ObjectMapper
import picocli.CommandLine
import retrylint.cli.RetryLintCommand
import java.io.PrintWriter
import java.io.StringWriter
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals

class EndToEndFixtureMatrixTest {
    private val mapper = ObjectMapper()

    @Test
    fun `all twenty five fixture projects match the frozen expectations`() {
        val fixtureRoot = resourcePath("/fixtures")
        val expectations = mapper.readTree(resourcePath("/fixtures/expected-results.json").toFile())
        val fixtureNames = Files.list(fixtureRoot).use { paths ->
            paths.filter(Files::isDirectory).map { it.fileName.toString() }.sorted().toList()
        }

        assertEquals(25, fixtureNames.size)
        assertEquals(fixtureNames, expectations.fieldNames().asSequence().toList().sorted())

        fixtureNames.forEach { name ->
            val expected = expectations[name]
            val result = execute(fixtureRoot.resolve(name).resolve("retrylint.yml"))
            assertEquals(expected["exitCode"].intValue(), result.exitCode, name)
            if (result.exitCode == 0) {
                val report = mapper.readTree(result.output)
                val actualFindings = report["findings"].map { it["ruleId"].textValue() }.sorted()
                val actualGaps = report["completenessGaps"].map { it["ruleId"].textValue() }.sorted()
                assertEquals(expected["findingRules"].map { it.textValue() }.sorted(), actualFindings, name)
                assertEquals(expected["gapRules"].map { it.textValue() }.sorted(), actualGaps, name)
            } else {
                assertContains(result.error, expected["errorContains"].textValue(), message = name)
            }
        }
    }

    private fun execute(manifest: Path): CommandResult {
        val output = StringWriter()
        val error = StringWriter()
        val commandLine = CommandLine(RetryLintCommand())
            .setOut(PrintWriter(output, true))
            .setErr(PrintWriter(error, true))
        val exitCode = commandLine.execute(
            "analyze", manifest.toString(), "--format", "json", "--fail-on", "never",
        )
        return CommandResult(exitCode, output.toString(), error.toString())
    }

    private fun resourcePath(name: String): Path = Path.of(requireNotNull(javaClass.getResource(name)).toURI())

    private data class CommandResult(val exitCode: Int, val output: String, val error: String)
}
