package retrylint.config

import java.time.Duration
import java.time.format.DateTimeParseException

object DurationParser {
    private val simpleDuration = Regex("^(\\d+)(ms|s|m)$")

    fun parse(value: String): Duration {
        val text = value.trim()
        val match = simpleDuration.matchEntire(text)
        if (match != null) {
            val amount = try {
                match.groupValues[1].toLong()
            } catch (exception: NumberFormatException) {
                throw ConfigurationException("invalid duration '$value'", exception)
            }
            return when (match.groupValues[2]) {
                "ms" -> Duration.ofMillis(amount)
                "s" -> Duration.ofSeconds(amount)
                "m" -> Duration.ofMinutes(amount)
                else -> error("unreachable duration unit")
            }
        }

        return try {
            Duration.parse(text)
        } catch (exception: DateTimeParseException) {
            throw ConfigurationException(
                "invalid duration '$value'; expected formats such as 100ms, 2s, 1m, or ISO-8601",
                exception,
            )
        }
    }
}
