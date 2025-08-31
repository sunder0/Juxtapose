package com.sunder.juxtapose.server;


import com.sunder.juxtapose.common.BaseCompositeComponent;
import com.sunder.juxtapose.common.ComponentLifecycleListener;
import com.sunder.juxtapose.common.ProxyProtocol;
import com.sunder.juxtapose.common.auth.AuthenticationStrategy;
import com.sunder.juxtapose.common.auth.SimpleAuthenticationStrategy;
import com.sunder.juxtapose.server.conf.ServerConfig;
import com.sunder.juxtapose.server.proxy.JuxtaProxyTaskPublisher;
import com.sunder.juxtapose.server.proxy.Socks5ProxyTaskPublisher;
import com.sunder.juxtapose.server.session.SessionManager;


/**
 * @author : denglinhai
 * @date : 22:24 2025/07/21
 */
public final class ProxyCoreComponent extends BaseCompositeComponent<com.sunder.juxtapose.server.ServerBootstrap> {
    public final static String NAME = "PROXY_CORE_COMPONENT";

    // 认证策略，后续改成从DB获取
    private AuthenticationStrategy authStrategy = new SimpleAuthenticationStrategy("root", "root");

    private CertComponent certComponent;
    private SessionManager sessionManager;
    private TcpProxyDispatchComponent dispatcher;

    public ProxyCoreComponent(com.sunder.juxtapose.server.ServerBootstrap parent) {
        super(NAME, parent, ComponentLifecycleListener.INSTANCE);
    }

    @Override
    protected void initInternal() {
        addModule(sessionManager = new SessionManager(this));

        ServerConfig cfg = getConfigManager().getConfigByName(ServerConfig.NAME, ServerConfig.class);
        ProxyProtocol protocol = cfg.getProxyProto();
        if (protocol == ProxyProtocol.JUXTA) {
            addChildComponent(new JuxtaProxyTaskPublisher(this));
        } else if (protocol == ProxyProtocol.SOCKS5) {
            addChildComponent(new Socks5ProxyTaskPublisher(this));
        } else if (protocol == ProxyProtocol.HTTP) {
            //todo
        } else if (protocol == ProxyProtocol.VMESS) {
            //todo
        }

        addChildComponent(certComponent = new CertComponent(this));
        addChildComponent(dispatcher = new TcpProxyDispatchComponent(this));

        super.initInternal();
    }

    @Override
    protected void startInternal() {

        super.startInternal();
    }

    @Override
    protected void destroyInternal() {
        super.destroyInternal();
    }

    public TcpProxyDispatchComponent getDispatcher() {
        return dispatcher;
    }

}
