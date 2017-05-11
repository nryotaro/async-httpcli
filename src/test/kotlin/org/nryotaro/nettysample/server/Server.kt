package org.nryotaro.nettysample.server

import io.netty.bootstrap.ServerBootstrap
import io.netty.buffer.ByteBuf
import io.netty.channel.*
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.SocketChannel
import io.netty.channel.socket.nio.NioServerSocketChannel
import io.netty.handler.codec.http.*
import io.netty.handler.ssl.SslHandler
import io.netty.handler.stream.ChunkedWriteHandler
import org.apache.http.HttpStatus
import java.net.InetSocketAddress
import java.io.RandomAccessFile



class Server {

    val bootstrap = ServerBootstrap()

    init {
        val group: EventLoopGroup = NioEventLoopGroup()
        bootstrap.group(group).channel(NioServerSocketChannel::class.java).localAddress(InetSocketAddress(8080))
                .childHandler(object: ChannelInitializer<SocketChannel>(){
                    override fun initChannel(channel: SocketChannel) {
                        val pipeline = channel.pipeline()
                        pipeline.addLast("decoder", HttpRequestDecoder())
                        pipeline.addLast("encoder", HttpResponseEncoder())
                        //pipeline.addLast("compressor", HttpContentCompressor())
                        pipeline.addLast(ChunkedWriteHandler()) // order
                        pipeline.addLast("simple", SimpleHandler())
                    }
                })
    }

    fun start() {
        bootstrap.bind().sync()
    }
}

class SimpleHandler: SimpleChannelInboundHandler<HttpObject>() {
    override fun channelRead0(ctx: ChannelHandlerContext, msg: HttpObject) {

        val file = RandomAccessFile("/Users/nryotaro/foobar.txt", "r")
        val resp = DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK)
        resp.headers().set(HttpHeaders.Names.CONTENT_TYPE, "text/plain; charset=UTF-8")

        // if keep alive
        resp.headers().set(HttpHeaders.Names.CONTENT_LENGTH, file.length())
        resp.headers().set( HttpHeaders.Names.CONNECTION, HttpHeaders.Values.KEEP_ALIVE)
        // end

        ctx.write(resp)
        ctx.write(DefaultFileRegion(file.channel, 0, file.length()))
        val future = ctx.writeAndFlush(LastHttpContent.EMPTY_LAST_CONTENT)
    }
}
fun main(args : Array<String>) {

    Server().start()
}

