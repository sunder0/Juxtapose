package com.sunder.juxtapose.server;

import cn.hutool.core.io.resource.ClassPathResource;
import cn.hutool.core.io.resource.NoResourceException;
import com.sunder.juxtapose.common.BaseComponent;
import com.sunder.juxtapose.common.ComponentException;
import com.sunder.juxtapose.common.ComponentLifecycleListener;
import com.sunder.juxtapose.common.encrypt.SslEncryptProvider;
import com.sunder.juxtapose.common.encrypt.SslEncryptProvider.SslEncryptor;
import com.sunder.juxtapose.server.conf.ServerConfig;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.ChannelProgressiveFuture;
import io.netty.channel.ChannelProgressiveFutureListener;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.DefaultHttpResponse;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http.HttpUtil;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http.LastHttpContent;
import io.netty.handler.ssl.ClientAuth;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.stream.ChunkedFile;
import io.netty.handler.stream.ChunkedWriteHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * @author : denglinhai
 * @date : 21:35 2025/08/06
 */
public class CertComponent extends BaseComponent<ProxyCoreComponent> {
    public final static String NAME = "CERT_COMPONENT";
    private final Logger logger = LoggerFactory.getLogger(CertComponent.class);

    private final String SERVER_CRT = "conf/ssl/server.crt";
    private final String SERVER_KEY = "conf/ssl/server.key";
    // 下载的ca证书存放路径
    private final String CA_CRT = "conf/ssl/ca.crt";
    private String host = "0.0.0.0";
    private Integer port = 1202;
    private SslContext sslContext;
    private EventLoopGroup workerGroup;

    public CertComponent(ProxyCoreComponent parent) {
        super(NAME, Objects.requireNonNull(parent), ComponentLifecycleListener.INSTANCE);
    }

    @Override
    protected void initInternal() {
        workerGroup = new NioEventLoopGroup(1);

        ServerConfig cfg = getConfigManager().getConfigByName(ServerConfig.NAME, ServerConfig.class);
        SslEncryptor sslEncryptor = SslEncryptProvider.provider("PEM_ENCRYPTOR");

        ClassPathResource serverCrt = new ClassPathResource(SERVER_CRT);
        ClassPathResource serverKey = new ClassPathResource(SERVER_KEY);

        Map<String, Object> encrypt = new HashMap<>(4);
        encrypt.put("server", true);
        encrypt.put("server.crt", serverCrt.getStream());
        encrypt.put("server.key", serverKey.getStream());
        try {
            sslContext = sslEncryptor.buildSslContext(ClientAuth.NONE, encrypt);
        } catch (Exception ex) {
            throw new ComponentException("Init ssl encryptor fail!", ex);
        }

        super.initInternal();
    }

    @Override
    protected void startInternal() {
        try {
            ServerBootstrap boot = new ServerBootstrap();
            boot.group(workerGroup)
                    .channel(NioServerSocketChannel.class)
                    .option(ChannelOption.SO_BACKLOG, 128)
                    .childOption(ChannelOption.SO_KEEPALIVE, true);

            boot.childHandler(new ChannelInitializer<SocketChannel>() {
                @Override
                protected void initChannel(SocketChannel ch) {
                    ChannelPipeline p = ch.pipeline();
                    p.addLast(new HttpServerCodec());
                    p.addLast(new HttpObjectAggregator(65536));
                    p.addLast(new ChunkedWriteHandler()); // 支持大文件传输
                    p.addLast(new FileDownloadHandler(CA_CRT));  // 自定义文件处理器
                }
            });

            boot.bind(port).addListener(f -> {
                if (f.isSuccess()) {
                    logger.info("Cert file server start successful!");
                } else {
                    logger.error("Cert file server start fail.", f.cause());
                }
            }).await();
        } catch (InterruptedException ex) {
            throw new ComponentException(ex);
        }

        super.startInternal();
    }

    @Override
    protected void destroyInternal() {
        workerGroup.shutdownGracefully();

        super.destroyInternal();
    }

    public SslContext getSslContext() {
        return sslContext;
    }

    /**
     * 随机读写对应file，写回文件内容
     */
    private class FileDownloadHandler extends SimpleChannelInboundHandler<FullHttpRequest> {
        private final String classPath;

        public FileDownloadHandler(String classPath) {
            this.classPath = classPath;
        }

        @Override
        protected void channelRead0(ChannelHandlerContext ctx, FullHttpRequest request) throws Exception {
            // 只处理 GET 请求
            if (!request.method().equals(HttpMethod.GET)) {
                sendError(ctx, HttpResponseStatus.METHOD_NOT_ALLOWED);
                return;
            }
            //todo: 扩展不通的文件
            ClassPathResource resource;
            try {
                resource = new ClassPathResource(this.classPath);
            } catch (NoResourceException ex) {
                sendError(ctx, HttpResponseStatus.NOT_FOUND);
                return;
            } catch (Exception ex) {
                logger.error("load file resource error.", ex);
                sendError(ctx, HttpResponseStatus.INTERNAL_SERVER_ERROR);
                return;
            }

            RandomAccessFile raf = new RandomAccessFile(resource.getAbsolutePath(), "r");
            long fileLength = raf.length();

            // 设置响应头
            HttpResponse response = new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK);
            HttpUtil.setContentLength(response, fileLength);
            response.headers().set(HttpHeaderNames.CONTENT_TYPE, "application/x-x509-ca-cert");
            response.headers().set(HttpHeaderNames.CONTENT_DISPOSITION, "attachment; filename=ca.crt");
            // 写入响应头, 文件内容
            ctx.write(response);
            ChannelFuture sendFileFuture = ctx.write(
                    new ChunkedFile(raf, 0, fileLength, 8192),
                    ctx.newProgressivePromise()
            );

            // 添加传输监听器
            sendFileFuture.addListener(new ChannelProgressiveFutureListener() {
                @Override
                public void operationProgressed(ChannelProgressiveFuture future, long progress, long total) {
                    if (total < 0) { // 总大小未知
                        logger.info("Transfer file[ca.crt] progress: " + progress);
                    } else {
                        logger.info(String.format("Transfer file progress: %d / %d (%.1f%%)%n", progress, total,
                                (progress * 100.0 / total)));
                    }
                }

                @Override
                public void operationComplete(ChannelProgressiveFuture future) throws IOException {
                    raf.close();
                    ctx.writeAndFlush(LastHttpContent.EMPTY_LAST_CONTENT);
                    logger.info("File transfer completed");
                }
            });
        }

        private void sendError(ChannelHandlerContext ctx, HttpResponseStatus status) {
            FullHttpResponse response = new DefaultFullHttpResponse(
                    HttpVersion.HTTP_1_1,
                    status,
                    Unpooled.copiedBuffer("Failure: " + status + "\r\n", StandardCharsets.UTF_8)
            );
            response.headers().set(HttpHeaderNames.CONTENT_TYPE, "text/plain; charset=UTF-8");
            ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            logger.error("handle download file fail!", cause);
            if (ctx.channel().isActive()) {
                sendError(ctx, HttpResponseStatus.INTERNAL_SERVER_ERROR);
            }
        }
    }

}
