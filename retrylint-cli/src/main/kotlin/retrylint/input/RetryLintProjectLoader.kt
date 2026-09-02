package retrylint.input

import retrylint.config.ProjectConfigurationResolver
import retrylint.graph.TopologyValidator
import retrylint.graph.ValidatedTopology
import retrylint.model.ResolvedProjectConfiguration
import java.nio.file.Path

data class LoadedRetryLintProject(
    val manifest: TopologyManifest,
    val topology: ValidatedTopology,
    val configuration: ResolvedProjectConfiguration,
)

class RetryLintProjectLoader(
    private val manifestParser: TopologyManifestParser = TopologyManifestParser(),
    private val topologyValidator: TopologyValidator = TopologyValidator(),
    private val configurationResolver: ProjectConfigurationResolver = ProjectConfigurationResolver(),
) {
    fun load(manifestPath: Path): LoadedRetryLintProject {
        val manifest = manifestParser.parse(manifestPath)
        val topology = topologyValidator.validate(manifest)
        val configuration = configurationResolver.resolve(manifestPath, manifest, topology)
        return LoadedRetryLintProject(manifest, topology, configuration)
    }
}
