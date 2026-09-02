package retrylint.cli

import picocli.CommandLine
import java.io.PrintWriter
import java.io.StringWriter
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals

class ValidateCommandTest {
    @Test
    fun `complete example exits successfully`() {
        val result = executeFixture("complete-example")

        assertEquals(0, result.exitCode)
        assertContains(result.output, "VALID: checkout-demo")
    }

    @Test
    fun `invalid fixtures exit with input error and clear diagnostics`() {
        val expectations = mapOf(
            "duplicate-id" to "duplicate service ID 'service-a'",
            "missing-reference" to "unknown target operation 'service-b.missing'",
            "cycle" to "service-a.call -> service-b.call -> service-c.call -> service-a.call",
        )

        expectations.forEach { (fixture, expectedMessage) ->
            val result = executeFixture(fixture)
            assertEquals(2, result.exitCode, fixture)
            assertContains(result.error, "INVALID_TOPOLOGY:", message = fixture)
            assertContains(result.error, expectedMessage, message = fixture)
        }
    }

    private fun executeFixture(name: String): CommandResult {
        val output = StringWriter()
        val error = StringWriter()
        val commandLine = CommandLine(RetryLintCommand())
            .setOut(PrintWriter(output, true))
            .setErr(PrintWriter(error, true))
        val path = Path.of(requireNotNull(javaClass.getResource("/fixtures/$name/retrylint.yml")).toURI())

        val exitCode = commandLine.execute("validate", path.toString())

        return CommandResult(exitCode, output.toString(), error.toString())
    }

    private data class CommandResult(val exitCode: Int, val output: String, val error: String)
}
