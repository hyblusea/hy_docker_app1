# MiMo API 测试指南

## 📋 需要从浏览器获取的信息

### 第一步：访问网站并登录

1. 打开浏览器，访问：https://aistudio.xiaomimimo.com/
2. 使用小米账号登录

### 第二步：打开开发者工具

1. 按 `F12` 打开开发者工具
2. 切换到 **Network（网络）** 标签
3. 勾选 **Preserve log（保留日志）**

### 第三步：发起一次对话

1. 在网页上输入任意消息并发送
2. 在Network面板中找到名为 `chat?xiaomichatbot_ph=...` 的请求
3. 点击该请求，查看详情

### 第四步：获取Cookie信息

在请求详情中，找到 **Request Headers（请求头）** 部分，查看 `Cookie:` 字段：

```
Cookie: userId=xxxxx; xiaomichatbot_ph=xxxxx; serviceToken=xxxxx; 其他cookie...
```

**需要提取的三个关键值：**
- `userId` - 用户ID
- `xiaomichatbot_ph` - 认证凭证
- `serviceToken` - 服务令牌

### 第五步：验证请求参数

在同一个请求中，查看 **Payload（请求体）** 部分，确认以下参数：

```json
{
  "msgId": "32位UUID（无连字符）",
  "conversationId": "32位UUID（无连字符）",
  "query": "你发送的消息",
  "messages": [],
  "parentId": "上一条消息ID（可选）",
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

### 第六步：验证请求头

确认请求头包含以下字段：

```
Content-Type: application/json
Accept: text/event-stream, text/plain, */*
Cookie: userId=...; xiaomichatbot_ph=...; serviceToken=...
User-Agent: Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/120.0.0.0
Referer: https://aistudio.xiaomimimo.com/
Origin: https://aistudio.xiaomimimo.com
```

---

## 🔍 详细截图示例

### 1. 找到chat请求

在Network面板中，找到类似这样的请求：
```
chat?xiaomichatbot_ph=eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...
```

### 2. 查看Cookie

在Headers标签下，找到Request Headers中的Cookie：

示例：
```
userId=123456789; 
xiaomichatbot_ph=eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjM0NTY3ODkwIiwibmFtZSI6IkpvaG4gRG9lIiwiaWF0IjoxNTE2MjM5MDIyfQ.SflKxwRJSMeKKF2QT4fwpMeJf36POk6yJV_adQssw5c; 
serviceToken=abcdef123456789;
```

### 3. 复制这三个值

将这三个值复制下来，格式为：
```
xiaomichatbot_ph.userId.serviceToken
```

例如：
```
eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjM0NTY3ODkwIiwibmFtZSI6IkpvaG4gRG9lIiwiaWF0IjoxNTE2MjM5MDIyfQ.SflKxwRJSMeKKF2QT4fwpMeJf36POk6yJV_adQssw5c.123456789.abcdef123456789
```

---

## ⚠️ 注意事项

1. **Cookie有效期**：Cookie可能会过期，如果测试失败，请重新获取
2. **完整Cookie**：确保复制完整的Cookie值，不要遗漏任何字符
3. **URL编码**：xiaomichatbot_ph可能包含特殊字符，需要进行URL编码
4. **请求频率**：不要频繁请求，避免被限流

---

## 📝 填写信息模板

请将你从浏览器获取的信息填写到下面的模板中：

```
userId: [你的userId]
xiaomichatbot_ph: [你的xiaomichatbot_ph]
serviceToken: [你的serviceToken]
```

填写完成后，我会创建完整的测试脚本进行验证。
