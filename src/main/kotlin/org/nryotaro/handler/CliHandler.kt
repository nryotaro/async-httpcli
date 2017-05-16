package org.nryotaro.handler

import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.http.HttpContent
import io.netty.handler.codec.http.HttpResponse
import io.netty.handler.codec.http.LastHttpContent

interface CliHandler {

    fun onFailure(cause: Throwable)

    fun acceptHttpResponse(response: HttpResponse)

    fun acceptContent(msg: HttpContent)

    fun  acceptLastHttpContent(msg: LastHttpContent)

    fun  onException(ctx: ChannelHandlerContext, cause: Throwable)

}
