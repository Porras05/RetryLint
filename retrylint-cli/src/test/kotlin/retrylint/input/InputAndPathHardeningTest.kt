package retrylint.input

import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.io.TempDir
import retrylint.config.ConfigurationException
import retrylint.graph.TopologyValidationException
import retrylint.graph.TopologyValidator
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class InputAndPathHardeningTest {
    @TempDir
    lateinit var temporaryDirectory: Path

    @Test
    fun `manifest file size is bounded before YAML parsing`() {
        val manifest = temporaryDirectory.resolve("large.yml")
        Files.write(manifest, ByteArray((InputLimits.MAX_MANIFEST_BYTES + 1).toInt()))

        val error = assertFailsWith<java.io.IOException> { TopologyManifestParser().parse(manifest) }
        assertTrue(error.message.orEmpty().contains("byte limit"))
    }

    @Test
    fun `service configuration size is bounded`() {
        val manifest = createManifest("application.yml")
        Files.write(
            temporaryDirectory.resolve("application.yml"),
            ByteArray((InputLimits.MAX_SERVICE_CONFIG_BYTES + 1).toInt()),
        )

        val error = assertFailsWith<ConfigurationException> { RetryLintProjectLoader().load(manifest) }
        assertTrue(error.message.orEmpty().contains("byte limit"))
    }

    @Test
    fun `absolute traversal missing and directory configuration paths fail`() {
        val validator = TopologyValidator()
        listOf("../application.yml", temporaryDirectory.resolve("outside.yml").toString()).forEach { path ->
            val parsed = minimalManifest(path)
            assertFailsWith<TopologyValidationException> { validator.validate(parsed) }
        }

        val missing = createManifest("missing.yml")
        assertFailsWith<ConfigurationException> { RetryLintProjectLoader().load(missing) }
        val directory = temporaryDirectory.resolve("config-directory").also(Files::createDirectory)
        assertFailsWith<ConfigurationException> { RetryLintProjectLoader().load(createManifest(directory.fileName.toString())) }
    }

    @Test
    fun `UTF-8 configuration filename resolves inside manifest directory`() {
        temporaryDirectory.resolve("configuración.yml").writeText("resilience4j: {}")
        val project = RetryLintProjectLoader().load(createManifest("configuración.yml"))

        assertEquals(Path.of("configuración.yml"), project.configuration.services.getValue("service").source)
    }

    @Test
    fun `symbolic-link configuration is rejected when platform permits creating it`() {
        val outside = temporaryDirectory.parent.resolve("outside-${System.nanoTime()}.yml")
        outside.writeText("resilience4j: {}")
        val link = temporaryDirectory.resolve("linked.yml")
        try {
            Files.createSymbolicLink(link, outside)
        } catch (_: Exception) {
            assumeTrue(false, "symbolic links are unavailable on this Windows environment")
        }

        val error = assertFailsWith<ConfigurationException> {
            RetryLintProjectLoader().load(createManifest("linked.yml"))
        }
        assertTrue(error.message.orEmpty().contains("symbolic links"))
        Files.deleteIfExists(outside)
    }

    private fun createManifest(config: String): Path = temporaryDirectory.resolve("retrylint.yml").also {
        it.writeText(
            """
            version: 1
            name: path-test
            services: [{ id: service, config: "$config" }]
            operations: [{ id: service.op, service: service, idempotency: idempotent }]
            calls: []
            analysis: { roots: [service.op] }
            """.trimIndent(),
        )
    }

    private fun minimalManifest(config: String) = TopologyManifest(
        1,
        "path-test",
        listOf(ServiceDeclaration("service", config)),
        listOf(OperationDeclaration("service.op", "service", Idempotency.IDEMPOTENT)),
        emptyList(),
        AnalysisDeclaration(listOf("service.op")),
    )
}
