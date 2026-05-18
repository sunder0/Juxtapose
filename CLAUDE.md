# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build

```bash
# Full build (skips tests, produces distributable packages)
sh install.sh
# Equivalent to: mvn -Dmaven.test.skip=true clean package install assembly:assembly -U

# Build without assembly
mvn clean package -DskipTests
```

Output: `target/juxtapose-all-<version>-client/` and `target/juxtapose-all-<version>-server/`.

Tests are disabled by default (`maven.test.skip=true` in root pom.xml). To run them:
```bash
mvn test                           # All tests
mvn test -Dtest=ClassName          # Single test class
mvn test -pl juxtapose-common      # Single module
```

**JDK requirement:** JDK 1.8 with JavaFX bundled (tested on 1.8.0_441). Standard JDK 8 without JavaFX will fail to start the client.

## Running

Both client and server require `JUXTAPOSE_HOME` environment variable pointing to the project root.

```bash
sh bin/startup_server.sh   # com.sunder.juxtapose.server.StandardServer
sh bin/startup_client.sh   # com.sunder.juxtapose.client.StandardClient (macOS)
bin/startup_client.cmd     # Windows client
```

Remote debug port: 9556 (configured in startup scripts).

## Architecture

Juxtapose is a proxy application (HTTP + SOCKS5 + proprietary JUXTA protocol) built on **Netty 4** with a **JavaFX** GUI. It has three Maven modules:

- **juxtapose-common** — shared base classes, lifecycle, config, encryption, connection pooling
- **juxtapose-client** — client proxy with JavaFX UI, rule engine, group strategies, system proxy integration
- **juxtapose-server** — server-side TCP proxy dispatch, session management, TLS termination

### Component Lifecycle

All major components extend `ToplevelComponent → BaseCompositeComponent → BaseComponent` from `juxtapose-common`. The lifecycle is `init() → start() → destroy()`. `ApplicationContext` provides lightweight dependency injection.

`StandardClient` and `StandardServer` are the top-level entry points. Each creates a `ProxyCoreComponent` that wires together all subsystems.

**Modules vs. child components:** Components have two extension points. Child components (added via `addChildComponent`) share the full lifecycle. Modules (added via `addModule`) are service objects (e.g., `DefaultConfigManager`, `DefaultConnectionManager`) retrieved by name via `getModuleByName(name, searchAtParent)`, which walks up the component tree. This is how any component reaches the shared `ConfigManager`.

### Request Flow (Client)

1. `HttpProxyRequestPublisher` / `Socks5ProxyRequestPublisher` — local Netty servers on ports 1201/1200 that accept incoming connections and wrap them in a `ProxyRequest`
2. `ProxyCoreComponent.publishProxyRequest()` — applies the current `ProxyMode` (GLOBAL / DIRECT / RULE)
3. In RULE mode, `ProxyRuleEngine.match()` evaluates rules and returns a `RuleResult` (action + target group)
4. `ProxyServerNodeManager` selects the appropriate upstream `ProxyRequestSubscriber` for the group
5. `ProxyRequestSubscriber` (`JuxtaProxyRequestSubscriber` / `HttpProxyRequestSubscriber` / `DirectForwardingSubscriber`) forwards traffic to the upstream

### Client Internals

- **ProxyServerNodeManager** — manages the set of upstream proxy nodes, tested via latency checks
- **Rule engine** (`client/rule/`) — evaluates `DOMAIN-SUFFIX`, `DOMAIN-KEYWORD`, `IP-CIDR` rules from `conf/proxy_rules.yaml` to decide routing
- **Proxy groups** (`client/group/`) — implements selection strategies: `select`, `url-test`, `fallback`, `load-balance`
- **DNS resolver pool** (`client/dns/`) — pooled async DNS resolvers
- **System proxy** (`client/system/`) — sets OS-level HTTP/SOCKS proxy on Windows and macOS
- **UI** (`client/ui/`) — JavaFX views; real-time updates driven by the pub/sub system

### Server Internals

- **TcpProxyDispatchComponent** — Netty pipeline entry point; dispatches connections to `ProxyTaskPublisher` implementations: `JuxtaProxyTaskPublisher`, `HttpProxyTaskPublisher`, `Socks5ProxyTaskPublisher`, `VMessProxyTaskPublisher`
- **CertComponent** — serves self-signed certificates on a separate port (default 2202) for client TLS setup; cert files live in `conf/cert/server.pem` and `conf/cert/server.key`
- **Session management** (`server/session/`) — tracks active proxy sessions
- **Connection management** (`server/connection/`) — manages upstream connections

### JUXTA Protocol (Custom)

JUXTA is the primary proprietary protocol. The wire format uses Netty's `LengthFieldBasedFrameDecoder` (header: 4-byte length field at offset 0). Each frame starts with a 1-byte `serviceId` that determines message type:

| serviceId | Message type |
|-----------|-------------|
| `PingMessage.SERVICE_ID` | keepalive ping |
| `PongMessage.SERVICE_ID` | keepalive pong |
| `AuthRequestMessage.SERVICE_ID` | auth handshake |
| `AuthResponseMessage.SERVICE_ID` | auth result |
| `ProxyRequestMessage.SERVICE_ID` | new proxy target |
| `ProxyCloseMessage.SERVICE_ID` | explicit connection teardown |

Flow control: `JuxtaProxyTaskPublisher.ProxyRelayMessageHandler` implements Netty write-buffer back-pressure — when the downstream channel becomes non-writable (exceeds 4 MB high-water mark), it calls `channel.config().setAutoRead(false)` on all associated upstream channels; `autoRead` is restored when the buffer drains below 32 KB.

### TLS Constraints

TLS is one-way only (server authentication). The code explicitly throws `UnsupportedOperationException` for mutual TLS. Only PEM format (`encrypt.method=pem`) is supported. TLSv1.3 is conditionally enabled only on JDK ≥ 1.8.0_333; older JDK 8 builds use TLSv1.2 only. For self-signed certificates, the client downloads the server's public key from `encrypt.server.port` (default 2202) — configure `certurl` in `proxy_servers.yaml` entries that use self-signed certs; CA-signed certs do not need this field.

### Known HTTP Proxy Issues

`HTTP_PROXY_REVIEW.md` documents 17 bugs in `HttpProxyTaskPublisher` (server) and `HttpProxyRequestSubscriber` (client). Critical ones: CONNECT URI parsing routes all HTTPS to `0.0.0.0` (fatal for HTTPS), `ProxyTask` thread crash causes permanent service hang without restart, and authentication returns 401 instead of RFC-required 407.

### Configuration

| File | Purpose |
|------|---------|
| `conf/client.properties` | Client settings: proxy mode (RULE/DIRECT/GLOBAL), SOCKS5 port (1200), HTTP port (1201), GeoIP |
| `conf/server.properties` | Server settings: protocol, listen port (443), auth credentials, TLS |
| `conf/proxy_servers.yaml` | Upstream node definitions and group definitions |
| `conf/proxy_rules.yaml` | Domain/IP routing rules |
| `conf/logback.xml` | Logback config (auto-scans every 10s; 200MB rolling files) |

`ConfigManager` handles hot-reload: it watches config files and notifies `ConfigListener` implementations.

The `Default` proxy group in `proxy_servers.yaml` has special significance: in RULE mode it is the catch-all group when no rule matches. Renaming it requires updating `proxy_rules.yaml` as well.

### Key Dependencies

- **Netty 4.1.77** — all networking
- **JavaFX** (bundled JDK 1.8.0_441) — GUI
- **BouncyCastle 1.68** — TLS/certificate generation (`common/encrypt/`)
- **MaxMind GeoIP2 2.16.1** — IP geolocation for rules
- **Caffeine 2.9.3** — caching
- **SnakeYAML 2.0** — YAML config parsing
- **Hutool 5.8.22** — general utilities
