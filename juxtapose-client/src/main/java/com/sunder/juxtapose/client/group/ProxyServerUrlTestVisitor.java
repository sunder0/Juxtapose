package com.sunder.juxtapose.client.group;

import cn.hutool.core.lang.Pair;
import com.sunder.juxtapose.client.conf.ClientConfig;
import com.sunder.juxtapose.common.connection.Connection;
import com.sunder.juxtapose.common.connection.ConnectionState;
import com.sunder.juxtapose.common.connection.ConnectionStateListener;
import com.sunder.juxtapose.common.mesage.ProxyRequestMessage;
import com.sunder.juxtapose.common.proxy.ProxyRequest;
import com.sunder.juxtapose.common.proxy.ProxyRequestSubscriber;
import io.netty.buffer.ByteBuf;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.DefaultHttpRequest;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpRequestEncoder;
import io.netty.handler.codec.http.HttpVersion;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

/**
 * @author : sunder
 * @date : 17:44 2025/12/23
 * 为proxy node提供url-test延迟测试增强
 */
public class ProxyServerUrlTestVisitor {
    // 最大容许延迟5s，超过视作超时
    public final static long LATENCY_TIMEOUT_MS = 5 * 1_000L;

    private final Logger logger;
    private final ClientConfig ccfg;

    public ProxyServerUrlTestVisitor(ClientConfig ccfg) {
        this.ccfg = ccfg;
        this.logger = LoggerFactory.getLogger(ProxyServerUrlTestVisitor.class);
    }

    /**
     * 测试协议节点延迟
     *
     * @param subscriber 订阅节点
     * @return 延迟（ms）
     */
    public CompletableFuture<Long> testUrl(ProxyRequestSubscriber subscriber) {
        Pair<String, Integer> hostInfo = parseHostInfoFromURL(ccfg.getLatencyUrl());
        if (hostInfo.getKey() == null || hostInfo.getValue() == null) {
            logger.error("Invalid latency url, cannot parse host and port. url[{}]", ccfg.getLatencyUrl());
            throw new IllegalArgumentException("Invalid latency url!");
        }

        ProxyRequest request = new ProxyRequest(hostInfo.getKey(), hostInfo.getValue(), new EmbeddedChannel());

        CompletableFuture<Long> result = waitForConnectionReady(request, subscriber)
                .thenCompose(conn -> measurePingAfterHttpRequest(subscriber, conn, request));
        return withTimeout(result, 10, TimeUnit.SECONDS, LATENCY_TIMEOUT_MS);
    }

    /**
     * 建立连接
     *
     * @param request    代理请求
     * @param subscriber 订阅节点
     * @return
     */
    private CompletableFuture<Connection> waitForConnectionReady(ProxyRequest request, ProxyRequestSubscriber subscriber) {
        CompletableFuture<Connection> future = new CompletableFuture<>();

        ConnectionStateListener listener = (conn, oldState, newState) -> {
            if (newState == ConnectionState.READY) {
                future.complete(conn);
            }
        };

        // 订阅请求并获取连接
        Connection connection = subscriber.subscribe(request);
        connection.addConnectionStateListener(listener);
        // 移除监听器，避免内存泄漏
        future.whenComplete((conn, ex) -> {
            connection.removeConnectionStateListener(listener);
        });

        return future;
    }

    /**
     * 测量延迟
     *
     * @param subscriber 订阅几点
     * @param connection 连接
     * @param request    代理请求
     * @return
     */
    private CompletableFuture<Long> measurePingAfterHttpRequest(ProxyRequestSubscriber subscriber, Connection connection, ProxyRequest request) {
        long ping = System.currentTimeMillis();
        CompletableFuture<Long> future = new CompletableFuture<>();

        ConnectionStateListener activeListener = (conn, oldState, newState) -> {
            if (newState == ConnectionState.ACTIVE) {
                future.complete(System.currentTimeMillis() - ping);
            }
        };
        connection.addConnectionStateListener(activeListener);
        // 移除监听器，避免内存泄漏
        future.whenComplete((conn, ex) -> {
            connection.removeConnectionStateListener(activeListener);
            connection.close();
        });

        sendHttpRequest(subscriber, connection, request);

        return future;
    }

    /**
     * 提取HTTP请求发送逻辑
     */
    private void sendHttpRequest(ProxyRequestSubscriber subscriber, Connection connection, ProxyRequest request) {
        final EmbeddedChannel channel = new EmbeddedChannel(new HttpRequestEncoder() {
            @Override
            protected boolean isContentAlwaysEmpty(HttpRequest msg) {
                return true;
            }
        });
        // 构建HTTP请求
        HttpRequest httpRequest = new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, ccfg.getLatencyUrl());
        channel.writeOutbound(httpRequest);

        // 根据代理协议发送消息
        ByteBuf outboundMsg = channel.readOutbound();
        if (outboundMsg == null) {
            logger.warn("No outbound message to send, proxy[{}].", subscriber.proxyUri());
            return;
        }

        switch (subscriber.proxyProtocol()) {
            case HTTP:
                connection.writeMessage(outboundMsg);
                break;
            case JUXTA:
                ProxyRequestMessage message = new ProxyRequestMessage(
                        request.getSerialId(),
                        request.getHost(),
                        request.getPort(),
                        outboundMsg
                );
                connection.writeMessage(message);
                break;
            default:
                logger.warn("Unsupported proxy protocol[{}], proxy[{}].",
                        subscriber.proxyProtocol(), subscriber.proxyUri());
        }
    }

    /**
     * 从URI中解析HOST和port
     *
     * @return host -> port
     */
    private Pair<String, Integer> parseHostInfoFromURL(String url) {
        URI uri = null;
        try {
            uri = new URI(url);
        } catch (URISyntaxException ex) {
            throw new RuntimeException("Latency url illegal[" + url + "].");
        }

        return new Pair<>(uri.getHost(), uri.getPort() == -1 ? url.contains("https") ? 443 : 80 : uri.getPort());
    }

    /**
     * 自定义超时， 在规定timeout没有完成则返回defaultValue， 否则返回future
     *
     * @param future       需要指定超时的future
     * @param timeout      超时时间
     * @param unit         超时单位
     * @param defaultValue 超时返回的值
     * @param <T>
     * @return 在规定timeout没有完成则返回defaultValue， 否则返回future
     */
    public static <T> CompletableFuture<T> withTimeout(CompletableFuture<T> future, long timeout, TimeUnit unit, T defaultValue) {
        // 创建一个超时Future
        CompletableFuture<T> timeoutFuture = CompletableFuture.supplyAsync(() -> {
            try {
                Thread.sleep(unit.toMillis(timeout));
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new CompletionException(ex);
            }
            return defaultValue;
        });

        // 返回最先完成的结果
        return future.applyToEither(timeoutFuture, Function.identity());
    }
}
