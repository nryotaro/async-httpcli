package org.nryotaro.httpcli.client;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.pool.AbstractChannelPoolHandler;
import io.netty.channel.pool.AbstractChannelPoolMap;
import io.netty.channel.pool.ChannelPool;
import io.netty.channel.pool.SimpleChannelPool;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.*;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.SslHandler;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.FutureListener;
import org.nryotaro.httpcli.handler.CliHandler;
import org.nryotaro.httpcli.handler.SslExceptionHandler;

import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

import static jdk.nashorn.internal.objects.Global.println;

public class HttpCli {

    private static String SSL = "ssl";
    private static String READ_TIMEOUT = "read_timeout";
    private static String  SPECIFIC = "specific";

    private NioEventLoopGroup group = new NioEventLoopGroup();

    Duration handshakeTimeout;
    SslExceptionHandler sslExceptionHandler;
    public HttpCli(Duration handshakeTimeout, SslExceptionHandler sslExceptionHandler) {
        this.handshakeTimeout = handshakeTimeout;
        this.sslExceptionHandler = sslExceptionHandler;

    }

    public HttpCli() {
        this(Duration.ofSeconds(10), cause -> cause.printStackTrace());
    }

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

    private SslHandler buildSSlHandler(Channel ch) throws SSLException {
        SslContext sslCtx = SslContextBuilder.forClient().trustManager(InsecureTrustManagerFactory.INSTANCE).build();
        SSLEngine engine= sslCtx.newEngine(ch.alloc());
        SslHandler sslHandler = new SslHandler(engine);
        sslHandler.setHandshakeTimeout(handshakeTimeout.toMillis(), TimeUnit.MILLISECONDS);
         Future<Channel> c = sslHandler.handshakeFuture().addListener(i -> {
             // TODO handle cancellation
                if(!i.isSuccess())  {
                   sslExceptionHandler.onHandshakeFailure(i.cause());
                }
         });
        return sslHandler;
    }

    public Future<?> close() {
        return group.shutdownGracefully();
    }

    private int port(URI uri) {
        int port = uri.getPort();

        if(port != -1) {
            return port;
        }

        switch (uri.getScheme()) {
            case "http":  return 80;
            case "https":  return 443;
            default: throw new UnsupportedOperationException(String.format("failed to infer the port of %s", uri.toString()));
        }
    }

    public void get(String url ,CliHandler handler) throws URISyntaxException {
        get(url, handler, Duration.ofSeconds(10L));
    }
    public void get(String url ,CliHandler handler,Duration readTimeout) throws URISyntaxException {
        URI uri = new URI(url);
        SimpleChannelPool pool  = poolMap.get(new InetSocketAddress(uri.getHost(), port(uri)));
        Future<Channel> chf  = pool.acquire();

        chf.addListener((Future<Channel> it) -> {
            //ChannelOption.CONNECT_TIMEOUT_MILLIS
            if(it.isSuccess()) {
                Channel channel = it.getNow();
                ChannelPipeline pipeline = channel.pipeline();

                if(uri.getScheme().equals("https")  && pipeline.get(SSL) == null) {
                    pipeline.addFirst(SSL, buildSSlHandler(channel));
                }
                pipeline.addLast(READ_TIMEOUT, new ReadTimeoutHandler(readTimeout.toMillis(), TimeUnit.MILLISECONDS));
                pipeline.addLast(SPECIFIC, new SimpleChannelInboundHandler<HttpObject> (){
                    @Override
                    protected void channelRead0(ChannelHandlerContext ctx, HttpObject msg) throws Exception {
                        if(msg instanceof LastHttpContent) {
                            LastHttpContent lastHttpContent = (LastHttpContent) msg;
                            handler.acceptLastHttpContent(lastHttpContent);
                            pool.release(ctx.channel());
                            return;
                        }
                        if(msg instanceof HttpResponse) {
                            handler.acceptHttpResponse((HttpResponse) msg);
                            return;
                        }
                        if(msg instanceof HttpContent) {
                           handler.acceptContent((HttpContent) msg);
                           return;
                        }
                    }

                    /*
                     * invoked when ReadTimeoutException occurred
                     */
                    @Override
                    public  void exceptionCaught(ChannelHandlerContext ctx ,Throwable cause) {
                        handler.onException(cause);
                        ctx.close(); //TODO required?
                        pool.release(ctx.channel());
                    }
                });

                /**
                 * ??? required
                 */
                channel.closeFuture().addListener(new ChannelFutureListener() {
                    @Override
                    public void operationComplete(ChannelFuture future) {
                        if(!future.isSuccess()) {
                            System.out.println(future.channel().isOpen());
                            future.channel().close();
                        }
                    }
                });

                // TODO deflate
                HttpRequest request= new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, uri.getRawPath());
                request.headers().set(HttpHeaderNames.HOST, uri.getHost());
                request.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.KEEP_ALIVE);
                request.headers().set(HttpHeaderNames.ACCEPT_ENCODING, HttpHeaderValues.GZIP);
                channel.writeAndFlush(request);
            } else {
                handler.onFailure(it.cause());
            }

        });
    }
}