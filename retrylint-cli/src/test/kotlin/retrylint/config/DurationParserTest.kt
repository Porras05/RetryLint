package retrylint.config

import java.time.Duration
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class DurationParserTest {
    @Test
    fun `parses supported simple and ISO durations`() {
        assertEquals(Duration.ofMillis(100), DurationParser.parse("100ms"))
        assertEquals(Duration.ofSeconds(2), DurationParser.parse("2s"))
        assertEquals(Duration.ofMinutes(1), DurationParser.parse("1m"))
        assertEquals(Duration.ofMillis(1500), DurationParser.parse("PT1.5S"))
    }

    @Test
    fun `invalid duration fails clearly`() {
        val error = assertFailsWith<ConfigurationException> { DurationParser.parse("five seconds") }

        assertTrue(error.message.orEmpty().contains("invalid duration 'five seconds'"))
    }
}
