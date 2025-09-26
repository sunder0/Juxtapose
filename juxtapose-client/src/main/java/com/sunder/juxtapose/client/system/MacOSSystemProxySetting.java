package com.sunder.juxtapose.client.system;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * @author : denglinhai
 * @date : 17:31 2025/09/08
 *         设置mac系统代理，目前只设置socks5
 */
public class MacOSSystemProxySetting implements SystemProxySetting {
    private final Logger logger = LoggerFactory.getLogger(MacOSSystemProxySetting.class);
    private final String activeService = "Wi-Fi"; // 默认网络服务使用wifi，目前暂不支持有线连接，后续添加

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
            String[] services = getNetworkServices();
            if (services.length == 0) {
                logger.error("No network services found");
                return;
            }

            // 默认网络服务使用wifi，目前暂不支持有线连接，后续添加
            boolean isWifi = Arrays.asList(services).contains(activeService);
            if (!isWifi) {
                throw new UnsupportedOperationException(
                        "Currently only wifi is supported, please switch the network to wifi...");
            }

            setSocksProxy(proxyHost, proxyPort, activeService);

            List<String> passDomains = Arrays.stream(ignoreOverride.split(";")).collect(Collectors.toList());
            setProxyBypassList(activeService, passDomains);

            logger.info("MacOs proxy enable successful!");
        } catch (Exception ex) {
            logger.error("MacOs proxy enable fail, please manual set proxy in mac setting.", ex);
        }
    }

    /**
     * 禁用系统代理
     */
    @Override
    public void disableSystemProxy() {
        try {
            // 禁用代理
            executeCommand("networksetup -setwebproxystate " + activeService + " off");
            executeCommand("networksetup -setsecurewebproxystate " + activeService + " off");
            executeCommand("networksetup -setsocksfirewallproxystate " + activeService + " off");
            logger.info("MacOs proxy disable successful!");
        } catch (Exception ex) {
            logger.error("MacOs proxy disable fail, please manual close proxy in mac setting.", ex);
        }
    }

    /**
     * 设置HTTP代理
     *
     * @param networkService 网络服务
     * @throws IOException
     */
    private void setHttpProxy(String host, int port, String networkService) throws Exception {
        String command = String.format("networksetup -setwebproxy %s %s %d", networkService, host, port);
        executeCommand(command);

        executeCommand("networksetup -setwebproxystate " + networkService + " on");
    }

    /**
     * 设置HTTPS代理
     *
     * @param networkService 网络服务
     * @throws IOException
     */
    private void setHttpsProxy(String host, int port, String networkService) throws Exception {
        String command = String.format("networksetup -setsecurewebproxy %s %s %d", networkService, host, port);
        executeCommand(command);

        executeCommand("networksetup -setsecurewebproxystate " + networkService + " on");
    }

    /**
     * 设置SOCKS代理
     *
     * @param networkService 网络服务
     * @throws IOException
     */
    private void setSocksProxy(String host, int port, String networkService) throws Exception {
        String command = String.format("networksetup -setsocksfirewallproxy %s %s %d", networkService, host, port);
        executeCommand(command);

        executeCommand("networksetup -setsocksfirewallproxystate " + networkService + " on");
    }

    /**
     * 设置代理排除列表（忽略的主机和域名）
     *
     * @param networkService 网络服务名称（如 "Wi-Fi", "Ethernet"）
     * @param bypassDomains 要排除的域名和主机列表
     */
    private void setProxyBypassList(String networkService, List<String> bypassDomains) throws Exception {
        if (networkService == null || networkService.trim().isEmpty()) {
            throw new IllegalArgumentException("network service is null!");
        }

        // 将列表转换为逗号分隔的字符串
        String bypassString = String.join(",", bypassDomains);

        // 设置排除列表
        String command = String.format("networksetup -setproxybypassdomains %s %s", networkService, bypassString);
        executeCommand(command);
    }

    /**
     * 获取当前网络服务列表
     *
     * @return 网络服务列表
     * @throws IOException
     */
    private String[] getNetworkServices() throws IOException {
        Process process = Runtime.getRuntime().exec("networksetup -listallnetworkservices");
        BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));

        // 跳过第一行（标题）
        reader.readLine();
        return reader.lines().filter(line -> !line.trim().isEmpty()).toArray(String[]::new);
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
