package retrylint.testbed

import io.github.resilience4j.core.functions.Either
import io.github.resilience4j.retry.RetryRegistry
import org.junit.jupiter.api.Test
import org.springframework.boot.SpringApplication
import org.springframework.boot.WebApplicationType
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.absolute
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FrozenConfigurationSmokeTest {
    @Test
    fun `Spring runtime loads the frozen checkout policy analyzed by RetryLint`() {
        val repositoryRoot = findRepositoryRoot()
        val frozenConfig = repositoryRoot.resolve(
            "retrylint-cli/src/test/resources/fixtures/complete-example/services/checkout/application.yml",
        )
        assertTrue(Files.isRegularFile(frozenConfig), "Missing frozen configuration: $frozenConfig")

        val application = SpringApplication(TestbedApplication::class.java).apply {
            webApplicationType = WebApplicationType.NONE
            setDefaultProperties(
                mapOf(
                    "spring.config.additional-location" to frozenConfig.toUri().toString(),
                    "retrylint.testbed.service-id" to "checkout",
                ),
            )
        }

        application.run().use { context ->
            val retry = context.getBean(RetryRegistry::class.java).retry("orders-client")
            assertEquals(3, retry.retryConfig.maxAttempts)
            assertEquals(
                100L,
                retry.retryConfig.getIntervalBiFunction<Any>().apply(
                    1,
                    Either.right<Throwable, Any>(Any()),
                ),
            )
        }
    }

    private fun findRepositoryRoot(): Path {
        var current = Path.of(System.getProperty("user.dir")).absolute().normalize()
        while (!Files.isRegularFile(current.resolve("settings.gradle.kts"))) {
            current = current.parent ?: error("Could not find RetryLint repository root")
        }
        return current
    }
}
