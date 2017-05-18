package org.nryotaro.httpcli.client;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.pool.AbstractChannelPoolHandler;
import io.netty.channel.pool.AbstractChannelPoolMap;
import io.netty.channel.pool.SimpleChannelPool;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.HttpContentDecompressor;
import io.netty.handler.codec.http.HttpRequestEncoder;
import io.netty.handler.codec.http.HttpResponseDecoder;

import java.net.InetSocketAddress;

class HttpCli {

    private static String SSL = "ssl";
    private static String READ_TIMEOUT = "read_timeout";
    private static String  SPECIFIC = "specific";

    private NioEventLoopGroup group = new NioEventLoopGroup();

    private Bootstrap bootstrap
            = new Bootstrap().group(group).channel(NioSocketChannel.class);

    private AbstractChannelPoolMap<InetSocketAddress, SimpleChannelPool> poolMap = new AbstractChannelPoolMap<InetSocketAddress, SimpleChannelPool>() {

        protected SimpleChannelPool newPool(InetSocketAddress key) {
            return new SimpleChannelPool(bootstrap.remoteAddress(key), new AbstractChannelPoolHandler(){
                public void channelAcquired(Channel ch) throws Exception {
                    super.channelAcquired(ch);
                }

                public void channelReleased(Channel ch) throws Exception {
                    super.channelReleased(ch);
                    ch.pipeline().remove(READ_TIMEOUT);
                    ch.pipeline().remove(SPECIFIC);
                }

                public void channelCreated(Channel ch) throws Exception {
                     ChannelPipeline pipeline = ch.pipeline();
                    pipeline.addLast("decoder", new HttpResponseDecoder());
                    pipeline.addLast("encoder", new HttpRequestEncoder());
                    pipeline.addLast("decompressor", new HttpContentDecompressor());
                }
            });
        }
    };
}