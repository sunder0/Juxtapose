package com.sunder.juxtapose.client.dns;

import cn.hutool.core.thread.ThreadFactoryBuilder;
import com.sunder.juxtapose.common.Platform;
import io.netty.channel.EventLoop;
import io.netty.channel.EventLoopGroup;
import io.netty.resolver.dns.DefaultDnsCache;
import io.netty.resolver.dns.DnsNameResolver;
import io.netty.resolver.dns.DnsNameResolverBuilder;
import io.netty.resolver.dns.SequentialDnsServerAddressStreamProvider;
import io.netty.util.concurrent.Future;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author : denglinhai
 * @date : 15:22 2025/09/11
 */
public class StandardDnsResolverPool {
    public final static StandardDnsResolverPool dnsResolver = new StandardDnsResolverPool(3);

    private final DnsNameResolver[] resolvers;
    private final AtomicInteger index = new AtomicInteger(0);

    private StandardDnsResolverPool(int threads) {
        resolvers = new DnsNameResolver[threads];

        EventLoopGroup eventLoopGroup = Platform.createEventLoopGroup(threads,
                ThreadFactoryBuilder.create().setNamePrefix("dns-resolver-").build());
        for (int i = 0; i < threads; i++) {
            resolvers[i] = createDnsNameResolver(eventLoopGroup.next());
        }
    }

    /**
     * 异步解析域名
     */
    public Future<InetAddress> resolveAsync(String hostname) {
        return resolvers[index.getAndIncrement() % resolvers.length].resolve(hostname);
    }

    /**
     * 同步解析
     */
    public InetAddress resolveSync(String hostname) throws Exception {
        return resolvers[index.getAndIncrement() % resolvers.length].resolve(hostname).sync().getNow();
    }

    /**
     * 关闭解析器
     */
    public void close() {
        for (int i = 0; i < resolvers.length; i++) {
            resolvers[i].close();
        }
    }

    /**
     * 创建一个dns解析器
     *
     * @param eventLoop 时间循环
     * @return io.netty.resolver.dns.DnsNameResolver
     */
    private DnsNameResolver createDnsNameResolver(EventLoop eventLoop) {
        return new DnsNameResolverBuilder(eventLoop)
                .channelType(Platform.datagramChannelClass())
                .queryTimeoutMillis(3000)  // 3秒超时
                .maxQueriesPerResolve(50)  // 最大10次查询
                .recursionDesired(true)    // 请求递归查询
                // DNS协议特性
                // 如果不启用 EDNS：// - 可能无法获得完整的 DNS 响应 - CDN 优化可能失效 - 安全性降低
                .optResourceEnabled(true)
                /**
                 * // 假设搜索域配置为: "example.com", "local"
                 *
                 * // 查询 "www" (0个点)
                 * // 1. 先尝试相对域名: www.example.com → www.local
                 * // 2. 如果都失败，最后尝试绝对域名: www.
                 *
                 * // 查询 "www.google.com" (2个点，大于ndots=1)
                 * // 1. 直接尝试绝对域名: www.google.com.
                 * // 2. 不再尝试相对域名
                 */
                .ndots(1)            // 相对域名尝试, 1个点为平衡
                .decodeIdn(true)    // 解码国际化域名, 允许使用非 ASCII 字符（如中文、阿拉伯文等）的域名。
                // DNS服务器配置
                .nameServerProvider(
                        new SequentialDnsServerAddressStreamProvider(
                                new InetSocketAddress("8.8.8.8", 53),      // Google DNS
                                new InetSocketAddress("1.1.1.1", 53),      // Cloudflare DNS
                                new InetSocketAddress("208.67.222.222", 53) // OpenDNS
                        )
                )
                // 缓存配置, 最大缓存时间1小时, 最小缓存时间1分钟,  失败后再次查询缓存时间10秒（10s内一直失败）
                .resolveCache(new DefaultDnsCache(60, 3600, 10))
                .build();
    }

}
