package retrylint.testbed

import org.springframework.context.annotation.Profile
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@Profile("testbed")
@RequestMapping("/test")
class TestbedAdminController(private val counter: InvocationCounter) {
    @PostMapping("/reset")
    fun reset(): CounterSnapshot = counter.reset()

    @GetMapping("/counters")
    fun counters(@RequestParam(required = false) traceId: String? = null): CounterSnapshot =
        counter.snapshot(traceId)
}
