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

console.log('=== MiMo API Raw SSE Analysis ===\n');

const req = https.request(options, (res) => {
  let allLines = [];
  let buffer = '';

  res.on('data', (chunk) => {
    buffer += chunk.toString();
    const lines = buffer.split('\n');
    buffer = lines.pop();
    for (const line of lines) {
      if (line.startsWith('data:')) {
        const data = line.substring(5).trim();
        if (data && data !== '[DONE]') {
          try { allLines.push(JSON.parse(data)); } catch {}
        }
      }
    }
  });

  res.on('end', () => {
    console.log(`Total data lines: ${allLines.length}\n`);

    // Print ALL lines with their fields
    allLines.forEach((line, i) => {
      const keys = Object.keys(line).join(',');
      const content = line.content || '';
      const type = line.type || 'N/A';
      const contentPreview = typeof content === 'string'
        ? content.replace(/\u0000/g, '\\0').substring(0, 100)
        : JSON.stringify(content).substring(0, 100);
      console.log(`[${i+1}] keys={${keys}} type=${type} content="${contentPreview}"`);
    });

    // Now try the \u0000 separation and show the boundary
    console.log('\n=== THINKING/CONTENT BOUNDARY ANALYSIS ===');
    let inThinking = false;
    let thinkingEndLine = -1;
    let contentStartLine = -1;

    allLines.forEach((line, i) => {
      const rawContent = line.content || '';
      if (typeof rawContent !== 'string') return;
      const hasMarker = rawContent.includes('\u0000');

      if (hasMarker && !inThinking) {
        console.log(`Thinking STARTS at line ${i+1}`);
        inThinking = true;
      }

      // Check for thinking end marker - look for </think or similar
      if (inThinking && rawContent.includes('</think')) {
        console.log(`Thinking ENDS at line ${i+1} (found </think)`);
        thinkingEndLine = i + 1;
      }
    });

    // Combine all content to find the boundary
    let fullRaw = '';
    allLines.forEach(line => {
      const c = line.content;
      if (typeof c === 'string') fullRaw += c;
    });

    // Find </think in the full text
    const thinkEndIdx = fullRaw.indexOf('</think');
    if (thinkEndIdx >= 0) {
      console.log(`\nFound </think at position ${thinkEndIdx} in full text`);
      console.log(`Context around </think:`);
      console.log(`  Before: "${fullRaw.substring(Math.max(0, thinkEndIdx - 50), thinkEndIdx).replace(/\u0000/g, '\\0')}"`);
      console.log(`  Tag: "${fullRaw.substring(thinkEndIdx, thinkEndIdx + 20).replace(/\u0000/g, '\\0')}"`);
      console.log(`  After: "${fullRaw.substring(thinkEndIdx + 8, thinkEndIdx + 60).replace(/\u0000/g, '\\0')}"`);
    } else {
      console.log('\nNo </think tag found in full text');
      // Check for other markers
      const thinkTagIdx = fullRaw.indexOf('<think');
      console.log(`Has <think tag: ${thinkTagIdx >= 0}`);

      // Look for the transition from thinking to content
      // The reasoning content usually ends with Chinese text, then content starts with 策略名称 or ```
      const codeBlockIdx = fullRaw.indexOf('```java');
      const strategyNameIdx = fullRaw.indexOf('策略名称');
      console.log(`\`\`\`java found at position: ${codeBlockIdx}`);
      console.log(`策略名称 found at position: ${strategyNameIdx}`);
    }

    console.log('\n=== FULL RAW TEXT (first 3000 chars) ===');
    console.log(fullRaw.substring(0, 3000).replace(/\u0000/g, '\\0'));
  });
});

req.on('error', (e) => console.error('Error:', e.message));
req.write(body);
req.end();
