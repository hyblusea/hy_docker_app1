"""
DeepSeek Web API 测试脚本 (Python版本)
模仿 Chat2API 的调用方式
"""

import requests
import json

# 配置 - 需要替换为你自己的Token
CONFIG = {
    'token': 'DIVkqH2N1ytv2BTcFUZfAa4HVOegzrY1tZ8pp0+Eg34V1kStYcQKSkpza9+Az1/8',  # 从浏览器获取
    'api_endpoint': 'https://chat.deepseek.com',
    'chat_path': '/api/v0/chat/completion'
}

# 请求头配置 (模拟浏览器)
HEADERS = {
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
}


def check_token():
    """验证Token有效性"""
    print('\n🔍 验证Token有效性...')
    
    url = f"{CONFIG['api_endpoint']}/api/v0/users/current"
    headers = {**HEADERS, 'Authorization': f"Bearer {CONFIG['token']}"}
    
    try:
        response = requests.get(url, headers=headers)
        
        if response.status_code == 200:
            print('✅ Token有效')
            user_info = response.json()
            print(f"用户信息: {json.dumps(user_info, indent=2, ensure_ascii=False)}")
            return True
        else:
            print(f'❌ Token无效或已过期 (状态码: {response.status_code})')
            print(f'响应: {response.text}')
            return False
    except Exception as e:
        print(f'❌ 验证失败: {e}')
        return False


def send_chat_request(message, model='deepseek-chat', stream=False):
    """
    发送聊天请求
    
    Args:
        message: 用户消息
        model: 模型名称 (deepseek-chat, deepseek-reasoner)
        stream: 是否使用流式响应
    """
    url = f"{CONFIG['api_endpoint']}{CONFIG['chat_path']}"
    headers = {**HEADERS, 'Authorization': f"Bearer {CONFIG['token']}"}
    
    # 构建请求体
    payload = {
        'model': model,
        'messages': [
            {
                'role': 'user',
                'content': message
            }
        ],
        'stream': stream,
        'temperature': 0.7,
        'max_tokens': 2048
    }
    
    print(f'\n🚀 发送请求到 DeepSeek API...')
    print(f'URL: {url}')
    print(f'请求体: {json.dumps(payload, indent=2, ensure_ascii=False)}')
    
    try:
        response = requests.post(url, headers=headers, json=payload, stream=stream)
        
        print(f'\n📥 响应状态码: {response.status_code}')
        
        if response.status_code == 200:
            if stream:
                # 处理流式响应
                print('\n✅ 流式响应:')
                for line in response.iter_lines():
                    if line:
                        line_str = line.decode('utf-8')
                        if line_str.startswith('data: '):
                            data_str = line_str[6:]  # 去掉 'data: ' 前缀
                            if data_str.strip() == '[DONE]':
                                break
                            try:
                                data = json.loads(data_str)
                                if 'choices' in data and len(data['choices']) > 0:
                                    delta = data['choices'][0].get('delta', {})
                                    content = delta.get('content', '')
                                    if content:
                                        print(content, end='', flush=True)
                            except json.JSONDecodeError:
                                pass
                print('\n')
            else:
                # 处理非流式响应
                result = response.json()
                print('\n✅ 响应成功!')
                print(f'响应内容: {json.dumps(result, indent=2, ensure_ascii=False)}')
                
                # 提取回复内容
                if 'choices' in result and len(result['choices']) > 0:
                    content = result['choices'][0]['message']['content']
                    print(f'\n💬 AI回复: {content}')
                
                return result
        else:
            print(f'\n❌ 请求失败')
            print(f'错误信息: {response.text}')
            return None
            
    except Exception as e:
        print(f'\n❌ 请求错误: {e}')
        return None


def main():
    print('=' * 60)
    print('DeepSeek Web API 测试')
    print('=' * 60)
    
    # 检查Token配置
    if CONFIG['token'] == 'YOUR_DEEPSEEK_USER_TOKEN_HERE':
        print('\n⚠️  请先配置Token!')
        print('\n获取Token步骤:')
        print('1. 访问 https://chat.deepseek.com/')
        print('2. 登录并开始任意对话')
        print('3. 按 F12 打开开发者工具')
        print('4. Application → Local Storage → chat.deepseek.com')
        print('5. 找到 userToken 并复制值')
        print('6. 粘贴到本脚本的 CONFIG["token"] 中')
        return
    
    # 验证Token
    if not check_token():
        print('\n请获取新的Token后重试')
        return
    
    # 测试非流式请求
    print('\n' + '=' * 60)
    print('测试1: 非流式请求')
    print('=' * 60)
    send_chat_request('你好，请用一句话介绍你自己', stream=False)
    
    # 测试流式请求
    print('\n' + '=' * 60)
    print('测试2: 流式请求')
    print('=' * 60)
    send_chat_request('请写一首关于春天的短诗', stream=True)
    
    print('\n' + '=' * 60)
    print('🎉 测试完成!')
    print('=' * 60)


if __name__ == '__main__':
    main()
