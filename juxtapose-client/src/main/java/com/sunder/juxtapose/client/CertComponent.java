package com.sunder.juxtapose.client;

import cn.hutool.core.io.FileUtil;
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

import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * @author : denglinhai
 * @date : 21:35 2025/08/06
 */
public class CertComponent extends BaseComponent<ProxyServerNodeManager> {
    public final static String NAME = "CERT_COMPONENT";
    // 下载的ca证书存放路径
    private final String CA_CRT = "conf/ssl/ca.crt";
    private SslContext sslContext;

    public CertComponent(ProxyServerNodeManager parent) {
        super(NAME, Objects.requireNonNull(parent), ComponentLifecycleListener.INSTANCE);
    }

    @Override
    protected void initInternal() {
        ClientConfig cfg = getConfigManager().getConfigByName(ClientConfig.NAME, ClientConfig.class);
        SslEncryptor sslEncryptor = SslEncryptProvider.provider(cfg.getEncryptMethod());

        Map<String, Object> encrypt = new HashMap<>(4);
        encrypt.put("server", false);

        URL cacrt = getClass().getClassLoader().getResource(CA_CRT);
        if (cacrt == null) {
            try (HttpResponse response = HttpUtil.createGet(
                    String.format("http://%s:%s/ca.crt", cfg.getEncryptHost(), cfg.getEncryptPort())).execute()) {
                URL classpathRoot = getClass().getClassLoader().getResource("");
                Path path = Paths.get(classpathRoot.toURI()).resolve(CA_CRT);
                FileUtil.writeString(response.body(), path.toFile(), StandardCharsets.UTF_8);
                cacrt = path.toUri().toURL();
            } catch (Exception ex) {
                throw new ComponentException("Load ssl ca.crt error!", ex);
            }
        }
        try {
            encrypt.put("ca.crt", cacrt.openStream());
            sslContext = sslEncryptor.buildSslContext(ClientAuth.NONE, encrypt);
        } catch (Exception ex) {
            throw new ComponentException("Init ssl encryptor fail!", ex);
        }

        logger.info("load ssl cert successful...");
    }

    public SslContext getSslContext() {
        return sslContext;
    }

}
