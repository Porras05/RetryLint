package retrylint.graph

import retrylint.input.CallDeclaration
import retrylint.input.TopologyManifest
import java.nio.file.InvalidPathException
import java.nio.file.Path
import java.util.ArrayDeque

class TopologyValidator {
    fun validate(manifest: TopologyManifest): ValidatedTopology {
        requireValidVersion(manifest)
        val services = uniqueById("service", manifest.services) { it.id }
        val operations = uniqueById("operation", manifest.operations) { it.id }
        val calls = uniqueById("call", manifest.calls) { it.id }

        manifest.services.forEach { service -> requireRelativeConfigPath(service.id, service.config) }
        manifest.operations.forEach { operation ->
            if (operation.service !in services) {
                invalid("operation '${operation.id}' references unknown service '${operation.service}'")
            }
        }
        manifest.calls.forEach { call ->
            if (call.from !in operations) {
                invalid("call '${call.id}' references unknown source operation '${call.from}'")
            }
            if (call.to !in operations) {
                invalid("call '${call.id}' references unknown target operation '${call.to}'")
            }
        }
        if (manifest.analysis.roots.isEmpty()) {
            invalid("at least one analysis root is required")
        }
        manifest.analysis.roots.forEach { root ->
            if (root !in operations) invalid("analysis root '$root' references an unknown operation")
        }

        val outgoing = operations.keys.associateWith { mutableListOf<CallDeclaration>() }
        manifest.calls.forEach { call -> outgoing.getValue(call.from).add(call) }
        val topologicalOrder = topologicalSort(operations.keys, manifest.calls, outgoing)

        return ValidatedTopology(
            servicesById = services,
            operationsById = operations,
            callsById = calls,
            outgoingCalls = outgoing.mapValues { it.value.toList() },
            topologicalOrder = topologicalOrder,
        )
    }

    private fun requireValidVersion(manifest: TopologyManifest) {
        if (manifest.version != 1) invalid("unsupported manifest version '${manifest.version}'; expected 1")
    }

    private fun requireRelativeConfigPath(serviceId: String, config: String) {
        val path = try {
            Path.of(config)
        } catch (_: InvalidPathException) {
            invalid("service '$serviceId' has an invalid configuration path '$config'")
        }
        if (config.isBlank() || path.isAbsolute || path.normalize().startsWith("..")) {
            invalid("service '$serviceId' configuration path must be relative to retrylint.yml: '$config'")
        }
    }

    private fun topologicalSort(
        operationIds: Set<String>,
        calls: List<CallDeclaration>,
        outgoing: Map<String, List<CallDeclaration>>,
    ): List<String> {
        val incomingCount = operationIds.associateWith { 0 }.toMutableMap()
        calls.forEach { call -> incomingCount[call.to] = incomingCount.getValue(call.to) + 1 }
        val ready = ArrayDeque(operationIds.filter { incomingCount.getValue(it) == 0 })
        val order = mutableListOf<String>()

        while (ready.isNotEmpty()) {
            val operation = ready.removeFirst()
            order += operation
            outgoing.getValue(operation).forEach { call ->
                val remaining = incomingCount.getValue(call.to) - 1
                incomingCount[call.to] = remaining
                if (remaining == 0) ready.addLast(call.to)
            }
        }

        if (order.size != operationIds.size) {
            val cycle = findCycle(operationIds, outgoing)
            invalid("synchronous call cycle detected: ${cycle.joinToString(" -> ")}")
        }
        return order
    }

    private fun findCycle(
        operationIds: Set<String>,
        outgoing: Map<String, List<CallDeclaration>>,
    ): List<String> {
        val state = mutableMapOf<String, Int>()
        val path = mutableListOf<String>()

        fun visit(operation: String): List<String>? {
            state[operation] = 1
            path += operation
            for (call in outgoing.getValue(operation)) {
                when (state[call.to] ?: 0) {
                    0 -> visit(call.to)?.let { return it }
                    1 -> {
                        val cycleStart = path.indexOf(call.to)
                        return path.subList(cycleStart, path.size).toList() + call.to
                    }
                }
            }
            path.removeAt(path.lastIndex)
            state[operation] = 2
            return null
        }

        operationIds.forEach { operation ->
            if ((state[operation] ?: 0) == 0) visit(operation)?.let { return it }
        }
        error("cycle expected but none found")
    }

    private fun <T> uniqueById(kind: String, values: List<T>, id: (T) -> String): Map<String, T> {
        val result = linkedMapOf<String, T>()
        values.forEach { value ->
            val valueId = id(value)
            if (valueId.isBlank()) invalid("$kind ID must not be blank")
            if (result.putIfAbsent(valueId, value) != null) invalid("duplicate $kind ID '$valueId'")
        }
        return result
    }

    private fun invalid(message: String): Nothing = throw TopologyValidationException(message)
}
