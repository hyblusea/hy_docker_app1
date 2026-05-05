# 🚀 MiMo API 测试 - 快速开始

## 📁 已创建的文件

1. ✅ **MiMo-测试指南.md** - 详细的浏览器抓包指南
2. ✅ **test-mimo-api.js** - Node.js完整测试脚本
3. ✅ **test-mimo-api.py** - Python完整测试脚本

---

## 🎯 下一步操作

### 第一步：获取Cookie信息

请按照 **MiMo-测试指南.md** 中的步骤，从浏览器获取以下三个关键值：

```
1. userId          - 用户ID
2. xiaomichatbot_ph - 认证凭证
3. serviceToken    - 服务令牌
```

### 第二步：填写配置

将获取到的信息填写到测试脚本中：

**Node.js版本** (`test-mimo-api.js`)：
```javascript
const CONFIG = {
    userId: '你的userId',
    xiaomichatbot_ph: '你的xiaomichatbot_ph',
    serviceToken: '你的serviceToken',
    // ...
};
```

**Python版本** (`test-mimo-api.py`)：
```python
CONFIG = {
    'userId': '你的userId',
    'xiaomichatbot_ph': '你的xiaomichatbot_ph',
    'serviceToken': '你的serviceToken',
    # ...
}
```

### 第三步：运行测试

**Node.js版本**：
```bash
node test-mimo-api.js
```

**Python版本**：
```bash
python test-mimo-api.py
```

---

## 🔍 关键请求参数（已完整实现）

### 请求头（Headers）

```json
{
  "Content-Type": "application/json",
  "Accept": "text/event-stream, text/plain, */*",
  "Cookie": "userId=...; xiaomichatbot_ph=...; serviceToken=...",
  "User-Agent": "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/120.0.0.0",
  "Referer": "https://aistudio.xiaomimimo.com/",
  "Origin": "https://aistudio.xiaomimimo.com"
}
```

### 请求体（Payload）

```json
{
  "msgId": "32位UUID（无连字符）",
  "conversationId": "32位UUID（无连字符）",
  "query": "用户消息",
  "messages": [],
  "parentId": null,
  "save": true,
  "source": "STATION",
  "scene": "STATION",
  "modelConfig": {
    "enableThinking": false,
    "model": "mimo-v2.5",
    "temperature": 0.7,
    "topP": 0.9
  },
  "multiMedias": []
}
```

### API端点

```
POST https://aistudio.xiaomimimo.com/open-apis/bot/chat?xiaomichatbot_ph={ph}
```

---

## ✨ 测试脚本特性

1. ✅ **完整请求头** - 严格按照源码实现，不遗漏任何字段
2. ✅ **完整请求体** - 包含所有必要参数
3. ✅ **流式响应处理** - 实时显示AI回复
4. ✅ **多轮对话支持** - 自动管理会话ID
5. ✅ **错误处理** - 详细的错误提示
6. ✅ **配置验证** - 自动检查配置完整性

---

## 🎨 支持的模型

| 模型ID | 说明 |
|--------|------|
| `mimo-v2.5` | 全模态旗舰（推荐） |
| `mimo-v2.5-pro` | 推理增强版 |
| `mimo-v2-flash` | 极速轻量版 |

---

## ⚠️ 注意事项

1. **Cookie有效期** - Cookie可能会过期，如果测试失败请重新获取
2. **完整复制** - 确保复制完整的Cookie值，不要遗漏任何字符
3. **请求频率** - 不要频繁请求，避免被限流
4. **网络环境** - 确保能访问 aistudio.xiaomimimo.com

---

## 📞 获取帮助

如果测试失败，请检查：

1. ✅ Cookie是否正确填写
2. ✅ Cookie是否已过期
3. ✅ 网络连接是否正常
4. ✅ 请求参数是否完整

---

## 🎉 准备好了吗？

1. 打开 **MiMo-测试指南.md**
2. 按照步骤获取Cookie信息
3. 填写到测试脚本中
4. 运行测试！

祝你测试成功！🚀
