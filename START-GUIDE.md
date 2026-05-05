# TradingX 项目启动指南（MiMo已集成）

## 环境要求

已确认的环境变量：
- ✅ JAVA_HOME=D:\Tools\jdk-21.0.7
- ✅ MIMO_USER_ID=2937012631
- ✅ MIMO_XIAOMICHATBOT_PH=b109A8ByzzXVgKrToJ8uFQ==
- ✅ MIMO_SERVICE_TOKEN=(已配置)
- ✅ GITHUB_TOKEN=(已配置)
- ✅ TUSHARE_TOKEN=(已配置)

## 方式一：使用PowerShell（推荐）

### 1. 编译后端

```powershell
cd D:\MyProjects\tradingX\backend
.\build.cmd compile
```

### 2. 启动后端

```powershell
cd D:\MyProjects\tradingX\backend
.\build.cmd spring-boot:run
```

### 3. 安装前端依赖

```powershell
cd D:\MyProjects\tradingX\web
npm install
```

### 4. 启动前端

```powershell
cd D:\MyProjects\tradingX\web
npm run dev
```

## 方式二：使用命令提示符（CMD）

### 1. 编译后端

```cmd
cd D:\MyProjects\tradingX\backend
mvnw.cmd compile
```

### 2. 启动后端

```cmd
cd D:\MyProjects\tradingX\backend
mvnw.cmd spring-boot:run
```

### 3. 安装前端依赖

```cmd
cd D:\MyProjects\tradingX\web
npm install
```

### 4. 启动前端

```cmd
cd D:\MyProjects\tradingX\web
npm run dev
```

## 方式三：使用IDE

### IntelliJ IDEA

1. 打开项目 `D:\MyProjects\tradingX\backend`
2. 等待Maven导入完成
3. 找到 `BackendApplication.java`
4. 右键 → Run 'BackendApplication'

### VS Code

1. 打开 `D:\MyProjects\tradingX\web`
2. 打开终端
3. 运行 `npm install`
4. 运行 `npm run dev`

## 验证MiMo集成

启动后端后，查看日志中是否有：

```
✅ AI Strategy Service initialized with MiMo client
```

如果看到这条日志，说明MiMo已成功集成！

## 访问地址

- 前端：http://localhost:3000
- 后端：http://localhost:8080
- H2控制台：http://localhost:8080/h2-console
- 健康检查：http://localhost:8080/actuator/health

## 故障排查

### 问题1：mvnw.cmd报错

**解决方案**：使用 `build.cmd` 代替 `mvnw.cmd`

### 问题2：JAVA_HOME未找到

**解决方案**：确认系统环境变量中已设置 `JAVA_HOME=D:\Tools\jdk-21.0.7`

### 问题3：MiMo未初始化

**解决方案**：确认环境变量 `MIMO_USER_ID`、`MIMO_XIAOMICHATBOT_PH`、`MIMO_SERVICE_TOKEN` 已正确设置

## 测试AI策略生成

启动成功后，可以通过前端界面测试AI策略生成功能，或者使用API：

```bash
curl -X POST http://localhost:8080/api/strategy/generate \
  -H "Content-Type: application/json" \
  -d '{
    "buyDesc": "当收盘价上穿20日均线时买入",
    "sellDesc": "当收盘价下穿20日均线时卖出"
  }'
```

## 注意事项

1. 确保端口 8080 和 3000 未被占用
2. 首次启动可能需要下载依赖，请耐心等待
3. MiMo Cookie可能会过期，如果AI生成失败请重新获取
