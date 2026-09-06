package retrylint.testbed

import io.github.resilience4j.retry.Retry
import io.github.resilience4j.retry.RetryRegistry
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient

fun interface DownstreamInvoker {
    fun invoke(traceId: String)
}

@Component
class DownstreamClient(
    restClientBuilder: RestClient.Builder,
    private val retryRegistry: RetryRegistry,
    private val properties: TestbedProperties,
) : DownstreamInvoker {
    private val restClient = restClientBuilder.build()

    override fun invoke(traceId: String) {
        val downstreamUrl = properties.downstreamUrl?.takeIf { it.isNotBlank() } ?: return
        val policyName = properties.retryPolicy?.takeIf { it.isNotBlank() }
            ?: throw IllegalStateException("A downstream retry policy is required for ${properties.serviceId}")
        val retry = retryRegistry.retry(policyName)
        val call = Retry.decorateCheckedSupplier(retry) {
            restClient.post()
                .uri(downstreamUrl)
                .header(TRACE_ID_HEADER, traceId)
                .retrieve()
                .toBodilessEntity()
            Unit
        }
        call.get()
    }
}
