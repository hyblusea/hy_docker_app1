"""
MiMo API 完整测试脚本 (Python版本)
严格按照源码实现的请求格式
"""

import requests
import json
import uuid
from typing import Optional, Dict, Any

# ==================== 配置区域 ====================
# 请将从浏览器获取的信息填写在这里
CONFIG = {
    # 从Cookie中获取的三个关键值
    'userId': 'YOUR_USER_ID_HERE',           # 例如: '123456789'
    'xiaomichatbot_ph': 'YOUR_PH_HERE',      # 例如: 'eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...'
    'serviceToken': 'YOUR_SERVICE_TOKEN_HERE', # 例如: 'abcdef123456789'
    
    # 模型配置
    'model': 'mimo-v2.5',  # 可选: mimo-v2.5, mimo-v2.5-pro, mimo-v2-flash
    'temperature': 0.7,
    'topP': 0.9,
    
    # 是否启用思考模式
    'enableThinking': False
}

# ==================== 工具函数 ====================

def generate_uuid() -> str:
    """生成UUID（无连字符）"""
    return str(uuid.uuid4()).replace('-', '')

def build_cookie() -> str:
    """构建Cookie字符串"""
    return f"userId={CONFIG['userId']}; xiaomichatbot_ph={CONFIG['xiaomichatbot_ph']}; serviceToken={CONFIG['serviceToken']}"

def build_headers() -> Dict[str, str]:
    """构建请求头（完全按照源码）"""
    return {
        'Content-Type': 'application/json',
        'Accept': 'text/event-stream, text/plain, */*',
        'Cookie': build_cookie(),
        'User-Agent': 'Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/120.0.0.0',
        'Referer': 'https://aistudio.xiaomimimo.com/',
        'Origin': 'https://aistudio.xiaomimimo.com'
    }

def build_payload(query: str, conversation_id: Optional[str] = None, parent_id: Optional[str] = None) -> Dict[str, Any]:
    """构建请求体（完全按照源码）"""
    # 处理模型名称（flash模型需要添加-studio后缀）
    model_name = CONFIG['model'].lower()
    if 'flash' in model_name and not model_name.endswith('-studio'):
        model_name = f"{model_name}-studio"
    
    return {
        'msgId': generate_uuid(),
        'conversationId': conversation_id or generate_uuid()[:32],
        'query': query,
        'messages': [],
        'parentId': parent_id,
        'save': True,
        'source': 'STATION',
        'scene': 'STATION',
        'modelConfig': {
            'enableThinking': CONFIG['enableThinking'],
            'model': model_name,
            'temperature': CONFIG['temperature'],
            'topP': CONFIG['topP']
        },
        'multiMedias': []
    }

# ==================== API 调用 ====================

def send_chat_request(query: str, conversation_id: Optional[str] = None, parent_id: Optional[str] = None) -> Dict[str, Any]:
    """发送聊天请求"""
    url = f"https://aistudio.xiaomimimo.com/open-apis/bot/chat?xiaomichatbot_ph={requests.utils.quote(CONFIG['xiaomichatbot_ph'])}"
    headers = build_headers()
    payload = build_payload(query, conversation_id, parent_id)
    
    print('\n' + '='*60)
    print('🚀 发送请求到 MiMo API')
    print('='*60)
    print(f'URL: {url}')
    print('\n📋 请求头:')
    print(json.dumps(headers, indent=2, ensure_ascii=False))
    print('\n📦 请求体:')
    print(json.dumps(payload, indent=2, ensure_ascii=False))
    print('\n' + '='*60)
    
    try:
        response = requests.post(
            url,
            headers=headers,
            json=payload,
            timeout=60,
            stream=True
        )
        
        print(f'\n✅ 请求成功！')
        print(f'状态码: {response.status_code}')
        print(f'响应头: {json.dumps(dict(response.headers), indent=2, ensure_ascii=False)}')
        print('\n' + '='*60)
        print('📥 响应内容（流式）:')
        print('='*60)
        
        full_response = ''
        last_msg_id = '0'
        returned_conv_id = payload['conversationId']
        
        for line in response.iter_lines():
            if not line:
                continue
            
            line_str = line.decode('utf-8')
            
            if line_str.startswith('id:'):
                last_msg_id = line_str.replace('id:', '').strip()
            
            if line_str.startswith('data:'):
                try:
                    data_str = line_str.replace('data:', '').strip()
                    if data_str == '[DONE]':
                        print('\n[完成]')
                        continue
                    
                    data = json.loads(data_str)
                    
                    # 提取对话ID
                    if 'conversationId' in data:
                        returned_conv_id = data['conversationId']
                    
                    # 提取消息ID
                    if 'id' in data:
                        last_msg_id = data['id']
                    
                    # 提取回复内容
                    if 'text' in data:
                        print(data['text'], end='', flush=True)
                        full_response += data['text']
                    
                    # 提取思考内容（如果有）
                    if 'thinking' in data:
                        print('\n[思考中...]')
                        print(data['thinking'], end='', flush=True)
                
                except json.JSONDecodeError:
                    pass
        
        print('\n\n' + '='*60)
        print('🎉 响应完成！')
        print('='*60)
        print(f'会话ID: {returned_conv_id}')
        print(f'消息ID: {last_msg_id}')
        print(f'完整回复长度: {len(full_response)} 字符')
        
        return {
            'conversationId': returned_conv_id,
            'msgId': last_msg_id,
            'response': full_response
        }
    
    except requests.exceptions.RequestException as error:
        print(f'\n❌ 请求失败！')
        print(f'错误信息: {error}')
        
        if hasattr(error, 'response') and error.response is not None:
            print(f'状态码: {error.response.status_code}')
            print(f'响应数据: {error.response.text}')
        
        raise error

def validate_config() -> bool:
    """验证配置"""
    print('🔍 验证配置...')
    
    if (CONFIG['userId'] == 'YOUR_USER_ID_HERE' or
        CONFIG['xiaomichatbot_ph'] == 'YOUR_PH_HERE' or
        CONFIG['serviceToken'] == 'YOUR_SERVICE_TOKEN_HERE'):
        print('\n❌ 错误：请先在CONFIG中填写从浏览器获取的信息！')
        print('\n请参考 MiMo-测试指南.md 获取以下信息：')
        print('  - userId')
        print('  - xiaomichatbot_ph')
        print('  - serviceToken')
        return False
    
    print('✅ 配置验证通过')
    print(f"  - userId: {CONFIG['userId']}")
    print(f"  - xiaomichatbot_ph: {CONFIG['xiaomichatbot_ph'][:50]}...")
    print(f"  - serviceToken: {CONFIG['serviceToken']}")
    print(f"  - model: {CONFIG['model']}")
    
    return True

# ==================== 主函数 ====================

def main():
    print('\n' + '='*60)
    print('MiMo API 测试')
    print('='*60)
    
    # 验证配置
    if not validate_config():
        return
    
    try:
        # 测试1：简单对话
        print('\n📝 测试1：简单对话')
        result1 = send_chat_request('你好，请用一句话介绍你自己')
        
        # 测试2：多轮对话（使用相同的会话ID）
        print('\n\n📝 测试2：多轮对话')
        result2 = send_chat_request(
            '刚才我说了什么？',
            result1['conversationId'],
            result1['msgId']
        )
        
        print('\n\n' + '='*60)
        print('✨ 所有测试完成！')
        print('='*60)
    
    except Exception as error:
        print(f'\n\n💥 测试失败: {error}')
        print('\n可能的原因：')
        print('  1. Cookie已过期，请重新获取')
        print('  2. 网络连接问题')
        print('  3. 请求参数错误')

if __name__ == '__main__':
    main()
