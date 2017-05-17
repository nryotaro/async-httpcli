package org.nryotaro.httpcli.handler;

public interface SslExceptionHandler {

    public void onHandshakeFailure(Throwable cause);
}
