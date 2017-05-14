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
import io.netty.util.concurrent.FutureListener
import org.junit.Ignore
import org.junit.Test
import org.nryotaro.handler.CliHandler
import org.nryotaro.httpcli.HttpCli
import java.io.RandomAccessFile
import java.net.InetSocketAddress
import java.net.URI
import java.nio.charset.StandardCharsets

/**
 * TODO
 *  https://localhost -> 443
 *  unexisted url(https://adfasdfasdfas)
 *  handle not 200 response
 *  http
 */
class HttpCliTest {

    @Test
    fun getSuccessFully() {

        val server = TestServer()
        val chan = server.start()

        val cli = HttpCli()

        cli.get("https://localhost:8443", createHandler())
        Thread.sleep(10000)
        cli.get("https://localhost:8443", createHandler())

        chan.closeFuture().sync()

    }

    fun createHandler(): CliHandler {
        return object: CliHandler {

            var failed = false
            var cachedContent: ByteArray = ByteArray(0)
            override fun acceptHttpResponse(response: HttpResponse) {

                failed = response.status() != HttpResponseStatus.OK
            }

            override fun acceptLastHttpContent(content: LastHttpContent) {
                if(failed) {
                    return
                }

                val buf = content.content()
                val length = buf.readableBytes()
                val array = ByteArray(length)
                buf.getBytes(buf.readerIndex(), array)
                cachedContent = byteArrayOf(*cachedContent, *array)

                println(String(cachedContent))
            }

            override fun acceptContent(content: HttpContent) {
                if(failed) {
                    return
                }

                val buf = content.content()
                val length = buf.readableBytes()
                val array = ByteArray(length)
                buf.getBytes(buf.readerIndex(), array)
                cachedContent = byteArrayOf(*cachedContent, *array)
            }

            override fun onFailure(cause: Throwable) {
                println("failure")
                cause.printStackTrace()
            }

            override fun onException(ctx: ChannelHandlerContext, cause: Throwable) {
                cause.printStackTrace()
            }
        }
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
