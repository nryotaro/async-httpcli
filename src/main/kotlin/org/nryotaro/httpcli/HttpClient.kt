package org.nryotaro.httpcli

import io.netty.bootstrap.Bootstrap
import io.netty.channel.*
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.nio.NioSocketChannel
import io.netty.handler.codec.http.*
import io.netty.handler.ssl.SslContextBuilder
import io.netty.handler.ssl.SslHandler
import io.netty.handler.ssl.util.InsecureTrustManagerFactory
import java.net.URI
import javax.net.ssl.SSLEngine
import io.netty.channel.ChannelHandlerContext
import java.util.concurrent.CountDownLatch


class HttpCli(private val countDownLatch: CountDownLatch) {

    private val group = NioEventLoopGroup()
    private val bootstrap = Bootstrap().group(group).channel(NioSocketChannel::class.java)
    init {
        val sslCtx = SslContextBuilder.forClient().trustManager(InsecureTrustManagerFactory.INSTANCE).build()

        bootstrap.handler(object: ChannelInitializer<Channel>() {
            override fun initChannel(ch: Channel) {
                val pipeline: ChannelPipeline = ch.pipeline();

                val engine: SSLEngine = sslCtx.newEngine(ch.alloc());
                pipeline.addFirst("ssl", SslHandler(engine))
                pipeline.addLast("decoder", HttpResponseDecoder())
                pipeline.addLast("encoder", HttpRequestEncoder())
                pipeline.addLast("decompressor", HttpContentDecompressor())
            }
        })
    }

    fun close() {
        group.shutdownGracefully()
    }

    fun retrieve(uri: URI) {

        println(uri.host)
        println(uri.port)
        println(uri.rawPath)

        val chf = bootstrap.connect(uri.host, 443)
        chf.addListener(object: ChannelFutureListener {
            override fun operationComplete(future: ChannelFuture) {
                chf.channel().pipeline().addLast(object: SimpleChannelInboundHandler<HttpObject>(){
                    override fun channelRead0(ctx: ChannelHandlerContext?, msg: HttpObject?) {
                        if(chf.isSuccess) {

                            println("foobar")
                            countDownLatch.countDown()
                        }
                    }
                })

                val request: HttpRequest = DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, uri.rawPath)
                request.headers().set(HttpHeaderNames.HOST, uri.host)
                request.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.CLOSE)
                request.headers().set(HttpHeaderNames.ACCEPT_ENCODING, HttpHeaderValues.GZIP)
                future.channel().writeAndFlush(request)

                //future.channel().pipeline().addLast


            }
        })

    }

}

fun main(args : Array<String>) {
    val latch = CountDownLatch(1)
    val cli = HttpCli(latch)

    
    cli.retrieve(URI("https://www.sec.gov/Archives/edgar/data/1280600/000117911017004594/0001179110-17-004594.txt"))

    latch.await()
    println("Hello Kotlin!!")
}