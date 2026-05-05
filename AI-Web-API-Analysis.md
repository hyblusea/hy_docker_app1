# AI Web API 逆向工程分析报告

## 📊 项目对比总结

| 特性 | MiMo (小米) | DeepSeek |
|------|------------|----------|
| **复杂度** | ⭐⭐ 简单 | ⭐⭐⭐⭐ 复杂 |
| **Token格式** | `ph.uid.token` 三段式 | `userToken` 单字符串 |
| **反逆向机制** | 无 | **PoW挑战验证** |
| **会话管理** | 简单conversationId | 需要创建chat_session |
| **API端点** | 单一端点 | 多步骤流程 |
| **推荐指数** | ⭐⭐⭐⭐⭐ | ⭐⭐⭐ |

## 🎯 结论：MiMo 更简单！

**原因**：
1. ✅ 无PoW挑战验证（DeepSeek需要复杂的算法计算）
2. ✅ Token获取简单（直接拼接三段即可）
3. ✅ API调用流程简单（单步请求）
4. ✅ 无需创建会话（DeepSeek需要先创建chat_session）

---

## 🔧 MiMo 实现详解

### 1. Token 获取

**方式一：从浏览器获取**
```
访问：https://aistudio.xiaomimimo.com/
登录后打开开发者工具 → Application → Cookies
找到：xiaomichatbot_ph, userId, serviceToken
格式：xiaomichatbot_ph.userId.serviceToken
```

**方式二：从请求头获取**
```
在Network面板找到任意请求
查看Cookie字段
提取三个关键值
```

### 2. API 调用

**端点**：
```
POST https://aistudio.xiaomimimo.com/open-apis/bot/chat?xiaomichatbot_ph={ph}
```

**请求头**：
```json
{
  "Content-Type": "application/json",
  "Accept": "text/event-stream, text/plain, */*",
  "Cookie": "userId={userId}; xiaomichatbot_ph={ph}; serviceToken={token}",
  "User-Agent": "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36",
  "Referer": "https://aistudio.xiaomimimo.com/",
  "Origin": "https://aistudio.xiaomimimo.com"
}
```

**请求体**：
```json
{
  "model": "mimo-v2.5",
  "query": "你好，请介绍一下你自己",
  "conversationId": "uuid",
  "msgId": "uuid",
  "multiMedias": [],
  "stream": true
}
```

### 3. 模型列表

| 模型ID | 能力 | 说明 |
|--------|------|------|
| `mimo-v2.5` | 全模态 | 视觉、音频、多模态理解最佳 |
| `mimo-v2.5-pro` | 推理增强 | 逻辑严密、最强搜索与长文本 |
| `mimo-v2-flash` | 极速轻量 | 毫秒级响应，适合简单对话 |

---

## 🔧 DeepSeek 实现详解

### 1. Token 获取

```
访问：https://chat.deepseek.com/
登录后打开开发者工具 → Application → Local Storage
找到：userToken
```

### 2. API 调用流程（复杂！）

**步骤1：验证Token**
```
GET https://chat.deepseek.com/api/v0/users/current
Authorization: Bearer {token}
```

**步骤2：创建会话**
```
POST https://chat.deepseek.com/api/v0/chat_session/create
Authorization: Bearer {token}
返回：chat_session_id
```

**步骤3：获取PoW挑战** ⚠️ 关键难点
```
POST https://chat.deepseek.com/api/v0/chat/create_pow_challenge
Body: { "target_path": "/api/v0/chat/completion" }
返回：challenge参数（algorithm, challenge, salt, difficulty等）
```

**步骤4：计算PoW答案** ⚠️ 需要特殊算法
```typescript
// 需要实现 DeepSeekHashV1 算法
// 涉及SHA3、WebAssembly等复杂计算
const answer = deepSeekHash.calculateHash(algorithm, challenge, salt, difficulty, expire_at)
```

**步骤5：发送聊天请求**
```
POST https://chat.deepseek.com/api/v0/chat/completion
Headers:
  Authorization: Bearer {token}
  X-Ds-Pow-Response: {base64编码的挑战答案}
  Cookie: {动态生成的Cookie}
Body:
{
  "chat_session_id": "会话ID",
  "prompt": "消息内容",
  "ref_file_ids": [],
  "search_enabled": false,
  "thinking_enabled": false,
  "model_type": "default"
}
```

### 3. PoW挑战算法（核心难点）

DeepSeek使用 **Proof of Work** 机制防止逆向：

```typescript
interface ChallengeResponse {
  algorithm: "DeepSeekHashV1"
  challenge: string
  salt: string
  difficulty: number  // 难度值，通常为3-5
  expire_at: number
  signature: string
}
```

**计算过程**：
1. 使用WebAssembly模块（sha3_wasm）
2. 实现SHA3-256哈希算法
3. 满足难度要求（前N位为0）
4. 生成Base64编码的答案

**难点**：
- 需要逆向WebAssembly模块
- 算法复杂，计算耗时
- 挑战参数动态变化
- 需要在过期时间内完成计算

---

## 📝 测试代码示例

### MiMo 测试（简单）

```javascript
const axios = require('axios');

const MIMO_CONFIG = {
    ph: 'your_ph_here',
    userId: 'your_userId_here',
    token: 'your_serviceToken_here'
};

async function testMimo() {
    const cookie = `userId=${MIMO_CONFIG.userId}; xiaomichatbot_ph=${MIMO_CONFIG.ph}; serviceToken=${MIMO_CONFIG.token}`;
    
    const response = await axios.post(
        `https://aistudio.xiaomimimo.com/open-apis/bot/chat?xiaomichatbot_ph=${encodeURIComponent(MIMO_CONFIG.ph)}`,
        {
            model: 'mimo-v2.5',
            query: '你好，请介绍一下你自己',
            conversationId: generateUUID(),
            msgId: generateUUID(),
            multiMedias: [],
            stream: false
        },
        {
            headers: {
                'Content-Type': 'application/json',
                'Cookie': cookie,
                'User-Agent': 'Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36',
                'Referer': 'https://aistudio.xiaomimimo.com/',
                'Origin': 'https://aistudio.xiaomimimo.com'
            }
        }
    );
    
    console.log(response.data);
}
```

### DeepSeek 测试（复杂）

```javascript
// 需要实现PoW算法，代码量超过500行
// 参考项目：https://github.com/Fu-Jie/deepseek-free-api
// 核心难点：逆向sha3_wasm模块
```

---

## 🚀 推荐方案

### 方案一：使用现成项目（推荐）

**MiMo**：
```bash
docker run -d -p 8000:8000 \
  -e token=ph.userId.token \
  ghcr.io/fu-jie/mimo-free-api-mcp:latest
```

**DeepSeek**：
```bash
docker run -d -p 8000:8000 \
  -e DEEP_SEEK_CHAT_AUTHORIZATION=your_token \
  ghcr.io/fu-jie/deepseek-free-api:latest
```

### 方案二：自己实现

**选择 MiMo**，因为：
1. ✅ 实现简单（约200行代码）
2. ✅ 无复杂算法
3. ✅ 稳定性高
4. ✅ 支持多模态

---

## 📚 参考资源

1. **MiMo项目**：https://github.com/Fu-Jie/mimo-free-api-mcp
2. **DeepSeek项目**：https://github.com/Fu-Jie/deepseek-free-api
3. **Chat2API项目**：https://github.com/xiaoY233/Chat2API

---

## ⚠️ 免责声明

本项目仅供学术研究和学习交流使用，请遵守相关服务的用户协议。
不建议在生产环境中使用逆向API，建议使用官方付费API服务。
