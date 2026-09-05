package retrylint.rules

import retrylint.input.CallDeclaration
import retrylint.input.Idempotency
import retrylint.input.LoadedRetryLintProject
import retrylint.model.CompletenessGap
import retrylint.model.Finding
import retrylint.model.Resolution
import retrylint.model.RuleAnalysisResult
import retrylint.model.Severity
import java.time.Duration

class TimeoutBudgetAnalyzer(
    private val maximumAdjacentPairsPerOperation: Long = 10_000,
) {
    fun analyze(project: LoadedRetryLintProject): RuleAnalysisResult {
        val incomingCalls = project.manifest.calls.groupBy { it.to }
        val findings = mutableListOf<Finding>()
        val gaps = mutableListOf<CompletenessGap>()

        project.topology.topologicalOrder.forEach { operation ->
            val incoming = incomingCalls[operation].orEmpty()
            val outgoing = project.topology.outgoingCalls.getValue(operation)
            val pairCount = incoming.size.toLong() * outgoing.size.toLong()
            if (pairCount > maximumAdjacentPairsPerOperation) {
                gaps += CompletenessGap(
                    ruleId = "RL002",
                    operationId = operation,
                    callPath = emptyList(),
                    reason = "skipped $pairCount adjacent call pairs; limit is $maximumAdjacentPairsPerOperation",
                )
                return@forEach
            }

            incoming.forEach { incomingCall ->
                outgoing.forEach { downstreamCall ->
                    analyzePair(project, operation, incomingCall, downstreamCall, findings, gaps)
                }
            }
        }

        return RuleAnalysisResult(findings = findings, completenessGaps = gaps)
    }

    private fun analyzePair(
        project: LoadedRetryLintProject,
        middleOperation: String,
        incomingCall: CallDeclaration,
        downstreamCall: CallDeclaration,
        findings: MutableList<Finding>,
        gaps: MutableList<CompletenessGap>,
    ) {
        val incomingPolicies = project.configuration.calls.getValue(incomingCall.id)
        val downstreamPolicies = project.configuration.calls.getValue(downstreamCall.id)
        val reasons = mutableListOf<String>()

        val incomingTimeout = knownDuration(
            incomingPolicies.timeLimiter?.timeout,
            "incoming call '${incomingCall.id}' has no time limiter",
            "incoming timeout",
            reasons,
        )
        val downstreamTimeout = knownDuration(
            downstreamPolicies.timeLimiter?.timeout,
            "downstream call '${downstreamCall.id}' has no time limiter",
            "downstream timeout",
            reasons,
        )
        val fixedWait = knownDuration(
            downstreamPolicies.retry.fixedWait,
            "downstream fixed wait is unknown",
            "downstream fixed wait",
            reasons,
        )
        requireSupportedAspectOrder("incoming", incomingPolicies.aspectOrder, reasons)
        requireSupportedAspectOrder("downstream", downstreamPolicies.aspectOrder, reasons)

        if (reasons.isNotEmpty()) {
            gaps += CompletenessGap(
                ruleId = "RL002",
                operationId = middleOperation,
                callPath = listOf(incomingCall.id, downstreamCall.id),
                reason = reasons.distinct().joinToString("; "),
            )
            return
        }

        val retryWindow = try {
            RetryWindowCalculator.calculate(
                attempts = downstreamPolicies.retry.maxAttempts,
                timeoutPerAttempt = requireNotNull(downstreamTimeout),
                fixedWait = requireNotNull(fixedWait),
            )
        } catch (exception: ArithmeticException) {
            gaps += CompletenessGap(
                ruleId = "RL002",
                operationId = middleOperation,
                callPath = listOf(incomingCall.id, downstreamCall.id),
                reason = "retry-window duration exceeds supported arithmetic range",
            )
            return
        }

        val callerBudget = requireNotNull(incomingTimeout)
        if (callerBudget >= retryWindow) return

        val target = project.topology.operationsById.getValue(downstreamCall.to)
        val severity = if (target.idempotency == Idempotency.NON_IDEMPOTENT) {
            Severity.ERROR
        } else {
            Severity.WARNING
        }
        val overrun = retryWindow.minus(callerBudget)
        findings += Finding(
            ruleId = "RL002",
            severity = severity,
            title = "Timeout budget may be exceeded by ${formatDuration(overrun)}",
            message =
                "Incoming call '${incomingCall.id}' waits ${formatDuration(callerBudget)}, but " +
                    "'${downstreamCall.id}' permits a configured worst-case retry window of " +
                    "${formatDuration(retryWindow)}.",
            callPath = listOf(incomingCall.id, downstreamCall.id),
            evidence = linkedMapOf(
                "middleOperation" to middleOperation,
                "targetOperation" to downstreamCall.to,
                "incomingTimeout" to callerBudget,
                "attempts" to downstreamPolicies.retry.maxAttempts,
                "timeoutPerAttempt" to downstreamTimeout,
                "fixedWait" to fixedWait,
                "retryWindow" to retryWindow,
                "overrun" to overrun,
                "incomingConfigurationFile" to incomingPolicies.configurationSource.toString(),
                "downstreamConfigurationFile" to downstreamPolicies.configurationSource.toString(),
            ),
        )
    }

    private fun knownDuration(
        resolution: Resolution<Duration>?,
        missingReason: String,
        label: String,
        reasons: MutableList<String>,
    ): Duration? = when (resolution) {
        null -> {
            reasons += missingReason
            null
        }
        is Resolution.Known -> resolution.value
        is Resolution.Unknown -> {
            reasons += "$label unknown: ${resolution.reason}"
            null
        }
        is Resolution.Unsupported -> {
            reasons += "$label unsupported: ${resolution.reason}"
            null
        }
    }

    private fun requireSupportedAspectOrder(
        label: String,
        resolution: Resolution<*>,
        reasons: MutableList<String>,
    ) {
        when (resolution) {
            is Resolution.Known -> Unit
            is Resolution.Unknown -> reasons += "$label aspect order unknown: ${resolution.reason}"
            is Resolution.Unsupported -> reasons += "$label aspect order unsupported: ${resolution.reason}"
        }
    }

    private fun formatDuration(duration: Duration): String =
        if (duration.nano % 1_000_000 == 0) "${duration.toMillis()} ms" else duration.toString()
}
