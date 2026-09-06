package retrylint.testbed

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.runApplication

@SpringBootApplication
@EnableConfigurationProperties(TestbedProperties::class)
class TestbedApplication

fun main(args: Array<String>) {
    runApplication<TestbedApplication>(*args)
}
