package retrylint.graph

import retrylint.input.CallDeclaration
import retrylint.input.TopologyManifest
import retrylint.input.InputLimits
import java.nio.file.InvalidPathException
import java.nio.file.Path
import java.util.ArrayDeque

class TopologyValidator {
    fun validate(manifest: TopologyManifest): ValidatedTopology {
        requireValidVersion(manifest)
        requireInputLimits(manifest)
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

    private fun requireInputLimits(manifest: TopologyManifest) {
        requireMaximum("services", manifest.services.size, InputLimits.MAX_SERVICES)
        requireMaximum("operations", manifest.operations.size, InputLimits.MAX_OPERATIONS)
        requireMaximum("calls", manifest.calls.size, InputLimits.MAX_CALLS)
        requireMaximum("analysis roots", manifest.analysis.roots.size, InputLimits.MAX_ROOTS)
        requireLength("project name", manifest.name, InputLimits.MAX_PROJECT_NAME_LENGTH)
        manifest.services.forEach {
            requireLength("service ID", it.id, InputLimits.MAX_IDENTIFIER_LENGTH)
            requireLength("service configuration path", it.config, InputLimits.MAX_CONFIG_PATH_LENGTH)
        }
        manifest.operations.forEach {
            requireLength("operation ID", it.id, InputLimits.MAX_IDENTIFIER_LENGTH)
            requireLength("operation service reference", it.service, InputLimits.MAX_IDENTIFIER_LENGTH)
        }
        manifest.calls.forEach {
            requireLength("call ID", it.id, InputLimits.MAX_IDENTIFIER_LENGTH)
            requireLength("call source operation", it.from, InputLimits.MAX_IDENTIFIER_LENGTH)
            requireLength("call target operation", it.to, InputLimits.MAX_IDENTIFIER_LENGTH)
            it.retry?.let { name -> requireLength("retry policy name", name, InputLimits.MAX_IDENTIFIER_LENGTH) }
            it.timeLimiter?.let { name ->
                requireLength("time-limiter policy name", name, InputLimits.MAX_IDENTIFIER_LENGTH)
            }
        }
        manifest.analysis.roots.forEach { requireLength("analysis root", it, InputLimits.MAX_IDENTIFIER_LENGTH) }
    }

    private fun requireMaximum(label: String, actual: Int, maximum: Int) {
        if (actual > maximum) invalid("$label count $actual exceeds limit $maximum")
    }

    private fun requireLength(label: String, value: String, maximum: Int) {
        if (value.length > maximum) invalid("$label exceeds length limit $maximum")
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
        operationIds.forEach { start ->
            if ((state[start] ?: 0) != 0) return@forEach
            val stack = ArrayDeque<CycleFrame>()
            val path = mutableListOf<String>()
            state[start] = 1
            stack.addLast(CycleFrame(start))
            path += start
            while (stack.isNotEmpty()) {
                val frame = stack.last()
                val edges = outgoing.getValue(frame.operation)
                if (frame.nextEdge >= edges.size) {
                    state[frame.operation] = 2
                    stack.removeLast()
                    path.removeAt(path.lastIndex)
                    continue
                }
                val target = edges[frame.nextEdge++].to
                when (state[target] ?: 0) {
                    0 -> {
                        state[target] = 1
                        stack.addLast(CycleFrame(target))
                        path += target
                    }
                    1 -> {
                        val cycleStart = path.indexOf(target)
                        return path.subList(cycleStart, path.size).toList() + target
                    }
                }
            }
        }
        error("cycle expected but none found")
    }

    private data class CycleFrame(val operation: String, var nextEdge: Int = 0)

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
