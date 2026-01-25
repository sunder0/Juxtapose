package com.sunder.juxtapose.common.encrypt;

import com.sunder.juxtapose.common.Named;
import com.sunder.juxtapose.common.Platform;
import io.netty.handler.ssl.ClientAuth;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.SslProvider;
import io.netty.util.internal.PlatformDependent;
import org.bouncycastle.asn1.pkcs.PrivateKeyInfo;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.openssl.PEMKeyPair;
import org.bouncycastle.openssl.PEMParser;
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.Security;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author : sunder
 * @date : 22:56 2025/07/30
 */
public class SslEncryptProvider {
    private final static List<String> SUPPORT_PROTOCOLS = new ArrayList<>();
    private final static List<String> SUPPORT_CIPHERS = new ArrayList<>();
    private final static Map<String, SslEncryptor> encryptors = new HashMap<>(16);

    static {
        SUPPORT_PROTOCOLS.add("TLSv1.2");
        SUPPORT_CIPHERS.add("TLS_ECDHE_ECDSA_WITH_AES_256_GCM_SHA384");
        SUPPORT_CIPHERS.add("TLS_ECDHE_RSA_WITH_AES_256_GCM_SHA384");

        // jdk1.8.0_333开始生产可用TLS1.3
        if (!Platform.isBeforeJDK8u333()) {
            SUPPORT_PROTOCOLS.add("TLSv1.3");
            SUPPORT_CIPHERS.add("TLS_AES_256_GCM_SHA384");
            SUPPORT_CIPHERS.add("TLS_AES_128_GCM_SHA256");
        }
        encryptors.put(PEMSslEncryptor.NAME, new PEMSslEncryptor());
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
        public final static String NAME = "pem";

        @Override
        public SslContext buildSslContext(ClientAuth clientAuth, Map<String, Object> encrypt) throws Exception {
            boolean server = (boolean) encrypt.getOrDefault("server", false);
            if (server) {
                // 加载私钥
                InputStream keyStream = (InputStream) encrypt.get("server.key");
                ByteArrayOutputStream os = new ByteArrayOutputStream();
                byte[] buffer = new byte[1024];
                int len;
                while ((len = keyStream.read(buffer)) != -1) {
                    os.write(buffer, 0, len);
                }
                byte[] keyBytes = os.toByteArray();
                os.close();

                PrivateKey privateKey = loadPrivateKeyWithBC(keyBytes);

                // 加载证书
                CertificateFactory cf = CertificateFactory.getInstance("X.509");
                Collection<? extends Certificate> certificates =
                        cf.generateCertificates((InputStream) encrypt.get("server.pem"));

                SslContextBuilder builder = SslContextBuilder
                        .forServer(privateKey, (Collection<X509Certificate>) certificates)
                        .sslProvider(SslProvider.JDK)
                        .clientAuth(clientAuth)
                        .protocols(SUPPORT_PROTOCOLS)
                        .ciphers(SUPPORT_CIPHERS);

                // 非单向认证
                if (clientAuth != ClientAuth.NONE) {
                    throw new UnsupportedOperationException("Non-one-way encryption is not supported!");
                }
                return builder.build();
            } else {
                SslContextBuilder builder = SslContextBuilder.forClient()
                        .sslProvider(SslProvider.JDK)
                        .protocols(SUPPORT_PROTOCOLS)
                        .ciphers(SUPPORT_CIPHERS);

                // 如果是自己签名的证书（非ca机构颁发），需要信任
                boolean selfSigned = (boolean) encrypt.getOrDefault("selfSigned", false);
                if (selfSigned) {
                    builder.trustManager((InputStream) encrypt.get("ca.pem"));
                }
                // 非单向认证
                if (clientAuth != ClientAuth.NONE) {
                    throw new UnsupportedOperationException("Non-one-way encryption is not supported!");
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

        /**
         * 使用BC统一加载私钥， 兼容PKCS8和传统RSA（PKCS1）格式
         *
         * @param keyBytes 私钥数据
         * @return 私钥
         * @throws Exception
         */
        private PrivateKey loadPrivateKeyWithBC(byte[] keyBytes) throws Exception {
            if (Security.getProvider("BC") == null) {
                Security.addProvider(new BouncyCastleProvider());
            }

            try (PEMParser parser = new PEMParser(new InputStreamReader(new ByteArrayInputStream(keyBytes)))) {
                Object object = parser.readObject();
                JcaPEMKeyConverter converter = new JcaPEMKeyConverter().setProvider("BC");

                if (object instanceof PEMKeyPair) {
                    PEMKeyPair keyPair = (PEMKeyPair) object;
                    return converter.getPrivateKey(keyPair.getPrivateKeyInfo());
                } else if (object instanceof PrivateKeyInfo) {
                    return converter.getPrivateKey((PrivateKeyInfo) object);
                } else {
                    // 尝试直接转换
                    return converter.getPrivateKey((PrivateKeyInfo) object);
                }
            }
        }
    }

    public static void main(String[] args) throws NoSuchAlgorithmException {
//        System.out.println("Supported protocols: " +
//                String.join(", ", SSLContext.getDefault().getSupportedSSLParameters().getProtocols()));
        System.out.println(PlatformDependent.javaVersion());
        System.out.println(System.getProperty("java.version"));
    }
}
