package retrylint.testbed

import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

const val TRACE_ID_HEADER = "X-RetryLint-Trace-Id"

data class InvocationResponse(
    val serviceId: String,
    val traceId: String,
    val status: String,
    val message: String? = null,
)

@RestController
@RequestMapping("/invoke")
class InvocationController(
    private val properties: TestbedProperties,
    private val invocationService: InvocationService,
) {
    @PostMapping
    fun invoke(
        @RequestHeader(TRACE_ID_HEADER, required = false) requestedTraceId: String?,
    ): ResponseEntity<InvocationResponse> {
        val traceId = requestedTraceId?.takeIf { it.isNotBlank() } ?: UUID.randomUUID().toString()
        return try {
            invocationService.invoke(traceId)
            ResponseEntity.ok()
                .header(TRACE_ID_HEADER, traceId)
                .body(InvocationResponse(properties.serviceId, traceId, "success"))
        } catch (failure: Exception) {
            ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .header(TRACE_ID_HEADER, traceId)
                .body(
                    InvocationResponse(
                        properties.serviceId,
                        traceId,
                        "failure",
                        failure.message ?: failure.javaClass.simpleName,
                    ),
                )
        }
    }
}
