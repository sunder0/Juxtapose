package com.sunder.juxtapose.server;

import cn.hutool.core.io.resource.NoResourceException;
import cn.hutool.core.io.resource.UrlResource;
import com.sunder.juxtapose.common.BaseComponent;
import com.sunder.juxtapose.common.ComponentException;
import com.sunder.juxtapose.common.ComponentLifecycleListener;
import com.sunder.juxtapose.common.MultiProtocolResource;
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
import io.netty.channel.DefaultFileRegion;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.FileRegion;
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

    // ca证书存放路径
    private final String SERVER_CRT = "conf/cert/server.crt";
    private final String SERVER_KEY = "conf/cert/server.key";
    private final String CA_CRT = "conf/cert/ca.crt";
    private String host;
    private Integer port;
    private SslContext sslContext;
    private EventLoopGroup workerGroup;

    public CertComponent(ProxyCoreComponent parent) {
        super(NAME, Objects.requireNonNull(parent), ComponentLifecycleListener.INSTANCE);
    }

    @Override
    protected void initInternal() {
        workerGroup = new NioEventLoopGroup(1);

        ServerConfig cfg = getConfigManager().getConfigByName(ServerConfig.NAME, ServerConfig.class);
        SslEncryptor sslEncryptor = SslEncryptProvider.provider(cfg.getEncryptMethod());
        this.host = cfg.getProxyHost();
        this.port = cfg.getEncryptServerPort();

        MultiProtocolResource serverCrt = new MultiProtocolResource(SERVER_CRT, true);
        MultiProtocolResource serverKey = new MultiProtocolResource(SERVER_KEY, true);

        Map<String, Object> encrypt = new HashMap<>(4);
        encrypt.put("server", true);
        encrypt.put("server.crt", serverCrt.getResource().getStream());
        encrypt.put("server.key", serverKey.getResource().getStream());
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

            boot.bind(host, port).addListener(f -> {
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
     * 写回文件内容
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
            // todo: 扩展不同的文件
            MultiProtocolResource resource;
            try {
                resource = new MultiProtocolResource(this.classPath, true);
            } catch (NoResourceException ex) {
                sendError(ctx, HttpResponseStatus.NOT_FOUND);
                return;
            } catch (Exception ex) {
                logger.error("load file resource error.", ex);
                sendError(ctx, HttpResponseStatus.INTERNAL_SERVER_ERROR);
                return;
            }

            UrlResource urlResource = (UrlResource) resource.getResource();
            RandomAccessFile raf = new RandomAccessFile(urlResource.getFile().getAbsolutePath(), "r");
            long fileLength = raf.length();

            // 1. 创建并发送响应头（不立即刷新）
            HttpResponse response = new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK);
            HttpUtil.setContentLength(response, fileLength); // 关键：设置精确内容长度
            response.headers()
                    .set(HttpHeaderNames.CONTENT_TYPE, "application/x-x509-ca-cert")
                    .set(HttpHeaderNames.CONTENT_DISPOSITION, "attachment; filename=ca.crt");

            // 2. 将响应头和文件内容作为同一个写入操作
            ctx.write(response); // 先写入响应头

            // 3. 使用零拷贝传输文件（更高效）
            FileRegion region = new DefaultFileRegion(raf.getChannel(), 0, fileLength);
            ChannelFuture sendFuture = ctx.write(region, ctx.newProgressivePromise());
            // 4. 添加传输监听器
            sendFuture.addListener(future -> {
                // 无论成功失败都关闭文件
                try {
                    raf.close();
                } catch (IOException e) {
                    logger.error("Close file failed", e);
                }

                if (future.isSuccess()) {
                    // 5. 写入结束标记并关闭连接
                    ctx.writeAndFlush(LastHttpContent.EMPTY_LAST_CONTENT)
                            .addListener(ChannelFutureListener.CLOSE);
                    logger.info("File transfer completed");
                } else {
                    logger.error("File transfer failed", future.cause());
                    ctx.close();
                }
            });

            // 6. 立即刷新所有写入内容
            ctx.flush();
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
