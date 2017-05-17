package org.nryotaro.httpcli.client;

import io.netty.channel.nio.NioEventLoopGroup;

class HttpCli {

    private static String SSL = "ssl";
    private static String READ_TIMEOUT = "read_timeout";
    private static String  SPECIFIC = "specific";
    
    private NioEventLoopGroup group = new NioEventLoopGroup();
}