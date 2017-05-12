package org.nryotaro.httplci

import io.netty.bootstrap.ServerBootstrap
import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled
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
import io.netty.util.CharsetUtil
import io.netty.util.concurrent.Future
import org.junit.Test
import org.nryotaro.httpcli.HttpCli
import java.io.RandomAccessFile
import java.net.InetSocketAddress
import java.nio.charset.StandardCharsets

class HttpCliTest {

    @Test
    fun getSuccessFully() {

        val server = TestServer()

        val chan = server.start()

        chan.closeFuture().sync()
        //val cli = HttpCli()
    }
}

class TestServer {
    val bootstrap = ServerBootstrap()
    val group: EventLoopGroup = NioEventLoopGroup()
    init {

        val cert: SelfSignedCertificate = SelfSignedCertificate()

        val context: SslContext = SslContext.newServerContext(cert.certificate(), cert.privateKey())

        bootstrap.group(group).channel(NioServerSocketChannel::class.java).localAddress(InetSocketAddress(8443))
                .childHandler(object: ChannelInitializer<SocketChannel>(){
                    override fun initChannel(channel: SocketChannel) {
                        val pipeline = channel.pipeline()
                        pipeline.addLast("ssl", context.newHandler(channel.alloc()));
                        pipeline.addLast("decoder", HttpRequestDecoder())
                        pipeline.addLast("encoder", HttpResponseEncoder())
                        //pipeline.addLast("compressor", HttpContentCompressor())
                        pipeline.addLast(ChunkedWriteHandler()) // order
                        pipeline.addLast("simple", TestHandler())
                    }
                })
    }

    fun start(): Channel {
        return bootstrap.bind().sync().channel()
    }
}

// Simple extends Channel inboudn handler
class TestHandler: SimpleChannelInboundHandler<HttpObject>() {
    override fun channelRead0(ctx: ChannelHandlerContext, msg: HttpObject) {

        if(msg is DefaultHttpRequest) {

        }
        if(msg is LastHttpContent) {
            val resp = DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK)
            resp.headers().set(HttpHeaders.Names.CONTENT_TYPE, "text/plain; charset=UTF-8")

            // if keep alive
            val c: ByteBuf =Unpooled.copiedBuffer("Netty rocks!", CharsetUtil.UTF_8)

            resp.headers().set(HttpHeaders.Names.CONTENT_LENGTH, c.readableBytes())
            resp.headers().set( HttpHeaders.Names.CONNECTION, HttpHeaders.Values.KEEP_ALIVE)
            // end

            val cc = c.readableBytes()

            ctx.write(resp)
            // not compress
            if (ctx.pipeline().get(SslHandler::class.java) == null) {
                ctx.write(c)
            } else {
                ctx.write(c)
            }
            val future = ctx.writeAndFlush(LastHttpContent.EMPTY_LAST_CONTENT)
        }

    }
    override fun exceptionCaught(ctx: ChannelHandlerContext , cause: Throwable ){
        cause.printStackTrace()
        println("errror")
    }
}
