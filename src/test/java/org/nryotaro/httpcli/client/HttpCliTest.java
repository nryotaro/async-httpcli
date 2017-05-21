package org.nryotaro.httpcli.client;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.*;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslHandler;
import io.netty.handler.ssl.util.SelfSignedCertificate;
import io.netty.handler.stream.ChunkedWriteHandler;
import io.netty.util.CharsetUtil;
import io.netty.util.concurrent.Future;
import org.junit.Ignore;
import org.junit.Test;
import org.nryotaro.httpcli.handler.CliHandler;

import javax.net.ssl.SSLException;
import javax.xml.ws.soap.Addressing;
import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.cert.CertificateException;
import java.util.List;
import java.util.concurrent.CountDownLatch;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;

/**
 * TODO
 *  https://localhost -> 443
 *  unexisted url(https://adfasdfasdfas)
 *  handle not 200 response
 *  http
 */
public class HttpCliTest {

    @Ignore
    @Test
    public void fun() throws URISyntaxException, IOException, InterruptedException {

        List<String> lines =  Files.readAllLines(new File(this.getClass().getResource("testindices.txt").toURI()).toPath());

        Path temp = Files.createTempDirectory("foo");

        HttpCli cli = new HttpCli();

        for(String line: lines) {
            String prefix = "https://www.sec.gov/";

            String  a= line.substring(prefix.length());

            File dest = new File(temp.toFile(), a);
            dest.getParentFile().mkdirs();
            FileChannel chan =  FileChannel.open(dest.toPath(), StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);

            System.out.println(dest);


            Thread.sleep(200);
            cli.get(line, new CliHandler() {

                @Override
                public void onFailure(Throwable cause) {
                    throw new RuntimeException("");
                }

                @Override
                public void acceptHttpResponse(HttpResponse response) {

                    if(response.status().code() != 200) {
                        throw new RuntimeException("");
                    }
                }

                private byte[] getBytes(ByteBuf buf) {
                    if(buf.isDirect()) {
                        int length = buf.readableBytes();
                        byte[] array = new byte[length];
                        buf.getBytes(buf.readerIndex(), array);
                        return array;
                    }
                    byte[] array = buf.array();
                    int offset = buf.arrayOffset() + buf.readerIndex();
                    int length = buf.readableBytes();
                    byte[] res = new byte[length];
                    for(int i = 0;i<length;i++) {
                         res[i] = array[i+offset];
                    }
                    return res;
                }

                @Override
                public void acceptContent(HttpContent msg) {
                    byte[] res =  getBytes(msg.content());

                    ByteBuffer f = ByteBuffer.allocate(res.length);
                    if (f.isReadOnly()) {
                        f.flip();
                    }
                    f.put(res);

                    f.flip();

                    try {
                        chan.write(f);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }

                @Override
                public void acceptLastHttpContent(LastHttpContent msg) {
                    byte[] res =  getBytes(msg.content());

                    ByteBuffer f = ByteBuffer.allocate(res.length);
                    f.put(res);

                    f.flip();

                    try {
                        chan.write(f);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }

                    try {
                        chan.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }

                @Override
                public void onException(Throwable cause) {
                    throw new RuntimeException("");
                }
            });
        }
    }

    @Test
    public void getSuccessFully() throws SSLException, InterruptedException, CertificateException, URISyntaxException {
        TestServer server = new TestServer();
        server.start();

        HttpCli cli = new HttpCli();

        CountDownLatch latch =  new CountDownLatch(1);
        cli.get("https://localhost:8443", createHandler(latch));
        latch.await();
        server.close();
    }
    CliHandler createHandler(CountDownLatch latch) {
        return new CliHandler() {
            boolean failed = false;
            ByteBuf cachedContent = Unpooled.buffer(4096, Integer.MAX_VALUE);

            @Override
            public void acceptHttpResponse(HttpResponse response) {
                failed = !response.status().equals(HttpResponseStatus.OK);
            }

            @Override
            public void acceptLastHttpContent(LastHttpContent content) {
                store(content);
                byte[] res = cachedContent.array();

                int offset = cachedContent.arrayOffset() + cachedContent.readerIndex();
                int length = cachedContent.readableBytes();
                byte[] result = new byte[length];

                for(int i=0;i<length;i++) {
                    result[i] = res[i+offset];
                }
                try {
                    assertThat(new String(result), is("Netty rocks!"));
                } catch(Throwable t){
                   fail();
                } finally {
                    latch.countDown();
                }
            }

            private void store(HttpContent content) {
                if (failed) {
                    return;
                }

                ByteBuf buf = content.content();

                if(buf.isDirect()) {
                    int length = buf.readableBytes();
                    cachedContent.writeBytes(buf, buf.readerIndex(), length);
                } else {
                    byte[] array = buf.array();
                    int offset = buf.arrayOffset() + buf.readerIndex();
                    int length = buf.readableBytes();
                    cachedContent.writeBytes(array, offset, length);
                }
            }

            @Override
            public void acceptContent(HttpContent content) {
                store(content);
            }

            @Override
            public void onFailure(Throwable cause) {
                cause.printStackTrace();
            }

            @Override
            public void onException(Throwable cause) {
                cause.printStackTrace();
            }
        };
    }

}

class TestServer {
    ServerBootstrap bootstrap = new ServerBootstrap();
    EventLoopGroup  group = new NioEventLoopGroup();

    private int port = 8443;

    TestServer() throws SSLException, CertificateException {
        SelfSignedCertificate cert = new SelfSignedCertificate();

        SslContext context = SslContext.newServerContext(cert.certificate(), cert.privateKey());

        bootstrap.group(group).channel(NioServerSocketChannel.class).localAddress(new InetSocketAddress(port))
                .childHandler(new ChannelInitializer<SocketChannel> (){
                    @Override
                    public void initChannel( SocketChannel channel) {
                        ChannelPipeline pipeline = channel.pipeline();
                        pipeline.addLast("ssl", context.newHandler(channel.alloc()));
                        pipeline.addLast("decoder", new HttpRequestDecoder());
                        pipeline.addLast("encoder", new HttpResponseEncoder());
                        pipeline.addLast(new ChunkedWriteHandler());
                        pipeline.addLast("simple", new TestHandler());
                        pipeline.addLast("compressor", new HttpContentCompressor());
                    }
                });
    }

    Channel start() throws InterruptedException {
        return bootstrap.bind().sync().channel();
    }

    Future<?> close() throws InterruptedException {
        return group.shutdownGracefully().sync();
    }
}

// Simple extends Channel inbound handler
class TestHandler extends SimpleChannelInboundHandler<HttpObject> {
    @Override
    public void channelRead0(ChannelHandlerContext ctx, HttpObject msg) {

        if(msg instanceof DefaultHttpRequest) {

        }
        if(msg instanceof LastHttpContent) {
            DefaultHttpResponse resp = new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK);
            resp.headers().set(HttpHeaders.Names.CONTENT_TYPE, "text/plain; charset=UTF-8");

            // if keep alive
            ByteBuf c = Unpooled.copiedBuffer("Netty rocks!", CharsetUtil.UTF_8);

            resp.headers().set(HttpHeaders.Names.CONTENT_LENGTH, c.readableBytes());
            resp.headers().set( HttpHeaders.Names.CONNECTION, HttpHeaders.Values.KEEP_ALIVE);
            // end

            ctx.write(resp);
            // not compress
            if (ctx.pipeline().get(SslHandler.class) == null) {
                ctx.write(c);
            } else {
                ctx.write(c);
            }
            ctx.writeAndFlush(LastHttpContent.EMPTY_LAST_CONTENT);
        }

    }
    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause){
        cause.printStackTrace();
        System.out.print("error");
    }
}
