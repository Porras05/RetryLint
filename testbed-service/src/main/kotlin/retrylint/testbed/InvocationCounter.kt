package retrylint.testbed

import org.springframework.stereotype.Component
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

data class CounterSnapshot(
    val serviceId: String,
    val total: Long,
    val traceId: String? = null,
    val traceCount: Long? = null,
    val byTrace: Map<String, Long>,
)

@Component
class InvocationCounter(private val properties: TestbedProperties) {
    private val total = AtomicLong()
    private val byTrace = ConcurrentHashMap<String, AtomicLong>()

    fun record(traceId: String): Long {
        total.incrementAndGet()
        return byTrace.computeIfAbsent(traceId) { AtomicLong() }.incrementAndGet()
    }

    fun snapshot(traceId: String? = null): CounterSnapshot =
        CounterSnapshot(
            serviceId = properties.serviceId,
            total = total.get(),
            traceId = traceId,
            traceCount = traceId?.let { byTrace[it]?.get() ?: 0L },
            byTrace = byTrace.entries
                .associate { it.key to it.value.get() }
                .toSortedMap(),
        )

    fun reset(): CounterSnapshot {
        total.set(0)
        byTrace.clear()
        return snapshot()
    }
}
