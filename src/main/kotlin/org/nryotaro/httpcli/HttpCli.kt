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
import io.netty.channel.pool.*
import io.netty.util.concurrent.Future
import java.io.File
import java.nio.channels.FileChannel
import java.nio.file.StandardOpenOption
import java.util.concurrent.CountDownLatch
import io.netty.channel.pool.SimpleChannelPool
import java.net.InetSocketAddress
import io.netty.channel.pool.AbstractChannelPoolMap
import io.netty.channel.pool.ChannelPoolMap
import io.netty.handler.timeout.IdleStateHandler
import io.netty.handler.timeout.ReadTimeoutHandler
import io.netty.handler.timeout.WriteTimeoutHandler
import io.netty.util.concurrent.FutureListener
import org.nryotaro.handler.CliHandler
import org.nryotaro.handler.SslExceptionHandler
import java.time.Duration
import java.util.concurrent.TimeUnit


class HttpCli(
        private val readTimeout: Duration = Duration.ofSeconds(10),
        private val handshakeTimeout: Duration = Duration.ofSeconds(10),
        private val sslExceptionHandler: SslExceptionHandler= object: SslExceptionHandler{
            override fun onHandshakeFailure(cause: Throwable) {
                cause.printStackTrace()
            }
        }
        ) {

    private val group = NioEventLoopGroup()

    private val bootstrap = Bootstrap().group(group)
            .channel(NioSocketChannel::class.java)

    private val poolMap = object : AbstractChannelPoolMap<InetSocketAddress, SimpleChannelPool>() {
        override fun newPool(key: InetSocketAddress): SimpleChannelPool {
            return SimpleChannelPool(bootstrap.remoteAddress(key),object: AbstractChannelPoolHandler(){
                override fun channelCreated(ch: Channel) {
                    val pipeline: ChannelPipeline = ch.pipeline()

                    pipeline.addFirst("ssl", buildSSlHandler(ch))
                    pipeline.addLast("decoder", HttpResponseDecoder())
                    pipeline.addLast("encoder", HttpRequestEncoder())
                    pipeline.addLast("decompressor", HttpContentDecompressor())
                    pipeline.addLast("readtimeout", ReadTimeoutHandler(readTimeout.toMillis(), TimeUnit.MILLISECONDS))

                    //ch.closeFuture().addListener(ChannelFutureListener.CLOSE_ON_FAILURE)
                    ch.closeFuture().addListener{
                        it
                    }
                }

                override fun channelAcquired(ch: Channel) {
                    super.channelAcquired(ch)
                }

                override fun channelReleased(ch: Channel) {
                    super.channelReleased(ch)
                }
            }
            )
        }
    }

    private fun buildSSlHandler(ch: Channel): SslHandler {
        val sslCtx = SslContextBuilder.forClient().trustManager(InsecureTrustManagerFactory.INSTANCE).build()
        val engine: SSLEngine = sslCtx.newEngine(ch.alloc())
        val sslHandler = SslHandler(engine)
        sslHandler.setHandshakeTimeout(handshakeTimeout.toMillis(), TimeUnit.MILLISECONDS)
        val c: Future<Channel> = sslHandler.handshakeFuture().addListener {
            // TODO handle cancellation
            if(!it.isSuccess) {
                sslExceptionHandler.onHandshakeFailure(it.cause())
            }
        }
        return sslHandler
    }

    fun close(): Future<*> {
        return group.shutdownGracefully()
    }

    private fun port(uri: URI): Int {
        return when(uri.port) {
            -1 -> when(uri.scheme) {
                "http" -> 80
                "https" -> 443
                else -> throw RuntimeException("failed to infer the port of $uri")
            }
            else -> uri.port
        }
    }
    fun get(url: String, handler: CliHandler) {
        val uri = URI(url)
        val pool: SimpleChannelPool = poolMap.get(InetSocketAddress(uri.host, port(uri)))
        val chf: Future<Channel> = pool.acquire()
        chf.addListener( FutureListener<Channel> {

            //ChannelOption.CONNECT_TIMEOUT_MILLIS
            if(it.isSuccess) {
                val channel = it.now
                val pipeline = channel.pipeline()

                pipeline.addLast(object: SimpleChannelInboundHandler<HttpObject>(){
                    override fun channelRead0(ctx: ChannelHandlerContext, msg: HttpObject) {
                        when(msg) {
                            is HttpResponse -> handler.acceptHttpResponse(msg)
                            is LastHttpContent -> {
                                handler.acceptLastHttpContent(msg)
                                pool.release(ctx.channel())
                            }
                            is HttpContent -> handler.acceptContent(msg)

                        }
                    }
                    override fun exceptionCaught(ctx: ChannelHandlerContext, cause: Throwable) {
                        handler.onException(ctx, cause)

                        ctx.close()
                        pool.release(ctx.channel())
                    }
                })
                // TODO GZIP
                val request: HttpRequest = DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, uri.rawPath)
                request.headers().set(HttpHeaderNames.HOST, uri.host)
                request.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.KEEP_ALIVE)
                request.headers().set(HttpHeaderNames.ACCEPT_ENCODING, HttpHeaderValues.GZIP)
                channel.writeAndFlush(request)
            } else {
                handler.onFailure(it.cause())
            }
        })

    }


    fun retrieve(uri: URI, destFile: File) {
        val pool: SimpleChannelPool = poolMap.get(InetSocketAddress(uri.host, 443))
        val chf = pool.acquire()

        var failed = false

        chf.addListener(object: FutureListener<Channel> {

            override fun operationComplete(future: Future<Channel>) {
                if(future.isSuccess) {
                    val channel = future.now
                    val pipeline = channel.pipeline()
                    val names = pipeline.names()
                    if(!destFile.exists()) {
                        destFile.parentFile.mkdirs()
                    }
                    if(destFile.exists()) {
                        destFile.delete()
                    }
                    destFile.createNewFile()

                    var dest: FileChannel = FileChannel.open(destFile.toPath(), StandardOpenOption.APPEND)
                    println("open: " + destFile.toString())

                    pipeline.addLast(object: SimpleChannelInboundHandler<HttpObject>(){
                        override fun channelRead0(ctx: ChannelHandlerContext, msg: HttpObject) {

                            if(msg is DefaultHttpResponse) {
                                println("$uri: "+ msg.status())
                            }
                            if(msg is DefaultHttpContent) {
                                if(!dest.isOpen && !failed) {
                                    dest = FileChannel.open(destFile.toPath(), StandardOpenOption.WRITE,
                                            StandardOpenOption.APPEND)
                                }
                                if(!failed) {
                                    try {
                                        dest.write(msg.content().nioBuffer())
                                    } catch(e: Exception) {
                                        println(e)
                                    }
                                }
                            }
                            if(msg is LastHttpContent) {
                                pool.release(ctx.channel())
                                println("close: " + destFile.toString())
                                dest.close()
                            }
                        }
                        override fun exceptionCaught(ctx: ChannelHandlerContext, cause: Throwable) {
                            //super.exceptionCaught(ctx, cause)
                            //ctx.close()
                            failed = true
                            cause.printStackTrace()
                            dest.close()
                            destFile.delete()
                            pool.release(ctx.channel())
                        }
                    })
                    //i++
                    val request: HttpRequest = DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, uri.rawPath)
                    request.headers().set(HttpHeaderNames.HOST, uri.host)
                    request.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.KEEP_ALIVE)
                    request.headers().set(HttpHeaderNames.ACCEPT_ENCODING, HttpHeaderValues.GZIP)
                    channel.writeAndFlush(request)
                }
            }
        })
    }
}
