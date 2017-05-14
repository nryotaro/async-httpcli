package org.nryotaro.handler

import java.time.Duration

interface SslExceptionHandler {

    fun onHandshakeFailure(cause: Throwable)

}
