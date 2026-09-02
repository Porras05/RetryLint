package retrylint.cli

import picocli.CommandLine
import picocli.CommandLine.Command
import picocli.CommandLine.Parameters
import picocli.CommandLine.Spec
import picocli.CommandLine.Model.CommandSpec
import retrylint.graph.TopologyValidator
import retrylint.input.TopologyManifestParser
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
            val manifest = TopologyManifestParser().parse(manifestPath)
            val topology = TopologyValidator().validate(manifest)
            spec.commandLine().out.println(
                "VALID: ${manifest.name} " +
                    "(${topology.servicesById.size} services, " +
                    "${topology.operationsById.size} operations, ${topology.callsById.size} calls)",
            )
            0
        } catch (exception: Exception) {
            spec.commandLine().err.println("INVALID_TOPOLOGY: ${exception.message ?: exception::class.simpleName}")
            2
        }
    }
}

fun main(args: Array<String>) {
    exitProcess(CommandLine(RetryLintCommand()).execute(*args))
}
