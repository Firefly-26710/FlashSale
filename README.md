# FlashSale 命令手册

## 1. 启动与停止

默认启动（构建并后台运行，默认走分库分表）：

```bash
docker compose up -d --build
```

查看状态：

```bash
docker compose ps
```

结果判断：

- `STATUS` 为 `Up`（MySQL 通常为 `Up ... (healthy)`）表示服务就绪。

停止：

```bash
docker compose down
```

### 1.1 改代码后，确保服务是最新版

全量重建（推荐）：

```bash
docker compose up -d --build --force-recreate
```

仅前后端重建（更快）：

```bash
docker compose up -d --build --force-recreate backend1 backend2 order-service inventory-service frontend gateway-service
```

只改单个服务时，将服务名替换为目标服务（如 `backend1`、`frontend`）。

### 1.2 后端服务角色

- `backend1` / `backend2`：核心后端（core-service），提供登录、商品查询、搜索、缓存与限流。
- `order-service`：订单微服务，处理秒杀下单、订单查询与支付模拟。
- `inventory-service`：库存微服务，负责库存预占、库存回补与 Redis 秒杀库存维护。
- `gateway-service`：Spring Cloud Gateway 网关，通过 Nacos 服务发现动态转发 `/api/**` 请求。
- `nacos`：服务注册中心 + 配置中心（默认控制台端口 `8848`）。

Nginx 路由：

- `/api/orders/**` -> `order-service`
- 其他 `/api/**` -> `backend1` / `backend2` 负载均衡

### 1.3 Nacos + Gateway 验证

目标：验证“注册发现、网关路由、动态配置”三件事是否都可用。

说明：当前已加入 `nacos-config-init` 一次性初始化容器，会把以下集中配置自动发布到 Nacos：

- `infra/nacos/config/flashsale-common.properties`
- `infra/nacos/config/core-service.properties`
- `infra/nacos/config/order-service.properties`
- `infra/nacos/config/inventory-service.properties`

启动完成后，访问 Nacos 控制台：

```bash
http://localhost:8848/nacos
```

#### 1.3.1 服务注册验证

在 Nacos 服务列表中应看到：

- `core-service`（2 个实例）
- `order-service`
- `inventory-service`
- `gateway-service`

#### 1.3.2 网关动态路由验证

作用：确认请求经过网关后，能按路径正确分发到目标服务。

通过网关地址调用（不直连后端端口）：

```bash
curl "http://localhost:8080/api/products"
curl "http://localhost:8080/api/orders/user/1"
```

预期：

- `/api/products` 被路由到 `core-service`
- `/api/orders/**` 被路由到 `order-service`（若未带有效 token，可能返回 400/401，属业务鉴权结果）

#### 1.3.3 Nacos 动态配置热更新验证

作用：确认配置变更可“在线生效”，不需要重启服务。

1. 在 Nacos 新建配置：

- Data ID: `core-service.properties`
- Group: `DEFAULT_GROUP`
- 内容示例：

```properties
flashsale.dynamic.message=hello-from-nacos
```

2. 发布配置后，调用：

```bash
curl "http://localhost:8080/api/config-demo/message"
```

3. 修改 `flashsale.dynamic.message` 并再次发布，再次调用上述接口，返回 `message` 会自动变化，无需重启 `core-service`。

说明：

- 如果 `message` 变化了，说明 Nacos 配置中心 + 客户端刷新机制生效。
- 如果 `message` 不变，优先检查 Data ID、Group、服务名与 `spring.config.import` 是否一致。

## 2. 日志与结果查看

查看所有服务日志：

```bash
docker compose logs -f
```

查看单个服务日志：

```bash
docker compose logs -f mysql-primary
docker compose logs -f mysql-replica
docker compose logs -f proxysql
docker compose logs -f backend1
docker compose logs -f backend2
docker compose logs -f order-service
docker compose logs -f inventory-service
docker compose logs -f frontend
```

## 3. JMeter 压测

### 3.1 静态文件压测

目标：`GET /index.html`

```powershell
powershell -ExecutionPolicy Bypass -File .\jmeter\scripts\run-static-load-test.ps1
```

### 3.2 后端服务压测

目标：`POST /api/auth/login`

```powershell
powershell -ExecutionPolicy Bypass -File .\jmeter\scripts\run-login-load-test.ps1
```

自定义并发参数示例：

```powershell
powershell -ExecutionPolicy Bypass -File .\jmeter\scripts\run-login-load-test.ps1 -Threads 100 -Loops 30 -RampUp 15
```

结果查看：

- 控制台会输出 `Total Requests`、`Average Response Time(ms)`、`P95 Response Time(ms)`。
- 后端服务压测会额外输出 `Backend Distribution (from X-Upstream-Addr)`。
- 文件结果：
- `jmeter/output/static-results.jtl`
- `jmeter/output/static-jmeter.log`
- `jmeter/output/login-results.jtl`
- `jmeter/output/login-jmeter.log`

### 3.3 秒杀并发压测（库存300、并发500）

目标：自动创建测试商品（库存300），发起 500 并发秒杀请求，校验是否超卖、库存守恒、用户去重是否正常。

```powershell
powershell -ExecutionPolicy Bypass -File .\jmeter\scripts\run-seckill-500-test.ps1 -CleanupAfterRun
```

可选参数示例：

```powershell
powershell -ExecutionPolicy Bypass -File .\jmeter\scripts\run-seckill-500-test.ps1 -Threads 500 -RampUp 20 -TargetStock 300 -VerificationTimeoutSec 180 -CleanupAfterRun
```

结果文件：

- `jmeter/output/seckill-500-results.jtl`
- `jmeter/output/seckill-500-jmeter.log`

- 压测结果：`202` 响应 `500/500`，网络层错误 `0`。
- 业务一致性：无超卖、库存守恒、无重复用户下单。
- 架构链路校验：分库分表有命中分布，负载均衡生效，读写分离路由正常。

## 4. 常用地址

- 前端入口：`http://localhost`（默认 `80`）
- 网关（gateway-service）：`http://localhost:8080`
- Nacos 控制台：`http://localhost:8848/nacos`
- 后端实例1（core-service）：`http://localhost:8081`
- 后端实例2（core-service）：`http://localhost:8082`
- 订单服务（order-service）：`http://localhost:8083`
- 库存服务（inventory-service）：`http://localhost:8084`
- MySQL 主库：`localhost:3307`
- MySQL 从库：`localhost:3308`
- ProxySQL 数据端口：`localhost:6033`
- ProxySQL 管理端口：`localhost:6032`
- Elasticsearch：`http://localhost:9200`

## 5. ProxySQL 读写分离

当前 `docker compose` 已内置：

- `mysql-primary`（主库）
- `mysql-replica`（从库）
- `mysql-replica-init`（一次性初始化复制）
- `proxysql`（SQL 路由）

默认链路：后端 -> ShardingSphere-Proxy -> ProxySQL -> MySQL 主从。

如果需要临时切回“仅 ProxySQL（不走分片）”模式：

```powershell
$env:SPRING_DATASOURCE_URL='jdbc:mysql://proxysql:6033/FlashSale?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai'
$env:SPRING_JPA_HIBERNATE_DDL_AUTO='update'
$env:SPRING_JPA_PROPERTIES_HIBERNATE_BOOT_ALLOW_JDBC_METADATA_ACCESS='true'
docker compose up -d --build --force-recreate backend1 backend2
```

## 6. 商品搜索（Elasticsearch）

已新增 Elasticsearch 服务，后端启动后会自动把当前商品数据同步到 `products` 索引（ES 不可用时自动降级到 MySQL 模糊查询）。

搜索接口：

- `GET /api/products/search?q=关键词&size=20`

示例：

```bash
curl "http://localhost/api/products/search?q=鼠标&size=10"
```

## 7. 订单分库分表（ShardingSphere-Proxy）

分片策略：

- 按 `user_id` 分库：`ds0` / `ds1`
- 按 `id`（订单ID）分表：`orders_0` / `orders_1`

默认已启用分片代理模式（启动命令见第 1 节）。

快速校验是否已走分片代理：

```powershell
docker compose exec backend1 printenv | Select-String "SPRING_DATASOURCE_URL"
docker compose exec backend2 printenv | Select-String "SPRING_DATASOURCE_URL"
```

期望输出包含：`jdbc:mysql://shardingsphere-proxy:3307/FlashSale...`

相关配置文件：

- `infra/shardingsphere-proxy/server.yaml`
- `infra/shardingsphere-proxy/config-flashsale.yaml`

分片物理表初始化脚本：

- `backEnd/core-service/src/main/resources/init.sql`

### 7.1 模式切换后秒杀一致性（避免“看不到旧订单/不能重秒”）

当切换了数据源模式（例如从 ProxySQL 切到分片）后，建议执行一次：

```powershell
docker compose exec redis redis-cli --scan --pattern "seckill:users:*" | % { docker compose exec redis redis-cli DEL $_ }
```

这条命令只清理“用户去重集合”，不会删除商品库存键和其他业务数据。