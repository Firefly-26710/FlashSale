# FlashSale 命令手册

## 1. 启动与停止

启动（构建并后台运行）：

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

## 2. 日志与结果查看

查看所有服务日志：

```bash
docker compose logs -f
```

查看单个服务日志：

```bash
docker compose logs -f mysql
docker compose logs -f backend1
docker compose logs -f backend2
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

## 4. 常用地址

- 前端入口：`http://localhost`（默认 `80`）
- 后端实例1：`http://localhost:8081`
- 后端实例2：`http://localhost:8082`
- MySQL：`localhost:3307`

