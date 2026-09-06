package retrylint.config

import retrylint.graph.ValidatedTopology
import retrylint.input.TopologyManifest
import retrylint.model.Resolution
import retrylint.model.ResolvedCallPolicies
import retrylint.model.ResolvedProjectConfiguration
import retrylint.model.ResolvedRetry
import java.nio.file.Path
import java.nio.file.Files
import java.time.Duration

class ProjectConfigurationResolver(
    private val serviceResolver: ServiceConfigurationResolver = ServiceConfigurationResolver(),
) {
    fun resolve(
        manifestPath: Path,
        manifest: TopologyManifest,
        topology: ValidatedTopology,
    ): ResolvedProjectConfiguration {
        val normalizedManifest = manifestPath.toAbsolutePath().normalize()
        if (Files.isSymbolicLink(normalizedManifest)) {
            throw ConfigurationException("manifest must not be a symbolic link: '$manifestPath'")
        }
        val manifestDirectory = normalizedManifest.parent
            ?: throw ConfigurationException("manifest path has no parent directory: '$manifestPath'")

        val services = manifest.services.associate { service ->
            val requestedPath = manifestDirectory.resolve(service.config).normalize()
            if (!requestedPath.startsWith(manifestDirectory)) {
                throw ConfigurationException(
                    "service '${service.id}' configuration path escapes the manifest directory: '${service.config}'",
                )
            }
            val relativePath = manifestDirectory.relativize(requestedPath)
            var current = manifestDirectory
            relativePath.forEach { component ->
                current = current.resolve(component)
                if (Files.isSymbolicLink(current)) {
                    throw ConfigurationException(
                        "service '${service.id}' configuration path must not contain symbolic links: '${service.config}'",
                    )
                }
            }
            if (!Files.isRegularFile(requestedPath)) {
                throw ConfigurationException(
                    "service '${service.id}' configuration must be a regular file inside the manifest directory: '${service.config}'",
                )
            }
            service.id to serviceResolver.resolve(service.id, requestedPath).copy(
                source = Path.of(service.config).normalize(),
            )
        }

        val calls = manifest.calls.associate { call ->
            val callerOperation = topology.operationsById.getValue(call.from)
            val callerService = services.getValue(callerOperation.service)
            val retry = call.retry?.let { policyName ->
                callerService.retries[policyName]
                    ?: throw ConfigurationException(
                        "call '${call.id}' references unknown retry policy '$policyName' " +
                            "in caller service '${callerService.serviceId}' at '${callerService.source}'",
                    )
            } ?: ResolvedRetry(
                name = null,
                maxAttempts = 1,
                fixedWait = Resolution.Known(Duration.ZERO),
            )
            val timeLimiter = call.timeLimiter?.let { policyName ->
                callerService.timeLimiters[policyName]
                    ?: throw ConfigurationException(
                        "call '${call.id}' references unknown time-limiter policy '$policyName' " +
                            "in caller service '${callerService.serviceId}' at '${callerService.source}'",
                    )
            }

            call.id to ResolvedCallPolicies(
                callId = call.id,
                callerServiceId = callerService.serviceId,
                configurationSource = callerService.source,
                retry = retry,
                timeLimiter = timeLimiter,
                aspectOrder = callerService.aspectOrder,
            )
        }

        return ResolvedProjectConfiguration(services = services, calls = calls)
    }
}
