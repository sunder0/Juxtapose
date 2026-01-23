package com.sunder.juxtapose.client;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpResponse;
import cn.hutool.http.HttpUtil;
import com.sunder.juxtapose.client.conf.ClientConfig;
import com.sunder.juxtapose.client.conf.ProxyServerConfig.ProxyServerNodeConfig;
import com.sunder.juxtapose.common.BaseComponent;
import com.sunder.juxtapose.common.Component;
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
 * @author : sunder
 * @date : 21:35 2025/08/06
 */
public class CertComponent<T extends Component<?>> extends BaseComponent<T> {
    public final static String NAME = "CERT_COMPONENT";
    // 下载的ca证书存放路径
    private final String CA_CRT = "conf/ssl/%s/ca.pem";
    private ProxyServerNodeConfig pscfg;
    private SslContext sslContext;

    public CertComponent(ProxyServerNodeConfig pscfg, T parent) {
        super(NAME, Objects.requireNonNull(parent), ComponentLifecycleListener.INSTANCE);
        this.pscfg = pscfg;
    }

    @Override
    protected void initInternal() {
        ClientConfig cfg = getConfigManager().getConfigByName(ClientConfig.NAME, ClientConfig.class);
        SslEncryptor sslEncryptor = SslEncryptProvider.provider(cfg.getEncryptMethod());

        Map<String, Object> encrypt = new HashMap<>(4);
        encrypt.put("server", false);

        // 如果是自签名证书才需要从server端获取证书
        // 默认使用2202端口下载, 真实场景需要域名+公有证书，不然容易被限速
        boolean selfSigned = StrUtil.isNotBlank(pscfg.certurl);
        String certPath = String.format(CA_CRT, parent.getName());
        URL cacrt = getClass().getClassLoader().getResource(certPath);
        if (cacrt == null && selfSigned) {
            try (HttpResponse response =
                    HttpUtil.createGet(String.format("http://%s/ca.pem", pscfg.certurl)).execute()) {
                URL classpathRoot = getClass().getClassLoader().getResource("");
                Path path = Paths.get(classpathRoot.toURI()).resolve(certPath);
                FileUtil.writeString(response.body(), path.toFile(), StandardCharsets.UTF_8);
                cacrt = path.toUri().toURL();
            } catch (Exception ex) {
                throw new ComponentException("Load ssl ca.crt error!", ex);
            }
        }
        try {
            if (selfSigned) {
                encrypt.put("ca.pem", cacrt.openStream());
            }
            encrypt.put("selfSigned", selfSigned);
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
