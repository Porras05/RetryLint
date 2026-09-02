package retrylint.cli

import picocli.CommandLine
import picocli.CommandLine.Command
import picocli.CommandLine.Parameters
import picocli.CommandLine.Spec
import picocli.CommandLine.Model.CommandSpec
import retrylint.config.ConfigurationException
import retrylint.graph.TopologyValidationException
import retrylint.input.RetryLintProjectLoader
import java.nio.file.Path
import java.util.concurrent.Callable
import kotlin.system.exitProcess

@Command(
    name = "retrylint",
    description = ["Analyze composed retry and timeout configurations."],
    mixinStandardHelpOptions = true,
    subcommands = [ValidateCommand::class],
)
class RetryLintCommand : Runnable {
    @Spec
    lateinit var spec: CommandSpec

    override fun run() {
        spec.commandLine().usage(spec.commandLine().out)
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
