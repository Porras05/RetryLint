package retrylint.graph

import retrylint.input.TopologyManifestParser
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class TopologyValidatorTest {
    private val parser = TopologyManifestParser()
    private val validator = TopologyValidator()

    @Test
    fun `complete example builds maps and passes topological sorting`() {
        val topology = validator.validate(parseFixture("complete-example"))

        assertEquals(4, topology.servicesById.size)
        assertEquals(4, topology.operationsById.size)
        assertEquals(3, topology.callsById.size)
        assertEquals(
            listOf(
                "checkout.place-order",
                "orders.create-order",
                "payments.charge",
                "bank.authorize",
            ),
            topology.topologicalOrder,
        )
    }

    @Test
    fun `duplicate service ID fails clearly`() {
        val error = assertFailsWith<TopologyValidationException> {
            validator.validate(parseFixture("duplicate-id"))
        }

        assertEquals("duplicate service ID 'service-a'", error.message)
    }

    @Test
    fun `missing operation reference fails clearly`() {
        val error = assertFailsWith<TopologyValidationException> {
            validator.validate(parseFixture("missing-reference"))
        }

        assertEquals(
            "call 'a-to-missing' references unknown target operation 'service-b.missing'",
            error.message,
        )
    }

    @Test
    fun `cycle fails with its concrete operation path`() {
        val error = assertFailsWith<TopologyValidationException> {
            validator.validate(parseFixture("cycle"))
        }

        assertTrue(
            error.message.orEmpty().contains(
                "service-a.call -> service-b.call -> service-c.call -> service-a.call",
            ),
        )
    }

    private fun parseFixture(name: String) =
        parser.parse(resourcePath("/fixtures/$name/retrylint.yml"))

    private fun resourcePath(name: String): Path =
        Path.of(requireNotNull(javaClass.getResource(name)).toURI())
}
