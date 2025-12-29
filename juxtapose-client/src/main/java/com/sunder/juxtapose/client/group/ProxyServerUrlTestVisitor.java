package com.sunder.juxtapose.client.group;

import cn.hutool.core.lang.Pair;
import com.sunder.juxtapose.client.ProxyRequest;
import com.sunder.juxtapose.client.ProxyRequestSubscriber;
import com.sunder.juxtapose.client.conf.ClientConfig;
import com.sunder.juxtapose.client.connection.Connection;
import com.sunder.juxtapose.client.connection.ConnectionState;
import com.sunder.juxtapose.client.connection.ConnectionStateListener;
import com.sunder.juxtapose.common.mesage.ProxyRequestMessage;
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
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * @author : denglinhai
 * @date : 17:44 2025/12/23
 *         为proxy node提供url-test延迟测试增强
 */
public class ProxyServerUrlTestVisitor {
    // 最大容许延迟5s，超过视作超时
    public final static long LATENCY_TIMEOUT_MS = 5 * 1_000L;

    private final Logger logger;
    private final ClientConfig ccfg;
    private final EmbeddedChannel channel = new EmbeddedChannel(new HttpRequestEncoder() {
        @Override
        protected boolean isContentAlwaysEmpty(HttpRequest msg) {
            return true;
        }
    });

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
    public long testUrl(ProxyRequestSubscriber subscriber) {
        Pair<String, Integer> hostInfo = parseHostInfoFromURL(ccfg.getLatencyUrl());
        if (hostInfo.getKey() == null || hostInfo.getValue() == null) {
            logger.error("Invalid latency url, cannot parse host and port. url[{}]", ccfg.getLatencyUrl());
            return LATENCY_TIMEOUT_MS;
        }

        ProxyRequest request = new ProxyRequest(hostInfo.getKey(), hostInfo.getValue(), new EmbeddedChannel());
        Connection connection = null;
        CompletableFuture<Connection> connectionReady = new CompletableFuture<>();
        ConnectionStateListener readyListener = (conn, oldState, newState) -> {
            if (newState == ConnectionState.READY) {
                connectionReady.complete(conn);
            }
        };

        try {
            // 订阅请求并获取连接
            connection = subscriber.subscribe(request);
            // 等待连接就绪
            connection.addConnectionStateListener(readyListener);
            connectionReady.get(10, TimeUnit.SECONDS);

            // 等待测试url返回
            long ping = System.currentTimeMillis();
            CompletableFuture<Long> pong = new CompletableFuture<>();
            ConnectionStateListener activeListener = (conn, oldState, newState) -> {
                if (newState == ConnectionState.ACTIVE) {
                    pong.complete(System.currentTimeMillis());
                }
            };
            connection.addConnectionStateListener(activeListener);

            // 构建并发送HTTP请求
            sendHttpRequest(subscriber, connection, request);
            return pong.get(10 * 1000, TimeUnit.MILLISECONDS) - ping;
        } catch (TimeoutException ex) {
            logger.error("Latency url test timeout, proxy[{}], protocol[{}].", subscriber.proxyUri(),
                    subscriber.proxyProtocol(), ex);
            return LATENCY_TIMEOUT_MS;
        } catch (Exception ex) {
            logger.error("Latency url test failed, proxy[{}], protocol[{}].", subscriber.proxyUri(),
                    subscriber.proxyProtocol(), ex);
            return LATENCY_TIMEOUT_MS;
        } finally {
            if (connection != null) {
                try {
                    connection.close();
                } catch (Exception closeEx) {
                    logger.warn("Failed to close connection, proxy[{}].", subscriber.proxyUri(), closeEx);
                }
            }
        }
    }

    /**
     * 提取HTTP请求发送逻辑
     */
    private void sendHttpRequest(ProxyRequestSubscriber subscriber, Connection connection, ProxyRequest request) {
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
}
