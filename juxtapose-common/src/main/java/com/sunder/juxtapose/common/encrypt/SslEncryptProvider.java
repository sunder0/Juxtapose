package com.sunder.juxtapose.common.encrypt;

import com.sunder.juxtapose.common.Named;
import io.netty.handler.ssl.ClientAuth;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.SslProvider;

import java.io.FileInputStream;
import java.io.InputStream;
import java.security.KeyStore;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.TrustManagerFactory;

/**
 * @author : denglinhai
 * @date : 22:56 2025/07/30
 */
public class SslEncryptProvider {
    private final static List<String> SUPPORT_PROTOCOLS = Arrays.asList("TLSv1.3", "TLSv1.2");
    private final static List<String> SUPPORT_CIPHERS = Arrays.asList("TLS_AES_256_GCM_SHA384",
            "TLS_CHACHA20_POLY1305_SHA256", "TLS_AES_128_GCM_SHA256");
    private final static Map<String, SslEncryptor> encryptors = new HashMap<>(16);

    static {
        encryptors.put(PEMSslEncryptor.NAME, new PEMSslEncryptor());
        encryptors.put(JksSslEncryptor.NAME, new JksSslEncryptor());
    }

    /**
     * 提供ssl加密工具
     *
     * @param name
     * @return
     */
    public static SslEncryptor provider(String name) {
        SslEncryptor encryptor = encryptors.get(name);
        if (encryptor == null) {
            throw new UnsupportedOperationException("Unsupport encrypt type!");
        }

        return encryptor;
    }


    public interface SslEncryptor extends Named {
        SslContext buildSslContext(ClientAuth clientAuth, Map<String, Object> encrypt) throws Exception;
    }

    static class PEMSslEncryptor implements SslEncryptor {
        public final static String NAME = "PEM_ENCRYPTOR";

        @Override
        public SslContext buildSslContext(ClientAuth clientAuth, Map<String, Object> encrypt) throws Exception {
            boolean server = (boolean) encrypt.getOrDefault("server", false);
            if (server) {
                SslContextBuilder builder = SslContextBuilder.forServer(
                                (InputStream) encrypt.get("server.crt"), // 服务端证书
                                (InputStream) encrypt.get("server.key")  // 服务端私钥
                        )
                        .sslProvider(SslProvider.OPENSSL)
                        .clientAuth(clientAuth)
                        .protocols(SUPPORT_PROTOCOLS)
                        .ciphers(SUPPORT_CIPHERS);
                // 非单向认证
                if (clientAuth != ClientAuth.NONE) {
                    builder.trustManager((InputStream) encrypt.get("ca.crt"));
                }
                return builder.build();
            } else {
                SslContextBuilder builder = SslContextBuilder.forClient()
                        .trustManager((InputStream) encrypt.get("ca.crt")) // 信任的CA证书
                        .sslProvider(SslProvider.OPENSSL)
                        .protocols(SUPPORT_PROTOCOLS)
                        .ciphers(SUPPORT_CIPHERS);
                // 非单向认证
                if (clientAuth != ClientAuth.NONE) {
                    builder.keyManager((InputStream) encrypt.get("client.crt"),
                            (InputStream) encrypt.get("client.key"));
                }
                return builder.build();
            }
        }

        @Override
        public void setName(String name) {
            throw new UnsupportedOperationException();
        }

        @Override
        public String getName() {
            return NAME;
        }
    }

    static class JksSslEncryptor implements SslEncryptor {
        public final static String NAME = "JKS_ENCRYPTOR";

        @Override
        public SslContext buildSslContext(ClientAuth clientAuth, Map<String, Object> encrypt) throws Exception {
            boolean server = (boolean) encrypt.getOrDefault("server", false);
            if (server) {
                // 加载JKS密钥库
                KeyStore keyStore = KeyStore.getInstance("JKS");
                try (FileInputStream fis = new FileInputStream("server.jks")) {
                    keyStore.load(fis, "juxtapose".toCharArray());
                }
                // 从密钥库获取私钥和证书链
                KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
                kmf.init(keyStore, "juxtapose".toCharArray());

                SslContextBuilder builder = SslContextBuilder.forServer(kmf);

                // 如果需要验证客户端,双向加密
                if (clientAuth != ClientAuth.NONE) {
                    KeyStore trustStore = KeyStore.getInstance("JKS");
                    keyStore.load((InputStream) encrypt.get("truststore"), "juxtapose".toCharArray());
                    TrustManagerFactory tmf = TrustManagerFactory.getInstance(
                            TrustManagerFactory.getDefaultAlgorithm());
                    tmf.init(trustStore);
                    builder.trustManager(tmf);
                }

                return builder.sslProvider(SslProvider.OPENSSL)
                        .protocols(SUPPORT_PROTOCOLS)
                        .ciphers(SUPPORT_CIPHERS)
                        .build();
            } else {
                // 客户端配置
                SslContextBuilder builder = SslContextBuilder.forClient();

                // 配置信任库
                KeyStore trustStore = KeyStore.getInstance("JKS");
                trustStore.load((InputStream) encrypt.get("truststore"), "juxtapose".toCharArray());
                TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
                tmf.init(trustStore);
                builder.trustManager(tmf);

                // 如果配置了客户端证书（双向认证）
                if (clientAuth != ClientAuth.NONE) {
                    KeyStore clientKeyStore = KeyStore.getInstance("JKS");
                    trustStore.load((InputStream) encrypt.get("clientKeystore"), "juxtapose".toCharArray());
                    KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
                    kmf.init(clientKeyStore, "juxtapose".toCharArray());
                    builder.keyManager(kmf);
                }

                return builder
                        .sslProvider(SslProvider.OPENSSL)
                        .protocols(SUPPORT_PROTOCOLS)
                        .ciphers(SUPPORT_CIPHERS)
                        .build();
            }
        }

        @Override
        public void setName(String name) {
            throw new UnsupportedOperationException();
        }

        @Override
        public String getName() {
            return NAME;
        }

    }
}
