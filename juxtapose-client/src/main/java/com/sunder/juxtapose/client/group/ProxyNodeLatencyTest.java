package com.sunder.juxtapose.client.group;

import java.util.concurrent.CompletableFuture;

/**
 * @author : sunder
 * @date : 16:42 2025/12/24
 */
public interface ProxyNodeLatencyTest {

    /**
     * 节点延迟测试
     *
     * @return 延迟（ms）
     */
    CompletableFuture<Long> testLatency();

}
