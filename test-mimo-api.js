/**
 * MiMo API 完整测试脚本
 * 严格按照源码实现的请求格式
 */

const axios = require('axios');

// ==================== 配置区域 ====================
// 请将从浏览器获取的信息填写在这里
const CONFIG = {
    // 从Cookie中获取的三个关键值
    userId: '2937012631',
    xiaomichatbot_ph: 'b109A8ByzzXVgKrToJ8uFQ==',
    serviceToken: '/Qzv9hyEQZikhu2h7j2kvFiFJIoPjgLHzdhQAm7xyQkKnH4D4g28Xac4J2LplmjNZTG7fD/8BB7TojhGP51gcsiw64Nl2EhhQYdJ1MuOL0rPaCJ85lvWDhCHMh2hqDgNV1BKI0lyr/4qTz+tLYowN3c/APlMlk0jPgJLaIapOZhbFj7tqFPhWqLGjp/aGuImA2UEc29H8p3onlZewLIeQOYjGkkZ79AMN0RY9XdsnZAxvVjhnIok9NIdZiUGsO7p/LQ/DNc5ZgKbQs5LLDHLOOFdB0HJtsibs8o9ZOBb0lkoRZJXo6wCBKkLIGoEQUhhO4i728TMf/DIyfgzrCeuOjH4QUt8W4FLIBolnJUNINQ=',
    
    // 模型配置
    model: 'mimo-v2.5',  // 可选: mimo-v2.5, mimo-v2.5-pro, mimo-v2-flash
    temperature: 0.7,
    topP: 0.9,
    
    // 是否启用思考模式
    enableThinking: false
};

// ==================== 工具函数 ====================

/**
 * 生成UUID（无连字符）
 */
function generateUUID() {
    return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, function(c) {
        const r = Math.random() * 16 | 0;
        const v = c === 'x' ? r : (r & 0x3 | 0x8);
        return v.toString(16);
    }).replace(/-/g, ''); // 移除连字符
}

/**
 * 构建Cookie字符串
 */
function buildCookie() {
    return `userId=${CONFIG.userId}; xiaomichatbot_ph=${CONFIG.xiaomichatbot_ph}; serviceToken=${CONFIG.serviceToken}`;
}

/**
 * 构建请求头（完全按照源码）
 */
function buildHeaders() {
    return {
        'Content-Type': 'application/json',
        'Accept': 'text/event-stream, text/plain, */*',
        'Cookie': buildCookie(),
        'User-Agent': 'Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/120.0.0.0',
        'Referer': 'https://aistudio.xiaomimimo.com/',
        'Origin': 'https://aistudio.xiaomimimo.com'
    };
}

/**
 * 构建请求体（完全按照源码）
 */
function buildPayload(query, conversationId = null, parentId = null) {
    // 处理模型名称（flash模型需要添加-studio后缀）
    let modelName = CONFIG.model.toLowerCase();
    if (modelName.includes('flash') && !modelName.endsWith('-studio')) {
        modelName = `${modelName}-studio`;
    }
    
    return {
        msgId: generateUUID(),
        conversationId: conversationId || generateUUID().substring(0, 32),
        query: query,
        messages: [],
        parentId: parentId,
        save: true,
        source: 'STATION',
        scene: 'STATION',
        modelConfig: {
            enableThinking: CONFIG.enableThinking,
            model: modelName,
            temperature: CONFIG.temperature,
            topP: CONFIG.topP
        },
        multiMedias: []
    };
}

// ==================== API 调用 ====================

/**
 * 发送聊天请求
 */
async function sendChatRequest(query, conversationId = null, parentId = null) {
    const url = `https://aistudio.xiaomimimo.com/open-apis/bot/chat?xiaomichatbot_ph=${encodeURIComponent(CONFIG.xiaomichatbot_ph)}`;
    const headers = buildHeaders();
    const payload = buildPayload(query, conversationId, parentId);
    
    console.log('\n' + '='.repeat(60));
    console.log('🚀 发送请求到 MiMo API');
    console.log('='.repeat(60));
    console.log('URL:', url);
    console.log('\n📋 请求头:');
    console.log(JSON.stringify(headers, null, 2));
    console.log('\n📦 请求体:');
    console.log(JSON.stringify(payload, null, 2));
    console.log('\n' + '='.repeat(60));
    
    try {
        const response = await axios.post(url, payload, {
            headers: headers,
            timeout: 60000,
            responseType: 'stream'
        });
        
        console.log('\n✅ 请求成功！');
        console.log('状态码:', response.status);
        console.log('响应头:', JSON.stringify(response.headers, null, 2));
        console.log('\n' + '='.repeat(60));
        console.log('📥 响应内容（流式）:');
        console.log('='.repeat(60));
        
        let fullResponse = '';
        let lastMsgId = '0';
        let returnedConvId = payload.conversationId;
        
        return new Promise((resolve, reject) => {
            response.data.on('data', (chunk) => {
                const chunkStr = chunk.toString('utf8');
                
                const lines = chunkStr.split('\n');
                for (const line of lines) {
                    if (line.startsWith('id:')) {
                        lastMsgId = line.replace('id:', '').trim();
                    }
                    if (line.startsWith('event:')) {
                        // 事件类型，可以忽略
                    }
                    if (line.startsWith('data:')) {
                        try {
                            const dataStr = line.replace('data:', '').trim();
                            if (dataStr === '[DONE]' || !dataStr) {
                                continue;
                            }
                            const data = JSON.parse(dataStr);
                            
                            // 提取对话ID
                            if (data.conversationId) {
                                returnedConvId = data.conversationId;
                            }
                            
                            // 提取消息ID
                            if (data.id) {
                                lastMsgId = data.id;
                            }
                            
                            // 提取回复内容 (注意：字段名是content，不是text)
                            if (data.content) {
                                process.stdout.write(data.content);
                                fullResponse += data.content;
                            }
                            
                            // 提取思考内容（如果有）
                            if (data.thinking) {
                                console.log('\n[思考中...]');
                                process.stdout.write(data.thinking);
                            }
                            
                        } catch (e) {
                            // 忽略解析错误
                        }
                    }
                }
            });
            
            response.data.on('end', () => {
                console.log('\n\n' + '='.repeat(60));
                console.log('🎉 响应完成！');
                console.log('='.repeat(60));
                console.log('会话ID:', returnedConvId);
                console.log('消息ID:', lastMsgId);
                console.log('完整回复长度:', fullResponse.length, '字符');
                
                resolve({
                    conversationId: returnedConvId,
                    msgId: lastMsgId,
                    response: fullResponse
                });
            });
            
            response.data.on('error', (error) => {
                console.error('\n❌ 流式响应错误:', error);
                reject(error);
            });
        });
        
    } catch (error) {
        console.error('\n❌ 请求失败！');
        console.error('错误信息:', error.message);
        
        if (error.response) {
            console.error('状态码:', error.response.status);
            console.error('响应数据:', error.response.data);
        }
        
        throw error;
    }
}

/**
 * 验证配置
 */
function validateConfig() {
    console.log('🔍 验证配置...');
    
    if (CONFIG.userId === 'YOUR_USER_ID_HERE' ||
        CONFIG.xiaomichatbot_ph === 'YOUR_PH_HERE' ||
        CONFIG.serviceToken === 'YOUR_SERVICE_TOKEN_HERE') {
        console.error('\n❌ 错误：请先在CONFIG中填写从浏览器获取的信息！');
        console.log('\n请参考 MiMo-测试指南.md 获取以下信息：');
        console.log('  - userId');
        console.log('  - xiaomichatbot_ph');
        console.log('  - serviceToken');
        return false;
    }
    
    console.log('✅ 配置验证通过');
    console.log('  - userId:', CONFIG.userId);
    console.log('  - xiaomichatbot_ph:', CONFIG.xiaomichatbot_ph.substring(0, 50) + '...');
    console.log('  - serviceToken:', CONFIG.serviceToken);
    console.log('  - model:', CONFIG.model);
    
    return true;
}

// ==================== 主函数 ====================

async function main() {
    console.log('\n' + '='.repeat(60));
    console.log('MiMo API 测试');
    console.log('='.repeat(60));
    
    // 验证配置
    if (!validateConfig()) {
        return;
    }
    
    try {
        // 测试1：简单对话
        console.log('\n📝 测试1：简单对话');
        const result1 = await sendChatRequest('你好，请用一句话介绍你自己');
        
        // 测试2：多轮对话（使用相同的会话ID）
        console.log('\n\n📝 测试2：多轮对话');
        const result2 = await sendChatRequest(
            '刚才我说了什么？',
            result1.conversationId,
            result1.msgId
        );
        
        console.log('\n\n' + '='.repeat(60));
        console.log('✨ 所有测试完成！');
        console.log('='.repeat(60));
        
    } catch (error) {
        console.error('\n\n💥 测试失败:', error.message);
        console.log('\n可能的原因：');
        console.log('  1. Cookie已过期，请重新获取');
        console.log('  2. 网络连接问题');
        console.log('  3. 请求参数错误');
    }
}

// 执行测试
main();
