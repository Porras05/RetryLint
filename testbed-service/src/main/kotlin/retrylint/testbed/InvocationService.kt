package retrylint.testbed

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class InvocationService(
    private val properties: TestbedProperties,
    private val counter: InvocationCounter,
    private val downstream: DownstreamInvoker,
) {
    private val failureDecider = FailureDecider(properties)
    private val logger = LoggerFactory.getLogger(javaClass)

    fun invoke(traceId: String) {
        val traceSequence = counter.record(traceId)
        logger.info(
            "service={} traceId={} invocation={} event=received",
            properties.serviceId,
            traceId,
            traceSequence,
        )
        try {
            downstream.invoke(traceId)
            failureDecider.failIfConfigured(traceSequence)
            logger.info(
                "service={} traceId={} invocation={} event=succeeded",
                properties.serviceId,
                traceId,
                traceSequence,
            )
        } catch (failure: Exception) {
            logger.info(
                "service={} traceId={} invocation={} event=failed reason={}",
                properties.serviceId,
                traceId,
                traceSequence,
                failure.message ?: failure.javaClass.simpleName,
            )
            throw failure
        }
    }
}
