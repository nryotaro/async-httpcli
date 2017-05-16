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
import io.netty.handler.stream.ChunkedWriteHandler
import io.netty.util.CharsetUtil
import org.hamcrest.CoreMatchers.`is`
import org.junit.Assert.assertThat
import org.junit.Test
import org.nryotaro.handler.CliHandler
import org.nryotaro.httpcli.HttpCli
import java.net.InetSocketAddress
import java.util.concurrent.CountDownLatch

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
        server.start()

        val cli = HttpCli()

        val latch =  CountDownLatch(1)
        cli.get("https://localhost:8443", createHandler(latch))
        latch.await()
        server.close()

    }

    fun createHandler(latch: CountDownLatch): CliHandler {
        return object: CliHandler {

            var failed = false
            var cachedContent: ByteArray = ByteArray(0)
            override fun acceptHttpResponse(response: HttpResponse) {
                failed = response.status() != HttpResponseStatus.OK
            }

            override fun acceptLastHttpContent(content: LastHttpContent) {
                store(content)

                assertThat(String(cachedContent),`is`("Netty rocks!"))
                latch.countDown()
            }

            private fun store(content: HttpContent) {
                if(failed) {
                    return
                }

                val buf = content.content()
                val length = buf.readableBytes()
                val array = ByteArray(length)
                buf.getBytes(buf.readerIndex(), array)
                cachedContent = byteArrayOf(*cachedContent, *array)
            }

            override fun acceptContent(content: HttpContent) {
                store(content)
            }

            override fun onFailure(cause: Throwable) {
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

    private val port = 8443
    init {

        val cert: SelfSignedCertificate = SelfSignedCertificate()

        val context: SslContext = SslContext.newServerContext(cert.certificate(), cert.privateKey())

        bootstrap.group(group).channel(NioServerSocketChannel::class.java).localAddress(InetSocketAddress(port))
                .childHandler(object: ChannelInitializer<SocketChannel>(){
                    override fun initChannel(channel: SocketChannel) {
                        val pipeline = channel.pipeline()
                        pipeline.addLast("ssl", context.newHandler(channel.alloc()));
                        pipeline.addLast("decoder", HttpRequestDecoder())
                        pipeline.addLast("encoder", HttpResponseEncoder())
                        pipeline.addLast(ChunkedWriteHandler())
                        pipeline.addLast("simple", TestHandler())
                        pipeline.addLast("compressor", HttpContentCompressor())
                    }
                })
    }

    fun start(): Channel {
        return bootstrap.bind().sync().channel()
    }

    fun close() {
        group.shutdownGracefully().sync()
    }
}

// Simple extends Channel inbound handler
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
        println("error")
    }
}
