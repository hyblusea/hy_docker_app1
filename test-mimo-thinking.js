const https = require('https');

const CONFIG = {
  userId: '2937012631',
  xiaomichatbotPh: 'b109A8ByzzXVgKrToJ8uFQ==',
  serviceToken: '/Qzv9hyEQZikhu2h7j2kvFiFJIoPjgLHzdhQAm7xyQkKnH4D4g28Xac4J2LplmjNZTG7fD/8BB7TojhGP51gcsiw64Nl2EhhQYdJ1MuOL0rPaCJ85lvWDhCHMh2hqDgNV1BKI0lyr/4qTz+tLYowN3c/APlMlk0jPgJLaIapOZhbFj7tqFPhWqLGjp/aGuImA2UEc29H8p3onlZewLIeQOYjGkkZ79AMN0RY9XdsnZAxvVjhnIok9NIdZiUGsO7p/LQ/DNc5ZgKbQs5LLDHLOOFdB0HJtsibs8o9ZOBb0lkoRZJXo6wCBKkLIGoEQUhhO4i728TMf/DIyfgzrCeuOjH4QUt8W4FLIBolnJUNINQ=',
};

function uuid() {
  return crypto.randomUUID().replace(/-/g, '');
}

const crypto = require('crypto');

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
  modelConfig: {
    enableThinking: false,
    model: 'mimo-v2.5-pro',
    temperature: 0.7,
    topP: 0.95
  },
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

console.log('=== MiMo API Test ===');
console.log('Model: mimo-v2.5-pro');
console.log('Sending request...\n');

const req = https.request(options, (res) => {
  console.log('Status:', res.statusCode);

  let fullContent = '';
  let fullReasoning = '';
  let inThinkingPhase = false;
  let dataLineCount = 0;
  let firstThinkingLine = null;
  let firstContentLine = null;
  let lastFewLines = [];

  let buffer = '';
  res.on('data', (chunk) => {
    buffer += chunk.toString();
    const lines = buffer.split('\n');
    buffer = lines.pop();

    for (const line of lines) {
      if (!line.startsWith('data:')) continue;
      const data = line.substring(5).trim();
      if (!data || data === '[DONE]') continue;

      dataLineCount++;
      let parsed;
      try { parsed = JSON.parse(data); } catch { continue; }

      const rawContent = parsed.content || parsed.text || '';
      if (!rawContent) continue;

      const hasThinkingMarker = rawContent.includes('\u0000');

      if (hasThinkingMarker) {
        inThinkingPhase = true;
        const cleaned = rawContent.replace(/\u0000/g, '');
        if (cleaned && !firstThinkingLine) {
          firstThinkingLine = `Line #${dataLineCount}: type=${parsed.type}, hasMarker=true, content="${cleaned.substring(0, 80)}..."`;
        }
        fullReasoning += cleaned;
      } else if (inThinkingPhase) {
        fullReasoning += rawContent;
      } else {
        if (!firstContentLine) {
          firstContentLine = `Line #${dataLineCount}: type=${parsed.type}, hasMarker=false, content="${rawContent.substring(0, 80)}..."`;
        }
        fullContent += rawContent;
      }

      if (dataLineCount <= 5) {
        console.log(`[Line #${dataLineCount}] type=${parsed.type}, hasMarker=${hasThinkingMarker}, content="${rawContent.substring(0, 60).replace(/\u0000/g, '\\0')}"`);
      }

      lastFewLines.push(`[Line #${dataLineCount}] type=${parsed.type}, hasMarker=${hasThinkingMarker}, content="${rawContent.substring(0, 80).replace(/\u0000/g, '\\0')}"`);
      if (lastFewLines.length > 5) lastFewLines.shift();
    }
  });

  res.on('end', () => {
    console.log('\n=== RESULTS ===');
    console.log(`Total data lines: ${dataLineCount}`);
    console.log(`Thinking phase detected: ${inThinkingPhase}`);
    console.log(`First thinking line: ${firstThinkingLine || 'NONE'}`);
    console.log(`First content line: ${firstContentLine || 'NONE'}`);
    console.log(`\nReasoning length: ${fullReasoning.length}`);
    console.log(`Content length: ${fullContent.length}`);

    console.log('\n--- Last 5 data lines ---');
    lastFewLines.forEach(l => console.log(l));

    console.log('\n--- REASONING (first 500 chars) ---');
    console.log(fullReasoning.substring(0, 500));

    console.log('\n--- CONTENT (first 1000 chars) ---');
    console.log(fullContent.substring(0, 1000));

    console.log('\n--- CONTENT (last 500 chars) ---');
    console.log(fullContent.substring(Math.max(0, fullContent.length - 500)));

    if (fullContent.length === 0 && fullReasoning.length > 0) {
      console.log('\n⚠️  WARNING: No content found! Only reasoning exists.');
      console.log('This means thinking/content separation did NOT work correctly.');
    } else if (fullContent.length > 0 && fullContent.includes('```java')) {
      console.log('\n✅ SUCCESS: Content contains java code block!');
    } else if (fullContent.length > 0) {
      console.log('\n⚠️  Content exists but no java code block found.');
    }
  });
});

req.on('error', (e) => {
  console.error('Request error:', e.message);
});

req.write(body);
req.end();
