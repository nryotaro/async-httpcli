package org.nryotaro.nettysample.server

import io.netty.bootstrap.ServerBootstrap
import io.netty.buffer.ByteBuf
import io.netty.channel.*
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.SocketChannel
import io.netty.channel.socket.nio.NioServerSocketChannel
import io.netty.handler.codec.http.HttpContentCompressor
import io.netty.handler.codec.http.HttpObject
import io.netty.handler.codec.http.HttpRequestDecoder
import io.netty.handler.codec.http.HttpResponseEncoder
import java.net.InetSocketAddress

class Server {


    init {
        val bootstrap = ServerBootstrap()
        val group: EventLoopGroup = NioEventLoopGroup()
        bootstrap.group(group).channel(NioServerSocketChannel::class.java).localAddress(InetSocketAddress(8080))
                .childHandler(object: ChannelInitializer<SocketChannel>(){
                    override fun initChannel(channel: SocketChannel) {
                        val pipeline = channel.pipeline()
                        pipeline.addLast("decoder", HttpRequestDecoder())
                        pipeline.addLast("encoder", HttpResponseEncoder())
                        pipeline.addLast("compressor", HttpContentCompressor())
                        pipeline.addLast("simple", SimpleHandler())
                    }
                })

        bootstrap.bind().sync()
    }
}

class SimpleHandler: SimpleChannelInboundHandler<HttpObject>() {
    override fun channelRead0(ctx: ChannelHandlerContext?, msg: HttpObject) {
        println(msg)
    }

}

