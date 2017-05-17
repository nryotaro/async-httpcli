package org.nryotaro.httpcli.handler;


import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.HttpContent;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.LastHttpContent;

public interface CliHandler {

    void onFailure(Throwable cause);

    void acceptHttpResponse(HttpResponse response);

    void acceptContent(HttpContent msg);

    void acceptLastHttpContent(LastHttpContent msg);

    void onException(ChannelHandlerContext ctx , Throwable cause);
}
