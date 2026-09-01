package retrylint.rules

import java.math.BigInteger
import kotlin.test.Test
import kotlin.test.assertEquals

class RetryAmplificationTest {
    @Test
    fun `three retry layers with three attempts amplify to twenty seven`() {
        val amplification = retryAmplification(3, 3, 3)

        assertEquals(BigInteger.valueOf(27), amplification)
    }
}
