/**
 * DeepSeek Web API 测试脚本
 * 模仿 Chat2API 的调用方式
 */

const https = require('https');

// 配置 - 需要替换为你自己的Token
const CONFIG = {
    token: 'DIVkqH2N1ytv2BTcFUZfAa4HVOegzrY1tZ8pp0+Eg34V1kStYcQKSkpza9+Az1/8', // 从浏览器获取
    apiEndpoint: 'chat.deepseek.com',
    chatPath: '/api/v0/chat/completion'
};

// 生成UUID
function generateUUID() {
    return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, function(c) {
        const r = Math.random() * 16 | 0;
        const v = c === 'x' ? r : (r & 0x3 | 0x8);
        return v.toString(16);
    });
}

// 请求头配置 (模拟浏览器)
const headers = {
    'Content-Type': 'application/json',
    'Accept': '*/*',
    'Accept-Encoding': 'gzip, deflate, br',
    'Accept-Language': 'zh-CN,zh;q=0.9,en;q=0.8',
    'Origin': 'https://chat.deepseek.com',
    'Referer': 'https://chat.deepseek.com/',
    'Sec-Ch-Ua': '"Chromium";v="134", "Not-A.Brand";v="24", "Google Chrome";v="134"',
    'Sec-Ch-Ua-Mobile': '?0',
    'Sec-Ch-Ua-Platform': '"macOS"',
    'Sec-Fetch-Dest': 'empty',
    'Sec-Fetch-Mode': 'cors',
    'Sec-Fetch-Site': 'same-origin',
    'User-Agent': 'Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/134.0.0.0 Safari/537.36',
    'X-App-Version': '20241129.1',
    'X-Client-Locale': 'zh-CN',
    'X-Client-Platform': 'web',
    'X-Client-Version': '1.6.1'
};

// 构建请求体 (DeepSeek原生格式)
function buildRequestBody(message, model = 'deepseek-chat', chatSessionId = null) {
    return {
        chat_session_id: chatSessionId || generateUUID(),  // 会话ID
        prompt: message,  // 用户消息
        ref_file_ids: [],  // 引用的文件ID列表，暂时为空
        model: model,
        stream: false,  // 非流式响应
        temperature: 0.7,
        max_tokens: 2048
    };
}

// 发送请求
function sendChatRequest(message) {
    return new Promise((resolve, reject) => {
        const requestBody = buildRequestBody(message);
        const postData = JSON.stringify(requestBody);

        const options = {
            hostname: CONFIG.apiEndpoint,
            port: 443,
            path: CONFIG.chatPath,
            method: 'POST',
            headers: {
                ...headers,
                'Authorization': `Bearer ${CONFIG.token}`,
                'X-Request-Id': generateUUID(),  // 每次请求生成新的ID
                'Content-Length': Buffer.byteLength(postData)
            }
        };

        console.log('\n🚀 发送请求到 DeepSeek API...');
        console.log('URL:', `https://${CONFIG.apiEndpoint}${CONFIG.chatPath}`);
        console.log('请求体:', JSON.stringify(requestBody, null, 2));

        const req = https.request(options, (res) => {
            let data = '';

            res.on('data', (chunk) => {
                data += chunk;
            });

            res.on('end', () => {
                console.log('\n📥 响应状态码:', res.statusCode);
                console.log('响应头:', JSON.stringify(res.headers, null, 2));

                if (res.statusCode === 200) {
                    try {
                        const response = JSON.parse(data);
                        console.log('\n✅ 响应成功!');
                        console.log('响应内容:', JSON.stringify(response, null, 2));
                        resolve(response);
                    } catch (e) {
                        console.log('\n❌ JSON解析失败');
                        console.log('原始数据:', data);
                        reject(e);
                    }
                } else {
                    console.log('\n❌ 请求失败');
                    console.log('错误信息:', data);
                    reject(new Error(`HTTP ${res.statusCode}: ${data}`));
                }
            });
        });

        req.on('error', (e) => {
            console.error('\n❌ 请求错误:', e.message);
            reject(e);
        });

        req.write(postData);
        req.end();
    });
}

// 验证Token有效性
function checkToken() {
    return new Promise((resolve, reject) => {
        const options = {
            hostname: CONFIG.apiEndpoint,
            port: 443,
            path: '/api/v0/users/current',
            method: 'GET',
            headers: {
                ...headers,
                'Authorization': `Bearer ${CONFIG.token}`
            }
        };

        console.log('\n🔍 验证Token有效性...');

        const req = https.request(options, (res) => {
            let data = '';
            res.on('data', (chunk) => data += chunk);
            res.on('end', () => {
                if (res.statusCode === 200) {
                    console.log('✅ Token有效');
                    try {
                        const user = JSON.parse(data);
                        console.log('用户信息:', JSON.stringify(user, null, 2));
                        resolve(true);
                    } catch (e) {
                        resolve(true);
                    }
                } else {
                    console.log('❌ Token无效或已过期');
                    console.log('响应:', data);
                    resolve(false);
                }
            });
        });

        req.on('error', reject);
        req.end();
    });
}

// 主函数
async function main() {
    console.log('='.repeat(60));
    console.log('DeepSeek Web API 测试');
    console.log('='.repeat(60));

    // 检查Token配置
    if (CONFIG.token === 'YOUR_DEEPSEEK_USER_TOKEN_HERE') {
        console.log('\n⚠️  请先配置Token!');
        console.log('\n获取Token步骤:');
        console.log('1. 访问 https://chat.deepseek.com/');
        console.log('2. 登录并开始任意对话');
        console.log('3. 按 F12 打开开发者工具');
        console.log('4. Application → Local Storage → chat.deepseek.com');
        console.log('5. 找到 userToken 并复制值');
        console.log('6. 粘贴到本脚本的 CONFIG.token 中');
        return;
    }

    try {
        // 验证Token
        const isValid = await checkToken();
        if (!isValid) {
            console.log('\n请获取新的Token后重试');
            return;
        }

        // 发送测试消息
        console.log('\n' + '='.repeat(60));
        const response = await sendChatRequest('你好，请用一句话介绍你自己');
        console.log('\n' + '='.repeat(60));
        console.log('🎉 测试完成!');

    } catch (error) {
        console.error('\n💥 测试失败:', error.message);
    }
}

// 执行
main();
