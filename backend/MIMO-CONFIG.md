# MiMo AI 集成配置指南

## 📋 概述

TradingX项目现已支持MiMo AI作为策略生成引擎。MiMo相比DeepSeek具有以下优势：

- ✅ **无PoW挑战** - 无需复杂的工作量证明计算
- ✅ **实现简单** - API调用流程简洁
- ✅ **稳定性高** - 无复杂的会话管理
- ✅ **支持多模态** - 支持图像、视频、音频分析

## 🔧 配置方式

### 方式一：环境变量配置（推荐）

在运行应用前设置以下环境变量：

```bash
# MiMo配置（优先使用）
export MIMO_USER_ID="你的userId"
export MIMO_XIAOMICHATBOT_PH="你的xiaomichatbot_ph"
export MIMO_SERVICE_TOKEN="你的serviceToken"

# DeepSeek配置（备用）
export DEEPSEEK_USER_TOKEN="你的userToken"
```

### 方式二：配置文件配置

在 `application.yml` 中直接配置：

```yaml
mimo:
  user-id: "你的userId"
  xiaomichatbot-ph: "你的xiaomichatbot_ph"
  service-token: "你的serviceToken"

deepseek:
  user-token: "你的userToken"
```

### 方式三：Docker环境变量

使用Docker运行时，通过 `-e` 参数传递：

```bash
docker run -d \
  -e MIMO_USER_ID="你的userId" \
  -e MIMO_XIAOMICHATBOT_PH="你的xiaomichatbot_ph" \
  -e MIMO_SERVICE_TOKEN="你的serviceToken" \
  -p 8080:8080 \
  tradingx-backend:latest
```

## 🔑 获取MiMo凭证

### 步骤1：访问网站

访问 https://aistudio.xiaomimimo.com/ 并使用小米账号登录

### 步骤2：打开开发者工具

按 `F12` 打开开发者工具，切换到 **Network** 标签

### 步骤3：发起对话

在网页上发送任意消息

### 步骤4：获取Cookie

在Network面板中找到 `chat?xiaomichatbot_ph=...` 请求，查看Request Headers中的Cookie：

```
Cookie: userId=2937012631; xiaomichatbot_ph=b109A8ByzzXVgKrToJ8uFQ==; serviceToken=/Qzv9hyE...
```

### 步骤5：提取三个关键值

从Cookie中提取：
- `userId` - 用户ID
- `xiaomichatbot_ph` - 认证凭证
- `serviceToken` - 服务令牌

## 🎯 使用优先级

系统会按以下优先级选择AI客户端：

1. **MiMo** - 如果配置了MiMo凭证，优先使用MiMo
2. **DeepSeek** - 如果没有配置MiMo，使用DeepSeek
3. **错误** - 如果都没有配置，抛出异常

## 📝 示例配置

### 完整的环境变量配置示例

```bash
# MiMo配置
export MIMO_USER_ID="2937012631"
export MIMO_XIAOMICHATBOT_PH="b109A8ByzzXVgKrToJ8uFQ=="
export MIMO_SERVICE_TOKEN="/Qzv9hyEQZikhu2h7j2kvFiFJIoPjgLHzdhQAm7xyQkKnH4D4g28Xac4J2LplmjNZTG7fD/8BB7TojhGP51gcsiw64Nl2EhhQYdJ1MuOL0rPaCJ85lvWDhCHMh2hqDgNV1BKI0lyr/4qTz+tLYowN3c/APlMlk0jPgJLaIapOZhbFj7tqFPhWqLGjp/aGuImA2UEc29H8p3onlZewLIeQOYjGkkZ79AMN0RY9XdsnZAxvVjhnIok9NIdZiUGsO7p/LQ/DNc5ZgKbQs5LLDHLOOFdB0HJtsibs8o9ZOBb0lkoRZJXo6wCBKkLIGoEQUhhO4i728TMf/DIyfgzrCeuOjH4QUt8W4FLIBolnJUNINQ="

# 启动应用
java -jar backend.jar
```

### Docker Compose配置示例

```yaml
version: '3'
services:
  tradingx-backend:
    image: tradingx-backend:latest
    ports:
      - "8080:8080"
    environment:
      - MIMO_USER_ID=2937012631
      - MIMO_XIAOMICHATBOT_PH=b109A8ByzzXVgKrToJ8uFQ==
      - MIMO_SERVICE_TOKEN=/Qzv9hyEQZikhu2h7j2kvFiFJIoPjgLHzdhQAm7xyQkKnH4D4g28Xac4J2LplmjNZTG7fD/8BB7TojhGP51gcsiw64Nl2EhhQYdJ1MuOL0rPaCJ85lvWDhCHMh2hqDgNV1BKI0lyr/4qTz+tLYowN3c/APlMlk0jPgJLaIapOZhbFj7tqFPhWqLGjp/aGuImA2UEc29H8p3onlZewLIeQOYjGkkZ79AMN0RY9XdsnZAxvVjhnIok9NIdZiUGsO7p/LQ/DNc5ZgKbQs5LLDHLOOFdB0HJtsibs8o9ZOBb0lkoRZJXo6wCBKkLIGoEQUhhO4i728TMf/DIyfgzrCeuOjH4QUt8W4FLIBolnJUNINQ=
```

## ⚠️ 注意事项

1. **Cookie有效期** - Cookie可能会过期，如果AI生成失败，请重新获取
2. **优先级** - 同时配置MiMo和DeepSeek时，优先使用MiMo
3. **安全性** - 不要将凭证提交到版本控制系统
4. **网络环境** - 确保能访问 aistudio.xiaomimimo.com

## 🔍 验证配置

启动应用后，查看日志：

```
✅ MiMo配置成功：
   AI Strategy Service initialized with MiMo client

❌ 配置失败：
   AI Strategy Service initialized without any AI client
```

## 📚 相关文件

- `MimoWebClient.java` - MiMo API客户端实现
- `MimoConfig.java` - MiMo配置类
- `AiStrategyService.java` - AI策略生成服务
- `application.yml` - 配置文件

## 🚀 测试

配置完成后，可以通过以下方式测试：

1. 启动应用
2. 访问策略生成接口
3. 查看日志确认使用了MiMo客户端

## 💡 故障排查

### 问题1：AI生成失败

**原因**：Cookie已过期

**解决**：重新获取Cookie并更新配置

### 问题2：连接超时

**原因**：网络无法访问MiMo服务

**解决**：检查网络连接，确保能访问 aistudio.xiaomimimo.com

### 问题3：配置未生效

**原因**：环境变量未正确设置

**解决**：检查环境变量名称和值是否正确
