const https = require('https');
const crypto = require('crypto');

const CONFIG = {
  userId: '2937012631',
  xiaomichatbotPh: 'b109A8ByzzXVgKrToJ8uFQ==',
  serviceToken: '/Qzv9hyEQZikhu2h7j2kvFiFJIoPjgLHzdhQAm7xyQkKnH4D4g28Xac4J2LplmjNZTG7fD/8BB7TojhGP51gcsiw64Nl2EhhQYdJ1MuOL0rPaCJ85lvWDhCHMh2hqDgNV1BKI0lyr/4qTz+tLYowN3c/APlMlk0jPgJLaIapOZhbFj7tqFPhWqLGjp/aGuImA2UEc29H8p3onlZewLIeQOYjGkkZ79AMN0RY9XdsnZAxvVjhnIok9NIdZiUGsO7p/LQ/DNc5ZgKbQs5LLDHLOOFdB0HJtsibs8o9ZOBb0lkoRZJXo6wCBKkLIGoEQUhhO4i728TMf/DIyfgzrCeuOjH4QUt8W4FLIBolnJUNINQ=',
};

function uuid() { return crypto.randomUUID().replace(/-/g, ''); }

const body = JSON.stringify({
  msgId: uuid(),
  conversationId: uuid().substring(0, 32),
  query: '请生成一个简单的双均线策略Java代码，包名com.tradingx.strategy，只需输出策略名称和java代码块',
  messages: [],
  parentId: null,
  save: true,
  source: 'STATION',
  scene: 'STATION',
  thinking: { type: 'disabled' },
  modelConfig: { enableThinking: false, model: 'mimo-v2.5-pro', temperature: 0.7, topP: 0.95 },
  multiMedias: []
});

const urlPath = '/open-apis/bot/chat?xiaomichatbot_ph=' + encodeURIComponent(CONFIG.xiaomichatbotPh);
const options = {
  hostname: 'aistudio.xiaomimimo.com',
  path: urlPath,
  method: 'POST',
  headers: {
    'Content-Type': 'application/json',
    'Accept': 'text/event-stream, text/plain, */*',
    'Cookie': `userId=${CONFIG.userId}; xiaomichatbot_ph=${CONFIG.xiaomichatbotPh}; serviceToken=${CONFIG.serviceToken}`,
    'User-Agent': 'Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36',
    'Referer': 'https://aistudio.xiaomimimo.com/',
    'Origin': 'https://aistudio.xiaomimimo.com'
  }
};

console.log('=== MiMo API </think Separation Test ===\n');

const req = https.request(options, (res) => {
  let rawAll = '';
  let buffer = '';

  res.on('data', (chunk) => {
    buffer += chunk.toString();
    const lines = buffer.split('\n');
    buffer = lines.pop();
    for (const line of lines) {
      if (!line.startsWith('data:')) continue;
      const data = line.substring(5).trim();
      if (!data || data === '[DONE]') continue;
      try {
        const parsed = JSON.parse(data);
        const c = parsed.content || parsed.text || '';
        if (c) rawAll += c;
      } catch {}
    }
  });

  res.on('end', () => {
    const fullText = rawAll.replace(/\u0000/g, '');

    const thinkEndIdx = fullText.indexOf('</think');
    let reasoning, content;

    if (thinkEndIdx >= 0) {
      const beforeThinkEnd = fullText.substring(0, thinkEndIdx);
      const thinkStartIdx = beforeThinkEnd.indexOf('<think');
      reasoning = thinkStartIdx >= 0 ? beforeThinkEnd.substring(thinkStartIdx) : beforeThinkEnd;
      content = fullText.substring(thinkEndIdx + '</think'.length);
      while (content.startsWith('>') || content.startsWith('\n') || content.startsWith('\r')) {
        content = content.substring(1);
      }
    } else {
      reasoning = '';
      content = fullText;
    }

    console.log('=== SEPARATION RESULT ===');
    console.log(`Has </think tag: ${thinkEndIdx >= 0}`);
    console.log(`Reasoning length: ${reasoning.length}`);
    console.log(`Content length: ${content.length}`);

    console.log('\n--- REASONING (first 300 chars) ---');
    console.log(reasoning.substring(0, 300));

    console.log('\n--- CONTENT (first 800 chars) ---');
    console.log(content.substring(0, 800));

    console.log('\n--- CONTENT (last 300 chars) ---');
    console.log(content.substring(Math.max(0, content.length - 300)));

    if (content.includes('```java')) {
      console.log('\n✅ SUCCESS: Content contains ```java code block!');
    } else {
      console.log('\n❌ FAIL: No ```java code block in content');
    }

    if (content.includes('package com.tradingx.strategy')) {
      console.log('✅ SUCCESS: Content contains correct package declaration!');
    } else {
      console.log('❌ FAIL: No correct package declaration in content');
    }
  });
});

req.on('error', (e) => console.error('Error:', e.message));
req.write(body);
req.end();
