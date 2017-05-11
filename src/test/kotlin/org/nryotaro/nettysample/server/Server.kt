package org.nryotaro.nettysample.server

import io.netty.bootstrap.ServerBootstrap
import io.netty.buffer.ByteBuf
import io.netty.channel.*
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.SocketChannel
import io.netty.channel.socket.nio.NioServerSocketChannel
import io.netty.handler.codec.http.*
import io.netty.handler.ssl.SslContext
import io.netty.handler.ssl.SslHandler
import io.netty.handler.ssl.util.SelfSignedCertificate
import io.netty.handler.stream.ChunkedNioFile
import io.netty.handler.stream.ChunkedWriteHandler
import org.apache.http.HttpStatus
import java.net.InetSocketAddress
import java.io.RandomAccessFile
import javax.net.ssl.SSLEngine




/**
 *  test timeout ssl
 *  connection timeout
 *  file contents consistency
 *
 *  ssl
 */

class Server {

    val bootstrap = ServerBootstrap()

    init {
        val group: EventLoopGroup = NioEventLoopGroup()

        val cert: SelfSignedCertificate  = SelfSignedCertificate()

        val context: SslContext = SslContext.newServerContext(cert.certificate(), cert.privateKey())



        bootstrap.group(group).channel(NioServerSocketChannel::class.java).localAddress(InetSocketAddress(8080))
                .childHandler(object: ChannelInitializer<SocketChannel>(){
                    override fun initChannel(channel: SocketChannel) {
                        val pipeline = channel.pipeline()
                        if (context != null) {
                            pipeline.addLast("ssl", context.newHandler(channel.alloc()));
                        }
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

// Simple extends Channel inboudn handler
class SimpleHandler: SimpleChannelInboundHandler<HttpObject>() {
    override fun channelRead0(ctx: ChannelHandlerContext, msg: HttpObject) {

        if(msg is DefaultHttpRequest) {

        }
        if(msg is LastHttpContent) {
            val file = RandomAccessFile("/Users/nryotaro/foobar.txt", "r")
            val resp = DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK)
            resp.headers().set(HttpHeaders.Names.CONTENT_TYPE, "text/plain; charset=UTF-8")

            // if keep alive
            resp.headers().set(HttpHeaders.Names.CONTENT_LENGTH, file.length())
            resp.headers().set( HttpHeaders.Names.CONNECTION, HttpHeaders.Values.KEEP_ALIVE)
            // end

            ctx.write(resp)
            if (ctx.pipeline().get(SslHandler::class.java) == null) {
                ctx.write(DefaultFileRegion(
                        file.channel, 0, file.length()))
            } else {
                ctx.write(ChunkedNioFile(file.channel))
            }
            val future = ctx.writeAndFlush(LastHttpContent.EMPTY_LAST_CONTENT)
        }

    }
    override fun exceptionCaught(ctx: ChannelHandlerContext , cause: Throwable ){
        println("errror")
    }
}

class A: ChannelInboundHandlerAdapter() {

}
fun main(args : Array<String>) {

    Server().start()
}

