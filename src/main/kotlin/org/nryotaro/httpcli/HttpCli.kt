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
import io.netty.util.concurrent.FutureListener
import org.nryotaro.handler.CliHandler


class HttpCli {

    private val group = NioEventLoopGroup()

    private val bootstrap = Bootstrap().group(group).channel(NioSocketChannel::class.java)

    private val poolMap = object : AbstractChannelPoolMap<InetSocketAddress, SimpleChannelPool>() {
        override fun newPool(key: InetSocketAddress): SimpleChannelPool {
            return SimpleChannelPool(bootstrap.remoteAddress(key),object: AbstractChannelPoolHandler(){
                override fun channelCreated(ch: Channel) {
                    val sslCtx = SslContextBuilder.forClient().trustManager(InsecureTrustManagerFactory.INSTANCE).build()
                    val pipeline: ChannelPipeline = ch.pipeline()
                    val engine: SSLEngine = sslCtx.newEngine(ch.alloc());
                    pipeline.addFirst("ssl", SslHandler(engine))
                    pipeline.addLast("decoder", HttpResponseDecoder())
                    pipeline.addLast("encoder", HttpRequestEncoder())
                    pipeline.addLast("decompressor", HttpContentDecompressor())
                }
            })
        }
    }

    fun close(): Future<*> {
        return group.shutdownGracefully()
    }

    private fun port(uri: URI): Int {
        return when(uri.port) {
            -1 -> when(uri.scheme) {
                "http" -> 80
                "https" -> 443
                else -> throw RuntimeException("")
            }
            else -> uri.port
        }
    }
    fun get(url: String, handler: CliHandler) {
        val uri = URI(url)
        val pool: SimpleChannelPool = poolMap.get(InetSocketAddress(uri.host, port(uri)))
        val chf: Future<Channel> = pool.acquire()
        chf.addListener( FutureListener<Channel> {

            if(it.isSuccess) {
                val channel = it.now
                val pipeline = channel.pipeline()

                pipeline.addLast(object: SimpleChannelInboundHandler<HttpObject>(){
                    override fun channelRead0(ctx: ChannelHandlerContext, msg: HttpObject) {
                        when(msg) {
                            is DefaultHttpResponse -> handler.acceptHttpResponse(msg)
                            is DefaultHttpContent -> handler.acceptContent(msg)
                            is LastHttpContent -> {
                                // TODO default last http content
                                handler.acceptLastHttpContent(msg)
                                pool.release(ctx.channel())
                            }
                        }
                    }
                    override fun exceptionCaught(ctx: ChannelHandlerContext, cause: Throwable) {
                        handler.onException(ctx, cause)
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
