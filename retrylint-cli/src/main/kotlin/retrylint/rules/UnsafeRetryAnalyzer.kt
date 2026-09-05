package retrylint.rules

import retrylint.input.Idempotency
import retrylint.input.LoadedRetryLintProject
import retrylint.model.Finding
import retrylint.model.RuleAnalysisResult
import retrylint.model.Severity

class UnsafeRetryAnalyzer {
    fun analyze(project: LoadedRetryLintProject): RuleAnalysisResult {
        val findings = project.manifest.calls.mapNotNull { call ->
            val policies = project.configuration.calls.getValue(call.id)
            if (policies.retry.maxAttempts <= 1) return@mapNotNull null

            val target = project.topology.operationsById.getValue(call.to)
            val severity = when (target.idempotency) {
                Idempotency.IDEMPOTENT -> return@mapNotNull null
                Idempotency.UNKNOWN -> Severity.WARNING
                Idempotency.NON_IDEMPOTENT -> Severity.ERROR
            }
            val idempotency = target.idempotency.name.lowercase().replace('_', '-')
            Finding(
                ruleId = "RL003",
                severity = severity,
                title = "${target.id} may be repeated",
                message =
                    "Call '${call.id}' may invoke ${target.id} up to " +
                        "${policies.retry.maxAttempts} times; idempotency is $idempotency.",
                callPath = listOf(call.id),
                evidence = linkedMapOf(
                    "callId" to call.id,
                    "targetOperation" to target.id,
                    "idempotency" to idempotency,
                    "maxAttempts" to policies.retry.maxAttempts,
                    "configurationFile" to policies.configurationSource.toString(),
                ),
            )
        }
        return RuleAnalysisResult(findings = findings)
    }
}
