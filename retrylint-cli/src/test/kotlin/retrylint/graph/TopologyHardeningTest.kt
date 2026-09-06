package retrylint.graph

import retrylint.input.AnalysisDeclaration
import retrylint.input.CallDeclaration
import retrylint.input.Idempotency
import retrylint.input.InputLimits
import retrylint.input.OperationDeclaration
import retrylint.input.ServiceDeclaration
import retrylint.input.TopologyManifest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class TopologyHardeningTest {
    private val validator = TopologyValidator()

    @Test
    fun `graph cardinality and identifier limits fail clearly`() {
        val tooManyServices = manifest().copy(
            services = (0..InputLimits.MAX_SERVICES).map { ServiceDeclaration("service-$it", "application.yml") },
        )
        assertTrue(assertFailsWith<TopologyValidationException> { validator.validate(tooManyServices) }
            .message.orEmpty().contains("services count"))

        val longId = "x".repeat(InputLimits.MAX_IDENTIFIER_LENGTH + 1)
        val excessiveId = manifest().copy(
            operations = listOf(OperationDeclaration(longId, "service", Idempotency.IDEMPOTENT)),
            analysis = AnalysisDeclaration(listOf(longId)),
        )
        assertTrue(assertFailsWith<TopologyValidationException> { validator.validate(excessiveId) }
            .message.orEmpty().contains("length limit"))
    }

    @Test
    fun `long valid DAG is iterative and deterministically ordered`() {
        val count = 2_000
        val operations = (0 until count).map { OperationDeclaration("op-$it", "service", Idempotency.IDEMPOTENT) }
        val calls = (0 until count - 1).map { CallDeclaration("call-$it", "op-$it", "op-${it + 1}") }
        val topology = validator.validate(manifest(operations, calls))

        assertEquals(operations.map { it.id }, topology.topologicalOrder)
    }

    @Test
    fun `long cycle is reconstructed without recursive stack growth`() {
        val count = 2_000
        val operations = (0 until count).map { OperationDeclaration("op-$it", "service", Idempotency.IDEMPOTENT) }
        val calls = (0 until count).map {
            CallDeclaration("call-$it", "op-$it", "op-${(it + 1) % count}")
        }
        val error = assertFailsWith<TopologyValidationException> { validator.validate(manifest(operations, calls)) }

        assertTrue(error.message.orEmpty().startsWith("synchronous call cycle detected: op-0 -> op-1"))
        assertTrue(error.message.orEmpty().endsWith("op-1999 -> op-0"))
    }

    @Test
    fun `disconnected wide graph and multiple roots retain declaration order`() {
        val operations = listOf("root-a", "root-b", "left", "right", "isolated").map {
            OperationDeclaration(it, "service", Idempotency.IDEMPOTENT)
        }
        val calls = listOf(
            CallDeclaration("a-left", "root-a", "left"),
            CallDeclaration("a-right", "root-a", "right"),
        )
        val topology = validator.validate(
            manifest(operations, calls).copy(analysis = AnalysisDeclaration(listOf("root-a", "root-b"))),
        )

        assertEquals(listOf("root-a", "root-b", "isolated", "left", "right"), topology.topologicalOrder)
    }

    private fun manifest(
        operations: List<OperationDeclaration> = listOf(
            OperationDeclaration("service.op", "service", Idempotency.IDEMPOTENT),
        ),
        calls: List<CallDeclaration> = emptyList(),
    ) = TopologyManifest(
        version = 1,
        name = "hardening",
        services = listOf(ServiceDeclaration("service", "application.yml")),
        operations = operations,
        calls = calls,
        analysis = AnalysisDeclaration(listOf(operations.first().id)),
    )
}
