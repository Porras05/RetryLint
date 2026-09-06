package retrylint.cli

import picocli.CommandLine
import picocli.CommandLine.Command
import picocli.CommandLine.Option
import picocli.CommandLine.Parameters
import picocli.CommandLine.Spec
import picocli.CommandLine.Model.CommandSpec
import retrylint.config.ConfigurationException
import retrylint.analysis.AnalysisService
import retrylint.analysis.RetryLintAnalyzer
import retrylint.graph.TopologyValidationException
import retrylint.input.RetryLintProjectLoader
import retrylint.input.FailureThreshold
import retrylint.model.Severity
import retrylint.report.JsonAnalysisReportRenderer
import retrylint.report.OutputFormat
import retrylint.report.TextAnalysisReportRenderer
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.Callable
import kotlin.system.exitProcess

@Command(
    name = "retrylint",
    description = ["Analyze composed retry and timeout configurations."],
    mixinStandardHelpOptions = true,
    version = ["RetryLint 0.1.0"],
    subcommands = [ValidateCommand::class, AnalyzeCommand::class, VersionCommand::class],
)
class RetryLintCommand : Runnable {
    @Spec
    lateinit var spec: CommandSpec

    override fun run() {
        spec.commandLine().usage(spec.commandLine().out)
    }
}

@Command(name = "analyze", description = ["Analyze a RetryLint project."])
class AnalyzeCommand(
    private val analysisService: AnalysisService = RetryLintAnalyzer(),
) : Callable<Int> {
    @Parameters(index = "0", paramLabel = "retrylint.yml")
    lateinit var manifestPath: Path

    @Option(names = ["--format"], description = ["Output format: text or json."])
    var format: String = "text"

    @Option(names = ["--output"], paramLabel = "file", description = ["Write the report to a UTF-8 file."])
    var outputPath: Path? = null

    @Option(names = ["--fail-on"], description = ["Failure threshold: warning, error, or never."])
    var failOn: String? = null

    @Option(names = ["--no-color"], description = ["Disable colored output."])
    var noColor: Boolean = false

    @Option(names = ["--verbose"], description = ["Include additional deterministic text diagnostics."])
    var verbose: Boolean = false

    @Spec
    lateinit var spec: CommandSpec

    override fun call(): Int {
        val selectedFormat = parseFormat(format) ?: return usageError("invalid --format '$format'; expected text or json")
        val thresholdOverride = failOn?.let {
            parseThreshold(it) ?: return usageError("invalid --fail-on '$it'; expected warning, error, or never")
        }

        return try {
            val report = analysisService.analyze(manifestPath)
            val renderer = when (selectedFormat) {
                OutputFormat.TEXT -> TextAnalysisReportRenderer()
                OutputFormat.JSON -> JsonAnalysisReportRenderer()
            }
            val rendered = renderer.render(report, verbose)
            val destination = outputPath
            if (destination == null) {
                spec.commandLine().out.println(rendered)
            } else {
                Files.writeString(destination, rendered + System.lineSeparator(), StandardCharsets.UTF_8)
            }

            val threshold = thresholdOverride ?: report.project.manifest.analysis.failOn
            if (meetsThreshold(report.findings.map { it.severity }, threshold)) 1 else 0
        } catch (exception: TopologyValidationException) {
            inputError("INVALID_TOPOLOGY", exception)
        } catch (exception: ConfigurationException) {
            inputError("INVALID_CONFIGURATION", exception)
        } catch (exception: IOException) {
            inputError("INVALID_INPUT", exception)
        } catch (exception: Exception) {
            spec.commandLine().err.println("INTERNAL_ERROR: ${exception.message ?: exception::class.simpleName}")
            3
        }
    }

    private fun usageError(message: String): Int {
        spec.commandLine().err.println("INVALID_OPTION: $message")
        return 2
    }

    private fun inputError(label: String, exception: Exception): Int {
        spec.commandLine().err.println("$label: ${exception.message ?: exception::class.simpleName}")
        return 2
    }

    private fun parseFormat(value: String): OutputFormat? =
        OutputFormat.entries.firstOrNull { it.name.equals(value, ignoreCase = true) }

    private fun parseThreshold(value: String): FailureThreshold? =
        FailureThreshold.entries.firstOrNull { it.name.equals(value, ignoreCase = true) }

    private fun meetsThreshold(severities: List<Severity>, threshold: FailureThreshold): Boolean = when (threshold) {
        FailureThreshold.NEVER -> false
        FailureThreshold.ERROR -> severities.any { it == Severity.ERROR }
        FailureThreshold.WARNING -> severities.isNotEmpty()
    }
}

@Command(name = "version", description = ["Print the RetryLint version."])
class VersionCommand : Callable<Int> {
    @Spec
    lateinit var spec: CommandSpec

    override fun call(): Int {
        spec.commandLine().out.println("RetryLint 0.1.0")
        return 0
    }
}

@Command(name = "validate", description = ["Validate a RetryLint topology manifest."])
class ValidateCommand : Callable<Int> {
    @Parameters(index = "0", paramLabel = "retrylint.yml")
    lateinit var manifestPath: Path

    @Spec
    lateinit var spec: CommandSpec

    override fun call(): Int {
        return try {
            val project = RetryLintProjectLoader().load(manifestPath)
            spec.commandLine().out.println(
                "VALID: ${project.manifest.name} " +
                    "(${project.topology.servicesById.size} services, " +
                    "${project.topology.operationsById.size} operations, " +
                    "${project.topology.callsById.size} calls)",
            )
            0
        } catch (exception: TopologyValidationException) {
            spec.commandLine().err.println("INVALID_TOPOLOGY: ${exception.message ?: exception::class.simpleName}")
            2
        } catch (exception: ConfigurationException) {
            spec.commandLine().err.println("INVALID_CONFIGURATION: ${exception.message}")
            2
        } catch (exception: Exception) {
            spec.commandLine().err.println("INVALID_INPUT: ${exception.message ?: exception::class.simpleName}")
            2
        }
    }
}

fun main(args: Array<String>) {
    exitProcess(CommandLine(RetryLintCommand()).execute(*args))
}
