package org.nryotaro.nettysample

import io.netty.bootstrap.Bootstrap
import io.netty.channel.*
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.SocketChannel
import io.netty.channel.socket.nio.NioSocketChannel
import io.netty.handler.codec.http.*
import io.netty.handler.ssl.SslContext
import io.netty.handler.ssl.SslContextBuilder
import io.netty.handler.ssl.util.InsecureTrustManagerFactory
import io.netty.util.CharsetUtil
import io.netty.util.ReferenceCountUtil
import io.netty.util.concurrent.GenericFutureListener
import org.springframework.web.reactive.function.client.WebClient
import java.net.URI


class NettySample {
    fun a () {
        val uri : URI = URI("https://www.sec.gov/Archives/edgar/data/1280600/000117911017004594/0001179110-17-004594.txt");
        val scheme: String = "https"
        val host = "www.sec.gov"
        val port = 443
        val sslCtx = SslContextBuilder.forClient().trustManager(InsecureTrustManagerFactory.INSTANCE).build()

        val group = NioEventLoopGroup()
        val b = Bootstrap()
        try {
            val c: Bootstrap = b.group(group).channel(NioSocketChannel::class.java)
            c.handler(HttpSnoopClientInitializer(sslCtx))
            val chf: ChannelFuture = b.connect(host, port)
            while(true) {
                Thread.sleep(100L)
                // Make the connection attempt.
                //val ch: Channel = b.connect(host, port).sync().channel()


                chf.addListener(object: ChannelFutureListener {
                    override fun operationComplete(f: ChannelFuture?) {
                        val list = chf.channel().pipeline().toMap()
                        chf.channel().pipeline().addLast(HttpSnoopClientHandler())
                    }
                })


                chf.sync().channel().pipeline()
                /*
                // Prepare the HTTP request.
                val request: HttpRequest = DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, uri.rawPath)
                request.headers().set(HttpHeaderNames.HOST, host)
                request.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.CLOSE)
                request.headers().set(HttpHeaderNames.ACCEPT_ENCODING, HttpHeaderValues.GZIP)
                // Set some example cookies.
                //request.headers().set(HttpHeaderNames.COOKIE, ClientCookieEncoder.STRICT.encode(DefaultCookie("my-cookie", "foo"), DefaultCookie("another-cookie", "bar")))
                // Send the HTTP request.
                ch.writeAndFlush(request)

                // Wait for the server to close the connection.
                ch.closeFuture().addListener {
                    println("done")
                }//.sync()
                */
            }

        } finally {
            // Shut down executor threads to exit.
            group.shutdownGracefully()
        }

    }

}

class HttpSnoopClientInitializer(private val sslCtx: SslContext) : ChannelInitializer<SocketChannel>() {

    override fun initChannel(ch: SocketChannel) {
        val p : ChannelPipeline = ch.pipeline()

        p.addLast(sslCtx.newHandler(ch.alloc()))

        p.addLast(HttpClientCodec());
        // Remove the following line if you don't want automatic content decompression.
        p.addLast(HttpContentDecompressor())
        // Uncomment the following line if you don't want to handle HttpContents.
        //p.addLast(new HttpObjectAggregator(1048576));
        //p.addLast(HttpSnoopClientHandler())

    }
}

class HttpSnoopClientHandler: SimpleChannelInboundHandler<HttpObject>() {
    override fun channelRead0(ctx: ChannelHandlerContext, msg: HttpObject) {
        if (msg is HttpResponse) {
            val  response: HttpResponse = msg

            System.err.println("STATUS: " + response.status())
            System.err.println("VERSION: " + response.protocolVersion())
            System.err.println()

            if (!response.headers().isEmpty) {
                response.headers().names().forEach{ name ->
                    response.headers().getAll(name).forEach { value ->
                        System.err.println("HEADER: " + name + " = " + value)
                    }
                }
                System.err.println();
            }

            if (HttpUtil.isTransferEncodingChunked(response)) {
                System.err.println("CHUNKED CONTENT {");
            } else {
                System.err.println("CONTENT {");
            }
        }
        if (msg is HttpContent) {
            val content: HttpContent = msg

            System.err.print(content.content().toString(CharsetUtil.UTF_8))
            System.err.flush()

            if (content is LastHttpContent) {
                System.err.println("} END OF CONTENT");
                ctx.close();
            }
        }
    }

    override fun channelActive(ctx: ChannelHandlerContext) {
        super.channelActive(ctx)

        // Prepare the HTTP request.
        val uri : URI = URI("https://www.sec.gov/Archives/edgar/data/1280600/000117911017004594/0001179110-17-004594.txt");
        val host = "www.sec.gov"

        val request: HttpRequest = DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, uri.rawPath)
        request.headers().set(HttpHeaderNames.HOST, host)
        request.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.CLOSE)
        request.headers().set(HttpHeaderNames.ACCEPT_ENCODING, HttpHeaderValues.GZIP)
        // Set some example cookies.
        //request.headers().set(HttpHeaderNames.COOKIE, ClientCookieEncoder.STRICT.encode(DefaultCookie("my-cookie", "foo"), DefaultCookie("another-cookie", "bar")))
        // Send the HTTP request.
        ctx.channel().writeAndFlush(request)

        ctx.channel().closeFuture()

    }

    override fun channelReadComplete(ctx: ChannelHandlerContext) {
        super.channelReadComplete(ctx)
        ctx.pipeline().remove(this)
        //ctx.channel().closeFuture()
    }

    override fun exceptionCaught(ctx: ChannelHandlerContext,  cause: Throwable) {
        cause.printStackTrace();
        ctx.close();
    }
}

fun main(args : Array<String>) {
    NettySample().a()
    println("Hello Kotlin!!")
}