package com.sunder.juxtapose.client.subscriber;

import cn.hutool.http.HttpResponse;
import cn.hutool.http.HttpUtil;
import com.sunder.juxtapose.client.conf.ClientConfig;
import com.sunder.juxtapose.common.BaseComponent;
import com.sunder.juxtapose.common.ComponentException;
import com.sunder.juxtapose.common.ComponentLifecycleListener;
import com.sunder.juxtapose.common.encrypt.SslEncryptProvider;
import com.sunder.juxtapose.common.encrypt.SslEncryptProvider.SslEncryptor;
import io.netty.handler.ssl.ClientAuth;
import io.netty.handler.ssl.SslContext;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * @author : denglinhai
 * @date : 21:35 2025/08/06
 */
public class CertComponent extends BaseComponent<ProxyRelayServerComponent> {
    public final static String NAME = "CERT_COMPONENT";
    // private final String SERVER_CRT = "conf/ssl/server.crt";
    // private final String SERVER_KEY = "conf/ssl/server.key";
    // 下载的ca证书存放路径
    private final String CA_CRT = "conf/ssl/ca.crt";
    private SslContext sslContext;

    public CertComponent(ProxyRelayServerComponent parent) {
        super(NAME, Objects.requireNonNull(parent), ComponentLifecycleListener.INSTANCE);
    }

    @Override
    protected void initInternal() {
        ClientConfig cfg = getConfigManager().getConfigByName(ClientConfig.NAME, ClientConfig.class);
        SslEncryptor sslEncryptor = SslEncryptProvider.provider("PEM_ENCRYPTOR");

        Map<String, Object> encrypt = new HashMap<>(4);
        encrypt.put("server", false);

        try (HttpResponse response = HttpUtil.createGet(
                String.format("http://{%s}:%s/ca.crt", parent.getHost(), parent.getPort())).execute()) {
            encrypt.put("ca.crt", response.bodyStream());
        }
        try {
            sslContext = sslEncryptor.buildSslContext(ClientAuth.NONE, encrypt);
        } catch (Exception ex) {
            throw new ComponentException("Init ssl encryptor fail!", ex);
        }

        super.initInternal();
    }

    public SslContext getSslContext() {
        return sslContext;
    }

}
