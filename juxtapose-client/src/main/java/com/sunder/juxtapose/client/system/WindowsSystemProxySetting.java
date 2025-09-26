package com.sunder.juxtapose.client.system;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

/**
 * @author : denglh
 * @date : 20:30 2025/9/7
 *         主要用于window的代理设置（只支持HTTP）, 设置注册表，效果见（win10）：设置 -> 网络和Internet -> 代理 -> 手动设置代理
 *         参照：https://myth.cx/p/windows-proxy/
 */
public class WindowsSystemProxySetting implements SystemProxySetting {
    private final Logger logger = LoggerFactory.getLogger(WindowsSystemProxySetting.class);

    /**
     * 开启系统本地代理
     *
     * @param proxyHost 代理本地的主机
     * @param proxyPort 代理本地的端口
     * @param ignoreOverride 忽视的代理地址， eg: localhost;127.*;10.*;172.16.*;172.17.*;172.18.*;172.19.*;172.20.*;172.21.*;172.22.*;172.23.*;172.24.*;172.25.*;172.26.*;172.27.*;172.28.*;172.29.*;172.30.*;172.31.*;192.168.*
     */
    @Override
    public void enableSystemProxy(String proxyHost, int proxyPort, String ignoreOverride) {
        try {
            // 启用代理
            String enableCmd
                    = "reg add \"HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Internet Settings\" /v ProxyEnable /t REG_DWORD /d 1 /f";
            executeCommand(enableCmd);

            // 设置代理服务器地址和端口
            String serverCmd = String.format(
                    "reg add \"HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Internet Settings\" /v ProxyServer /t REG_SZ /d %s:%d /f",
                    proxyHost, proxyPort);
            executeCommand(serverCmd);

            // 设置绕过本地地址的代理
            String overrideCmd = String.format(
                    "reg add \"HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Internet Settings\" /v ProxyOverride /t REG_SZ /d \"%s\" /f",
                    ignoreOverride);
            executeCommand(overrideCmd);

            logger.info("windows proxy enable successful!");
        } catch (Exception ex) {
            logger.error("windows proxy enable fail, please manual set proxy in windows setting.", ex);
        }
    }

    /**
     * 禁用系统代理
     */
    @Override
    public void disableSystemProxy() {
        try {
            // 禁用代理
            String disableCmd
                    = "reg add \"HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Internet Settings\" /v ProxyEnable /t REG_DWORD /d 0 /f";
            executeCommand(disableCmd);

            logger.info("windows proxy disable successful!");
        } catch (Exception ex) {
            logger.error("windows proxy disable fail, please manual close proxy in windows setting.", ex);
        }
    }

    private void executeCommand(String command) throws Exception {
        logger.info("start execute script, command:[{}].", command);
        Process process = Runtime.getRuntime().exec(command);

        // 捕获输出
        new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    logger.info("[System Proxy] {}", line);
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }).start();

        int exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new RuntimeException(String.format("System proxy set execute error[%s]!", exitCode));
        }
    }

}
