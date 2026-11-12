package retrylint.testbed

import io.github.resilience4j.core.functions.Either
import io.github.resilience4j.retry.RetryConfig
import io.github.resilience4j.retry.RetryRegistry
import io.github.resilience4j.timelimiter.TimeLimiterRegistry
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.boot.SpringApplication
import org.springframework.boot.WebApplicationType
import retrylint.config.ServiceConfigurationResolver
import retrylint.model.Resolution
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.absolute
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Contract between RetryLint's static resolver and the real Spring Boot + Resilience4j binding.
 *
 * Each scenario file is loaded twice: once by Spring Boot (resilience4j-spring-boot3), which builds the
 * real RetryRegistry/TimeLimiterRegistry, and once by RetryLint's ServiceConfigurationResolver. Every
 * retry and time-limiter instance resolved by RetryLint must have the same maxAttempts, fixed wait and
 * timeout as the registry built by the library. All mismatches are reported together.
 */
class SpringIntegrationContractTest {
    @ParameterizedTest(name = "{0}")
    @ValueSource(
        strings = [
            "contract/default-and-named-bases.yml",
            "contract/named-chain-without-default.yml",
            "contract/kebab-case-keys.yml",
            "../../retrylint-cli/src/test/resources/fixtures/complete-example/services/checkout/application.yml",
            "../../retrylint-cli/src/test/resources/fixtures/complete-example/services/orders/application.yml",
            "../../retrylint-cli/src/test/resources/fixtures/complete-example/services/payments/application.yml",
            "../../retrylint-cli/src/test/resources/fixtures/config-multilevel-base/application.yml",
            "../../retrylint-cli/src/test/resources/fixtures/rl003-inherited-attempts/application.yml",
        ],
    )
    fun `RetryLint resolution matches the Spring Boot Resilience4j registries`(scenario: String) {
        val file = scenarioPath(scenario)
        assertTrue(Files.isRegularFile(file), "Missing scenario file: $file")

        val expected = ServiceConfigurationResolver().resolve("contract", file)
        assertTrue(
            expected.retries.isNotEmpty() || expected.timeLimiters.isNotEmpty(),
            "Scenario $scenario declares no Resilience4j instances",
        )

        val mismatches = mutableListOf<String>()
        val application = SpringApplication(TestbedApplication::class.java).apply {
            webApplicationType = WebApplicationType.NONE
            setDefaultProperties(
                mapOf(
                    "spring.config.additional-location" to file.toUri().toString(),
                    "retrylint.testbed.service-id" to "contract",
                ),
            )
        }

        application.run().use { context ->
            val retryRegistry = context.getBean(RetryRegistry::class.java)
            expected.retries.forEach { (name, resolved) ->
                val actual = retryRegistry.retry(name).retryConfig
                if (actual.maxAttempts != resolved.maxAttempts) {
                    mismatches += "retry '$name' maxAttempts: RetryLint=${resolved.maxAttempts}, Spring=${actual.maxAttempts}"
                }
                val wait = resolved.fixedWait
                if (wait is Resolution.Known) {
                    val actualWait = firstInterval(actual)
                    if (actualWait != wait.value.toMillis()) {
                        mismatches += "retry '$name' waitDuration: RetryLint=${wait.value.toMillis()}ms, Spring=${actualWait}ms"
                    }
                }
            }

            val timeLimiterRegistry = context.getBean(TimeLimiterRegistry::class.java)
            expected.timeLimiters.forEach { (name, resolved) ->
                val actual = timeLimiterRegistry.timeLimiter(name).timeLimiterConfig.timeoutDuration
                val timeout = resolved.timeout
                if (timeout is Resolution.Known && actual != timeout.value) {
                    mismatches += "time limiter '$name' timeoutDuration: RetryLint=${timeout.value}, Spring=$actual"
                }
            }
        }

        if (mismatches.isNotEmpty()) {
            fail("Contract mismatches for $scenario:\n" + mismatches.joinToString("\n"))
        }
    }

    private fun firstInterval(config: RetryConfig): Long =
        config.getIntervalBiFunction<Any>().apply(1, Either.right<Throwable, Any>(Any()))

    private fun scenarioPath(scenario: String): Path {
        val moduleRoot = findModuleRoot()
        return if (scenario.startsWith("contract/")) {
            moduleRoot.resolve("src/test/resources").resolve(scenario).normalize()
        } else {
            moduleRoot.resolve(scenario.removePrefix("../")).normalize()
        }
    }

    /** Returns testbed-service/ regardless of whether Gradle runs from the module or the repository root. */
    private fun findModuleRoot(): Path {
        var current = Path.of(System.getProperty("user.dir")).absolute().normalize()
        while (!Files.isRegularFile(current.resolve("settings.gradle.kts"))) {
            current = current.parent ?: error("Could not find RetryLint repository root")
        }
        return current.resolve("testbed-service")
    }
}
