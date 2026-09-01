package retrylint.input

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals

class TopologyManifestParserTest {
    @Test
    fun `parses the complete topology example`() {
        val resource = requireNotNull(javaClass.getResource("/fixtures/complete-example/retrylint.yml"))

        val manifest = TopologyManifestParser().parse(Path.of(resource.toURI()))

        assertEquals(1, manifest.version)
        assertEquals("checkout-demo", manifest.name)
        assertEquals(4, manifest.services.size)
        assertEquals(4, manifest.operations.size)
        assertEquals(3, manifest.calls.size)
        assertEquals(Idempotency.NON_IDEMPOTENT, manifest.operations[2].idempotency)
        assertEquals("orders-client", manifest.calls.first().timeLimiter)
        assertEquals(listOf("checkout.place-order"), manifest.analysis.roots)
        assertEquals(9, manifest.analysis.amplificationWarning)
        assertEquals(27, manifest.analysis.amplificationError)
        assertEquals(FailureThreshold.ERROR, manifest.analysis.failOn)
    }
}
