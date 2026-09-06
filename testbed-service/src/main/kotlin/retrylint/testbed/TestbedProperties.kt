package retrylint.testbed

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties("retrylint.testbed")
data class TestbedProperties(
    var serviceId: String = "testbed-service",
    var downstreamUrl: String? = null,
    var retryPolicy: String? = null,
    var failureMode: String = "success",
    var failFirstN: Int = 0,
)
