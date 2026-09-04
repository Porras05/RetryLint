package retrylint.rules

import retrylint.input.LoadedRetryLintProject
import retrylint.model.Finding
import retrylint.model.Severity
import java.math.BigInteger
import java.nio.file.Path

data class AmplificationEdge(
    val callId: String,
    val from: String,
    val to: String,
    val attempts: Int,
    val configurationSource: Path,
)

data class AmplificationPath(
    val rootOperation: String,
    val targetOperation: String,
    val operations: List<String>,
    val edges: List<AmplificationEdge>,
    val multiplier: BigInteger,
) {
    val expression: String =
        if (edges.isEmpty()) "1 = 1" else edges.joinToString(" x ") { it.attempts.toString() } + " = $multiplier"
}

data class RetryAmplificationResult(
    val maximumPath: AmplificationPath,
    val finding: Finding?,
)

/**
 * Computes the greatest amplification reachable from the declared roots.
 * Equal candidates retain the path encountered first in manifest-derived
 * topological and outgoing-call order.
 */
class RetryAmplificationAnalyzer {
    fun analyze(project: LoadedRetryLintProject): RetryAmplificationResult {
        val best = linkedMapOf<String, BestPath>()
        project.manifest.analysis.roots.forEach { root ->
            best.putIfAbsent(root, BestPath(BigInteger.ONE, root, null))
        }

        project.topology.topologicalOrder.forEach { operation ->
            val sourcePath = best[operation] ?: return@forEach
            project.topology.outgoingCalls.getValue(operation).forEach { call ->
                val attempts = project.configuration.calls.getValue(call.id).retry.maxAttempts
                val candidate = sourcePath.multiplier * attempts.toBigInteger()
                val current = best[call.to]
                if (current == null || candidate > current.multiplier) {
                    best[call.to] = BestPath(candidate, sourcePath.rootOperation, call.id)
                }
            }
        }

        var target: String? = null
        project.topology.topologicalOrder.forEach { operation ->
            val candidate = best[operation] ?: return@forEach
            val currentTarget = target
            if (currentTarget == null || candidate.multiplier > best.getValue(currentTarget).multiplier) {
                target = operation
            }
        }
        val maximumTarget = target ?: error("validated topology has no reachable analysis root")
        val path = reconstructPath(project, maximumTarget, best)
        return RetryAmplificationResult(path, createFinding(project, path))
    }

    private fun reconstructPath(
        project: LoadedRetryLintProject,
        target: String,
        best: Map<String, BestPath>,
    ): AmplificationPath {
        val reversedEdges = mutableListOf<AmplificationEdge>()
        var operation = target
        while (true) {
            val predecessorCallId = best.getValue(operation).predecessorCallId ?: break
            val call = project.topology.callsById.getValue(predecessorCallId)
            val policies = project.configuration.calls.getValue(predecessorCallId)
            reversedEdges += AmplificationEdge(
                callId = call.id,
                from = call.from,
                to = call.to,
                attempts = policies.retry.maxAttempts,
                configurationSource = policies.configurationSource,
            )
            operation = call.from
        }
        val edges = reversedEdges.asReversed()
        val operations = buildList {
            add(best.getValue(target).rootOperation)
            edges.forEach { add(it.to) }
        }
        return AmplificationPath(
            rootOperation = best.getValue(target).rootOperation,
            targetOperation = target,
            operations = operations,
            edges = edges,
            multiplier = best.getValue(target).multiplier,
        )
    }

    private fun createFinding(
        project: LoadedRetryLintProject,
        path: AmplificationPath,
    ): Finding? {
        val warningThreshold = project.manifest.analysis.amplificationWarning.toBigInteger()
        val errorThreshold = project.manifest.analysis.amplificationError.toBigInteger()
        val severity = when {
            path.multiplier >= errorThreshold -> Severity.ERROR
            path.multiplier >= warningThreshold -> Severity.WARNING
            else -> return null
        }
        val consecutiveRetryLayers = maximumConsecutiveRetryLayers(path.edges)
        val ownershipSummary = if (consecutiveRetryLayers >= 2) {
            " ${layerCount(consecutiveRetryLayers)} layers independently own retries on this path."
        } else {
            ""
        }
        val evidence = linkedMapOf<String, Any>(
            "rootOperation" to path.rootOperation,
            "targetOperation" to path.targetOperation,
            "operationPath" to path.operations,
            "attempts" to path.edges.map { it.attempts },
            "expression" to path.expression,
            "multiplier" to path.multiplier,
            "configurationFiles" to path.edges.map { it.configurationSource.toString() },
        )
        return Finding(
            ruleId = "RL001",
            severity = severity,
            title = "Retry amplification of ${path.multiplier}x",
            message = "${path.operations.joinToString(" -> ")}: ${path.expression}.$ownershipSummary",
            callPath = path.edges.map { it.callId },
            evidence = evidence,
        )
    }

    private fun maximumConsecutiveRetryLayers(edges: List<AmplificationEdge>): Int {
        var maximum = 0
        var current = 0
        edges.forEach { edge ->
            current = if (edge.attempts > 1) current + 1 else 0
            maximum = maxOf(maximum, current)
        }
        return maximum
    }

    private fun layerCount(count: Int): String = when (count) {
        2 -> "Two"
        3 -> "Three"
        else -> count.toString()
    }

    private data class BestPath(
        val multiplier: BigInteger,
        val rootOperation: String,
        val predecessorCallId: String?,
    )
}
