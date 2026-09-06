package retrylint.config

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import retrylint.model.AspectOrder
import retrylint.model.Resolution
import retrylint.model.ResolvedRetry
import retrylint.model.ResolvedServiceConfiguration
import retrylint.model.ResolvedTimeLimiter
import retrylint.input.InputLimits
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import kotlin.io.path.inputStream

class ServiceConfigurationResolver {
    private val mapper = ObjectMapper(YAMLFactory())

    fun resolve(serviceId: String, path: Path): ResolvedServiceConfiguration {
        if (!Files.isRegularFile(path)) {
            throw ConfigurationException("configuration for service '$serviceId' is not a regular file: '$path'")
        }
        if (Files.size(path) > InputLimits.MAX_SERVICE_CONFIG_BYTES) {
            throw ConfigurationException(
                "configuration for service '$serviceId' exceeds ${InputLimits.MAX_SERVICE_CONFIG_BYTES} byte limit: '$path'",
            )
        }
        val root = try {
            path.inputStream().use(mapper::readTree)
        } catch (exception: Exception) {
            throw ConfigurationException(
                "cannot read configuration for service '$serviceId' at '$path': ${exception.message}",
                exception,
            )
        }
        val resilience4j = objectField(root, "resilience4j", "configuration root", required = false)
        val retry = objectField(resilience4j, "retry", "resilience4j", required = false)
        val timeLimiter = objectField(resilience4j, "timelimiter", "resilience4j", required = false)

        val customOrders = mutableListOf<String>()
        if (supportedField(retry, "retryAspectOrder", "retry-aspect-order", "resilience4j.retry") != null) {
            customOrders += "retry aspect order"
        }
        if (
            supportedField(
                timeLimiter,
                "timeLimiterAspectOrder",
                "time-limiter-aspect-order",
                "resilience4j.timelimiter",
            ) != null
        ) {
            customOrders += "time-limiter aspect order"
        }
        val aspectOrder = if (customOrders.isEmpty()) {
            Resolution.Known(AspectOrder.DEFAULT_RETRY_WRAPS_TIME_LIMITER)
        } else {
            Resolution.Unsupported("custom ${customOrders.joinToString(" and ")} is not supported")
        }

        return ResolvedServiceConfiguration(
            serviceId = serviceId,
            source = path,
            retries = resolveRetries(retry, path),
            timeLimiters = resolveTimeLimiters(timeLimiter, path),
            aspectOrder = aspectOrder,
        )
    }

    private fun resolveRetries(section: JsonNode?, source: Path): Map<String, ResolvedRetry> {
        val configs = objectField(section, "configs", "resilience4j.retry", required = false)
        val instances = objectField(section, "instances", "resilience4j.retry", required = false)
        val defaultNode = childObject(configs, "default", "resilience4j.retry.configs", required = false)
        val namedConfigs = namedObjects(configs, "default", "resilience4j.retry.configs")

        namedConfigs.keys.forEach { name ->
            resolveRetryConfig(name, namedConfigs, defaultNode, source, emptyList())
        }

        return namedObjects(instances, null, "resilience4j.retry.instances").mapValues { (name, node) ->
            val baseName = textField(node, "baseConfig", "base-config", "retry instance '$name'")
            val inherited = when (baseName) {
                null, "default" -> defaultRetry(defaultNode, source)
                else -> resolveRetryConfig(baseName, namedConfigs, defaultNode, source, emptyList())
            }
            applyRetryOverrides(inherited, node, "retry instance '$name'", source).copy(name = name)
        }
    }

    private fun resolveRetryConfig(
        name: String,
        configs: Map<String, JsonNode>,
        defaultNode: JsonNode?,
        source: Path,
        chain: List<String>,
    ): ResolvedRetry {
        if (name == "default") return defaultRetry(defaultNode, source)
        if (name in chain) {
            throw ConfigurationException(
                "retry baseConfig cycle in '$source': ${(chain + name).joinToString(" -> ")}",
            )
        }
        if (chain.size >= MAX_BASE_DEPTH) {
            throw ConfigurationException("retry baseConfig chain exceeds depth $MAX_BASE_DEPTH in '$source'")
        }
        val node = configs[name]
            ?: throw ConfigurationException("unknown retry baseConfig '$name' in '$source'")
        val baseName = textField(node, "baseConfig", "base-config", "retry config '$name'")
        val inherited = when (baseName) {
            null, "default" -> defaultRetry(defaultNode, source)
            else -> resolveRetryConfig(baseName, configs, defaultNode, source, chain + name)
        }
        return applyRetryOverrides(inherited, node, "retry config '$name'", source)
    }

    private fun defaultRetry(defaultNode: JsonNode?, source: Path): ResolvedRetry =
        applyRetryOverrides(
            ResolvedRetry(
                name = null,
                maxAttempts = Resilience4jDefaults.RETRY_MAX_ATTEMPTS,
                fixedWait = Resolution.Known(Resilience4jDefaults.RETRY_WAIT_DURATION),
            ),
            defaultNode,
            "retry config 'default'",
            source,
        )

    private fun applyRetryOverrides(
        inherited: ResolvedRetry,
        node: JsonNode?,
        context: String,
        source: Path,
    ): ResolvedRetry {
        if (node == null) return inherited
        val maxAttemptsNode = supportedField(node, "maxAttempts", "max-attempts", context)
        val maxAttempts = maxAttemptsNode?.let { positiveInt(it, "maxAttempts", context, source) }
            ?: inherited.maxAttempts
        val waitNode = supportedField(node, "waitDuration", "wait-duration", context)
        var fixedWait = if (inherited.fixedWait is Resolution.Unsupported) {
            inherited.fixedWait
        } else {
            waitNode?.let {
                val duration = duration(it, "waitDuration", context, source)
                if (duration.isNegative) {
                    throw ConfigurationException("waitDuration must not be negative in $context at '$source'")
                }
                Resolution.Known(duration)
            } ?: inherited.fixedWait
        }

        unsupportedWaitReason(node, context)?.let { reason -> fixedWait = Resolution.Unsupported(reason) }
        return inherited.copy(maxAttempts = maxAttempts, fixedWait = fixedWait)
    }

    private fun resolveTimeLimiters(section: JsonNode?, source: Path): Map<String, ResolvedTimeLimiter> {
        val configs = objectField(section, "configs", "resilience4j.timelimiter", required = false)
        val instances = objectField(section, "instances", "resilience4j.timelimiter", required = false)
        val defaultNode = childObject(configs, "default", "resilience4j.timelimiter.configs", required = false)
        val namedConfigs = namedObjects(configs, "default", "resilience4j.timelimiter.configs")

        namedConfigs.keys.forEach { name ->
            resolveTimeLimiterConfig(name, namedConfigs, defaultNode, source, emptyList())
        }

        return namedObjects(instances, null, "resilience4j.timelimiter.instances").mapValues { (name, node) ->
            val baseName = textField(node, "baseConfig", "base-config", "time-limiter instance '$name'")
            val inherited = when (baseName) {
                null, "default" -> defaultTimeLimiter(defaultNode, source)
                else -> resolveTimeLimiterConfig(baseName, namedConfigs, defaultNode, source, emptyList())
            }
            applyTimeLimiterOverrides(inherited, node, "time-limiter instance '$name'", source).copy(name = name)
        }
    }

    private fun resolveTimeLimiterConfig(
        name: String,
        configs: Map<String, JsonNode>,
        defaultNode: JsonNode?,
        source: Path,
        chain: List<String>,
    ): ResolvedTimeLimiter {
        if (name == "default") return defaultTimeLimiter(defaultNode, source)
        if (name in chain) {
            throw ConfigurationException(
                "time-limiter baseConfig cycle in '$source': ${(chain + name).joinToString(" -> ")}",
            )
        }
        if (chain.size >= MAX_BASE_DEPTH) {
            throw ConfigurationException("time-limiter baseConfig chain exceeds depth $MAX_BASE_DEPTH in '$source'")
        }
        val node = configs[name]
            ?: throw ConfigurationException("unknown time-limiter baseConfig '$name' in '$source'")
        val baseName = textField(node, "baseConfig", "base-config", "time-limiter config '$name'")
        val inherited = when (baseName) {
            null, "default" -> defaultTimeLimiter(defaultNode, source)
            else -> resolveTimeLimiterConfig(baseName, configs, defaultNode, source, chain + name)
        }
        return applyTimeLimiterOverrides(inherited, node, "time-limiter config '$name'", source)
    }

    private fun defaultTimeLimiter(defaultNode: JsonNode?, source: Path): ResolvedTimeLimiter =
        applyTimeLimiterOverrides(
            ResolvedTimeLimiter(
                name = "default",
                timeout = Resolution.Known(Resilience4jDefaults.TIME_LIMITER_TIMEOUT_DURATION),
            ),
            defaultNode,
            "time-limiter config 'default'",
            source,
        )

    private fun applyTimeLimiterOverrides(
        inherited: ResolvedTimeLimiter,
        node: JsonNode?,
        context: String,
        source: Path,
    ): ResolvedTimeLimiter {
        if (node == null) return inherited
        val timeoutNode = supportedField(node, "timeoutDuration", "timeout-duration", context)
        val timeout = timeoutNode?.let {
            val parsed = duration(it, "timeoutDuration", context, source)
            if (parsed.isZero || parsed.isNegative) {
                throw ConfigurationException("timeoutDuration must be positive in $context at '$source'")
            }
            Resolution.Known(parsed)
        } ?: inherited.timeout
        return inherited.copy(timeout = timeout)
    }

    private fun unsupportedWaitReason(node: JsonNode, context: String): String? {
        val reasons = mutableListOf<String>()
        val exponential = supportedField(
            node,
            "enableExponentialBackoff",
            "enable-exponential-backoff",
            context,
        )
        if (exponential?.asBoolean(false) == true) reasons += "exponential backoff"
        if (supportedField(node, "exponentialBackoffMultiplier", "exponential-backoff-multiplier", context) != null) {
            reasons += "exponential backoff"
        }
        val randomized = supportedField(node, "enableRandomizedWait", "enable-randomized-wait", context)
        if (randomized?.asBoolean(false) == true) reasons += "randomized wait"
        if (supportedField(node, "randomizedWaitFactor", "randomized-wait-factor", context) != null) {
            reasons += "randomized wait"
        }
        if (supportedField(node, "intervalFunction", "interval-function", context) != null) {
            reasons += "intervalFunction"
        }
        if (supportedField(node, "intervalBiFunction", "interval-bi-function", context) != null) {
            reasons += "intervalBiFunction"
        }
        return reasons.distinct().takeIf { it.isNotEmpty() }
            ?.joinToString(prefix = "unsupported retry timing in $context: ")
    }

    private fun duration(node: JsonNode, field: String, context: String, source: Path): Duration {
        if (!node.isTextual) {
            throw ConfigurationException("$field must be a duration string in $context at '$source'")
        }
        return try {
            DurationParser.parse(node.textValue())
        } catch (exception: ConfigurationException) {
            throw ConfigurationException("${exception.message} in $context at '$source'", exception)
        }
    }

    private fun positiveInt(node: JsonNode, field: String, context: String, source: Path): Int {
        if (!node.isIntegralNumber || !node.canConvertToInt() || node.intValue() < 1) {
            throw ConfigurationException("$field must be a positive integer in $context at '$source'")
        }
        return node.intValue()
    }

    private fun textField(node: JsonNode, camel: String, kebab: String, context: String): String? {
        val value = supportedField(node, camel, kebab, context) ?: return null
        if (!value.isTextual || value.textValue().isBlank()) {
            throw ConfigurationException("$camel must be a non-blank string in $context")
        }
        return value.textValue()
    }

    private fun supportedField(node: JsonNode?, camel: String, kebab: String, context: String): JsonNode? {
        if (node == null) return null
        val hasCamel = node.has(camel)
        val hasKebab = node.has(kebab)
        if (hasCamel && hasKebab) {
            throw ConfigurationException("$context declares both '$camel' and '$kebab'")
        }
        return when {
            hasCamel -> node.get(camel)
            hasKebab -> node.get(kebab)
            else -> null
        }
    }

    private fun objectField(node: JsonNode?, name: String, context: String, required: Boolean): JsonNode? {
        if (node == null || !node.has(name)) {
            if (required) throw ConfigurationException("missing '$name' in $context")
            return null
        }
        val value = node.get(name)
        if (!value.isObject) throw ConfigurationException("'$name' must be an object in $context")
        return value
    }

    private fun childObject(node: JsonNode?, name: String, context: String, required: Boolean): JsonNode? {
        if (node == null || !node.has(name)) {
            if (required) throw ConfigurationException("missing '$name' in $context")
            return null
        }
        val value = node.get(name)
        if (!value.isObject) throw ConfigurationException("'$name' must be an object in $context")
        return value
    }

    private fun namedObjects(node: JsonNode?, excluded: String?, context: String): Map<String, JsonNode> {
        if (node == null) return emptyMap()
        val result = linkedMapOf<String, JsonNode>()
        node.fields().forEach { (name, value) ->
            if (name != excluded) {
                if (name.length > InputLimits.MAX_IDENTIFIER_LENGTH) {
                    throw ConfigurationException("policy name in $context exceeds length limit ${InputLimits.MAX_IDENTIFIER_LENGTH}")
                }
                if (!value.isObject) throw ConfigurationException("'$name' must be an object in $context")
                result[name] = value
            }
        }
        if (result.size > InputLimits.MAX_NAMED_POLICIES_PER_SECTION) {
            throw ConfigurationException(
                "$context contains ${result.size} policies; limit is ${InputLimits.MAX_NAMED_POLICIES_PER_SECTION}",
            )
        }
        return result
    }

    private companion object {
        const val MAX_BASE_DEPTH = InputLimits.MAX_BASE_CONFIG_DEPTH
    }
}
