# Juxtapose
Juxtapose是一款持续迭代中的代理（proxy）软件，目前主要协议支持HTTP和Socks5。界面如图：

![ui](ui.png)

## 部署&运行
> 注意：JDK开发版本为1.8.0_441。由于UI界面使用的是JavaFx，所以需要采用包含JavaFx包的JDK版本。

执行`sh install.sh`, 在target目录下会出现client和server包。server端运行在代理服务器上，client则运行在本地。

client运行:
```
修改conf目录下的proxy_servers.yaml配置，修改器代理节点（server端）

windows: 双击startup_client.cmd
mac: sh startup_client.sh
```

server运行:
```
sh startup_server.sh
```

## 架构
![juxta architecture diagram](https://suyeq.oss-cn-shenzhen.aliyuncs.com/juxta/Juxta%E6%9E%B6%E6%9E%84.png)


## 迭代中的功能
- [x] 身份验证和授权机制
- [x] 私有证书TLS/SSL加密通信
- [x] 代理连接管理
- [x] 本地系统代理设置，参照：https://myth.cx/p/windows-proxy
- [x] 域名ip库GEOIP支持
- [x] 支持Rule规则模式（可配置）、本地直连模式、全局代理模式
- [x] 客户端UI
- [x] 代理组，代理组节点选择策略
- [x] 空闲检测、水位线控制（流量控制）
- [x] 支持HTTP协议
- [x] 支持Socks5协议
- [x] 支持自定义协议
- [x] 支持多平台的IO模型
- [ ] 支持UDP
- [ ] 服务端建立命令Web端，支持客户端与其命令交互（连接关闭、鉴权认证...）
- [ ] 更多的协议（Vmess、Shadowsocks、Trojan...）
- [ ] 插件系统
- [ ] 公有证书加密的支持
- [ ] ...


