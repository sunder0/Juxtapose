package com.sunder.juxtapose.client.group;

import com.sunder.juxtapose.client.ClientApplicationContext;
import com.sunder.juxtapose.client.ProxyRequest;
import com.sunder.juxtapose.client.ProxyRequestSubscriber;

import java.util.Comparator;
import java.util.List;

/**
 * @author : denglinhai
 * @date : 17:56 2025/12/24
 */
public interface ProxyGroupNodeSelectStrategy {

    /**
     * 代理组中订阅节点的选择策略
     *
     * @param request 代理请求
     * @return 返回一个订阅节点
     */
    ProxyRequestSubscriber select(ProxyRequest request);

    /**
     * 手动选择策略
     */
    class SelectStrategy implements ProxyGroupNodeSelectStrategy {
        private final String name; // 组名
        private final ClientApplicationContext context;
        private final List<ProxyRequestSubscriber> nodes;

        public SelectStrategy(String name, ClientApplicationContext context, List<ProxyRequestSubscriber> nodes) {
            this.name = name;
            this.context = context;
            this.nodes = nodes;
        }

        @Override
        public ProxyRequestSubscriber select(ProxyRequest request) {
            String nodeName = context.getSelectNodes().get(name);
            if (nodeName == null) {
                return nodes.get(0);
            }
            return nodes.stream().filter(e -> e.getName().equals(nodeName)).findFirst().orElse(null);
        }
    }

    /**
     * url-test策略
     */
    class URLTestStrategy implements ProxyGroupNodeSelectStrategy {
        private final String name; // 组名
        private final List<ProxyRequestSubscriber> nodes;

        public URLTestStrategy(String name, List<ProxyRequestSubscriber> nodes) {
            this.name = name;
            this.nodes = nodes;
        }

        @Override
        public ProxyRequestSubscriber select(ProxyRequest request) {
            return nodes.stream().min(Comparator.comparingLong(ProxyRequestSubscriber::proxyLatency))
                    .orElse(nodes.get(0));
        }

    }

    /**
     * fallback策略
     */
    class FallbackStrategy implements ProxyGroupNodeSelectStrategy {
        private final String name; // 组名
        private final List<ProxyRequestSubscriber> nodes;

        public FallbackStrategy(String name, List<ProxyRequestSubscriber> nodes) {
            this.name = name;
            this.nodes = nodes;
        }

        @Override
        public ProxyRequestSubscriber select(ProxyRequest request) {
            return nodes.stream().filter(e -> e.proxyLatency() < ProxyServerUrlTestVisitor.LATENCY_TIMEOUT_MS)
                    .findFirst().orElse(nodes.get(0));
        }
    }

    /**
     * load-balance策略
     */
    class LoadBalanceStrategy implements ProxyGroupNodeSelectStrategy {
        private final String name; // 组名
        private final List<ProxyRequestSubscriber> nodes;

        public LoadBalanceStrategy(String name, List<ProxyRequestSubscriber> nodes) {
            this.name = name;
            this.nodes = nodes;
        }

        @Override
        public ProxyRequestSubscriber select(ProxyRequest request) {
            // todo: 暂定哈希，可以扩展不通的均衡策略
            int index = Math.abs(request.hashCode() % nodes.size());
            return nodes.get(index);
        }
    }

}
