package com.sunder.juxtapose.client.http;

import com.sunder.juxtapose.client.conf.ClientConfig;
import com.sunder.juxtapose.client.publisher.HttpProxyRequestPublisher;
import com.sunder.juxtapose.common.BaseComponent;
import com.sunder.juxtapose.common.ComponentLifecycleListener;

/**
 * @author : denglh
 * @date : 20:30 2025/9/7
 * 主要用于window的代理设置，效果见（win10）：设置 -> 网络和Internet -> 代理 -> 手动设置代理
 * 参照：https://myth.cx/p/windows-proxy/
 */
public class WindowsSystemProxySetting extends BaseComponent<HttpProxyRequestPublisher> {
    public final static String NAME = "WINDOWS_SYSTEM_PROXY_SETTING";

    public WindowsSystemProxySetting(HttpProxyRequestPublisher parent) {
        super(NAME, parent, ComponentLifecycleListener.INSTANCE);
    }

    @Override
    protected void startInternal() {
        ClientConfig cfg = getConfigManager().getConfigByName(ClientConfig.NAME, ClientConfig.class);
        startSystemProxy(cfg.getProxyHost(), cfg.getHttpPort() + "", cfg.getProxyOverride());

        // 设置关闭清理钩子
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            logger.info("close juxtapose system proxy ...");
            disableSystemProxy();
        }));
    }

    @Override
    protected void destroyInternal() {
        disableSystemProxy();
    }

    /**
     * 开启系统本地代理
     *
     * @param proxyHost   代理本地的主机
     * @param proxyPort   代理本地的端口
     * @param ignoreHosts 忽视的代理地址， eg: localhost;127.*;10.*;172.16.*;172.17.*;172.18.*;172.19.*;172.20.*;172.21.*;172.22.*;172.23.*;172.24.*;172.25.*;172.26.*;172.27.*;172.28.*;172.29.*;172.30.*;172.31.*;192.168.*
     */
    private void startSystemProxy(String proxyHost, String proxyPort, String ignoreHosts) {
        try {
            // 启用代理
            String enableCmd = "reg add \"HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Internet Settings\" /v ProxyEnable /t REG_DWORD /d 1 /f";
            Runtime.getRuntime().exec(enableCmd);

            // 设置代理服务器地址和端口
            String serverCmd = "reg add \"HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Internet Settings\" /v ProxyServer /t REG_SZ /d " +
                    proxyHost + ":" + proxyPort + " /f";
            Runtime.getRuntime().exec(serverCmd);

            // 设置绕过本地地址的代理
            String overrideCmd = "reg add \"HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Internet Settings\" /v ProxyOverride /t REG_SZ /d \"" + ignoreHosts + "\" /f";
            Runtime.getRuntime().exec(overrideCmd);

           logger.info("windows proxy start successful!");
        } catch (Exception ex) {
            logger.error("windows proxy start fail, please manual set proxy in windows setting.", ex);
        }
    }

    /**
     * 禁用系统代理
     */
    public void disableSystemProxy() {
        try {
            // 禁用代理
            String disableCmd = "reg add \"HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Internet Settings\" /v ProxyEnable /t REG_DWORD /d 0 /f";
            Runtime.getRuntime().exec(disableCmd);

            logger.info("windows proxy disable successful!");
        } catch (Exception ex) {
            logger.error("windows proxy disable fail, please manual close proxy in windows setting.", ex);
        }
    }


}
