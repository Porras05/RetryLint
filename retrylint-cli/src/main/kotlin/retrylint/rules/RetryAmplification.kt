package retrylint.rules

import java.math.BigInteger

fun retryAmplification(vararg attempts: Int): BigInteger =
    attempts.fold(BigInteger.ONE) { amplification, attemptCount ->
        amplification * attemptCount.toBigInteger()
    }
