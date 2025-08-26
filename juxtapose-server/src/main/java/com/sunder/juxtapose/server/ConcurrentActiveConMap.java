package com.sunder.juxtapose.server;

import com.sunder.juxtapose.server.session.ClientSession;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * @author : denglinhai
 * @date : 11:35 2025/07/29
 */
public final class ConcurrentActiveConMap {
    // 外层Map: SocketChannel -> 内层Map(serialId -> ActiveProxyConnection)
    private final ConcurrentMap<ClientSession, ConcurrentMap<Long, ActiveProxyConnection>> outerMap =
            new ConcurrentHashMap<>();

    // 锁管理器，确保每个SocketChannel有独立的锁对象
    private final ConcurrentMap<ClientSession, Object> channelLocks = new ConcurrentHashMap<>();

    /**
     * 添加连接到Map中
     *
     * @param clientSession SocketChannel
     * @param connection ActiveProxyConnection
     */
    public void put(ClientSession clientSession, ActiveProxyConnection connection) {
        Long serialId = connection.getSerialId();
        Object lock = getSessionLock(clientSession);
        synchronized (lock) {
            // 获取或创建内层Map
            ConcurrentMap<Long, ActiveProxyConnection> innerMap =
                    outerMap.computeIfAbsent(clientSession, k -> new ConcurrentHashMap<>());
            innerMap.put(serialId, connection);
        }
    }

    /**
     * 获取连接（双重键查找）
     *
     * @param clientSession SocketChannel
     * @param serialId 连接序列ID
     * @return ActiveProxyConnection或null
     */
    public ActiveProxyConnection get(ClientSession clientSession, Long serialId) {
        Object lock = getSessionLock(clientSession);
        synchronized (lock) {
            ConcurrentMap<Long, ActiveProxyConnection> innerMap = outerMap.get(clientSession);
            return (innerMap != null) ? innerMap.get(serialId) : null;
        }
    }

    /**
     * 移除指定连接
     *
     * @param clientSession SocketChannel
     * @param serialId 连接序列ID
     * @return 被移除的ActiveProxyConnection或null
     */
    public ActiveProxyConnection remove(ClientSession clientSession, Long serialId) {
        Object lock = getSessionLock(clientSession);
        synchronized (lock) {
            ConcurrentMap<Long, ActiveProxyConnection> innerMap = outerMap.get(clientSession);
            if (innerMap == null) {
                return null;
            }

            ActiveProxyConnection conn = innerMap.remove(serialId);
            if (conn != null && innerMap.isEmpty()) {
                // 内层Map为空时移除整个通道条目
                outerMap.remove(clientSession);
                channelLocks.remove(clientSession); // 清理锁对象
            }
            return conn;
        }
    }

    public boolean contains(ClientSession clientSession, Long serialId) {
        Object lock = getSessionLock(clientSession);
        synchronized (lock) {
            ConcurrentMap<Long, ActiveProxyConnection> innerMap = outerMap.get(clientSession);
            return (innerMap != null) && innerMap.containsKey(serialId);
        }
    }

    /**
     * 获取通道所有连接（不可修改的视图）
     *
     * @param clientSession SocketChannel
     * @return 连接的只读集合
     */
    public Set<ActiveProxyConnection> getConnections(ClientSession clientSession) {
        Object lock = getSessionLock(clientSession);
        synchronized (lock) {
            ConcurrentMap<Long, ActiveProxyConnection> innerMap = outerMap.get(clientSession);
            return (innerMap != null)
                    ? Collections.unmodifiableSet(new HashSet<>(innerMap.values()))
                    : Collections.emptySet();
        }
    }

    // 获取通道对应的锁对象
    private Object getSessionLock(ClientSession clientSession) {
        // 为每个channel创建唯一的锁对象
        return channelLocks.computeIfAbsent(clientSession, k -> new Object());
    }

}
