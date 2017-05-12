package org.nryotaro.httpcli3

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
import java.io.File
import java.nio.channels.FileChannel
import java.nio.file.StandardOpenOption
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

    fun retrieve(uri: URI, destFile: File) {

        val chf = bootstrap.connect(uri.host, 443)
        chf.addListener(object: ChannelFutureListener {
            override fun operationComplete(future: ChannelFuture) {
                val pipeline = chf.channel().pipeline()
                val names = pipeline.names()


                if(!destFile.exists()) {
                    destFile.parentFile.mkdirs()
                }

                val dest = FileChannel.open(destFile.toPath(), StandardOpenOption.WRITE, StandardOpenOption.CREATE_NEW)

                pipeline.addLast(object: SimpleChannelInboundHandler<HttpObject>(){
                    override fun channelRead0(ctx: ChannelHandlerContext, msg: HttpObject) {

                        if(msg is DefaultHttpResponse) {

                            println("$uri: "+ msg.status())
                        }
                        if(msg is DefaultHttpContent) {
                            dest.write(msg.content().nioBuffer())
                        }
                        if(msg is LastHttpContent) {
                            countDownLatch.countDown()
                            dest.close()
                            ctx.close()
                        }
                    }

                    override fun exceptionCaught(ctx: ChannelHandlerContext, cause: Throwable) {
                        super.exceptionCaught(ctx, cause)
                        ctx.close()
                        dest.close()
                    }

                })

                val request: HttpRequest = DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, uri.rawPath)
                request.headers().set(HttpHeaderNames.HOST, uri.host)
                request.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.KEEP_ALIVE)
                request.headers().set(HttpHeaderNames.ACCEPT_ENCODING, HttpHeaderValues.GZIP)
                future.channel().writeAndFlush(request)

                //future.channel().pipeline().addLast

            }

        })

    }

}

fun main(args : Array<String>) {


    val localPrefix = "/Users/nryotaro/hoge"
    val lines =    File("/Users/nryotaro/hoge.txt").readLines()
    val latch = CountDownLatch(lines.size)
    val cli = HttpCli(latch)
    lines.forEach {
        Thread.sleep(200L)
        cli.retrieve(URI("https://www.sec.gov" + it), File(localPrefix + it))

    }

    latch.await()
    println("Hello Kotlin!!")
    cli.close()
}