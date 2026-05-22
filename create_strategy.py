import urllib.request
import json
import base64

base_url = 'http://localhost:8080'

password = 'J%4nQ7y!bV9$wR3d'
encoded_password = base64.b64encode(password.encode()).decode()

login_data = json.dumps({'username': 'root', 'password': encoded_password}).encode()
req = urllib.request.Request(base_url + '/api/auth/login', data=login_data, headers={'Content-Type': 'application/json'})
resp = urllib.request.urlopen(req)
login_result = json.loads(resp.read().decode())
print('Login:', login_result['msg'])
cookie = resp.headers.get('Set-Cookie')

# Delete the failed strategy (id=65)
try:
    req_del = urllib.request.Request(base_url + '/api/strategy/65', headers={
        'Cookie': cookie
    }, method='DELETE')
    resp_del = urllib.request.urlopen(req_del)
    print('Delete id=65:', json.loads(resp_del.read().decode())['msg'])
except Exception as e:
    print('Delete id=65 failed:', e)

strategy_code = r'''import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseStrategy;
import org.ta4j.core.Strategy;
import org.ta4j.core.indicators.averages.SMAIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.rules.CrossedUpIndicatorRule;
import org.ta4j.core.rules.CrossedDownIndicatorRule;

public class SimpleMACrossStrategy {
    public static Strategy buildStrategy(BarSeries series) {
        ClosePriceIndicator close = new ClosePriceIndicator(series);
        SMAIndicator sma5 = new SMAIndicator(close, 5);
        SMAIndicator sma20 = new SMAIndicator(close, 20);
        var entryRule = new CrossedUpIndicatorRule(sma5, sma20);
        var exitRule = new CrossedDownIndicatorRule(sma5, sma20);
        return new BaseStrategy(entryRule, exitRule);
    }
}'''

strategy_data = json.dumps({
    'name': 'MA5上穿MA20策略',
    'language': 'java',
    'code': strategy_code,
    'description': 'MA5均线上穿MA20均线时买入，MA5下穿MA20时卖出。简单均线交叉策略，适合趋势行情。'
}).encode()

req2 = urllib.request.Request(base_url + '/api/strategy', data=strategy_data, headers={
    'Content-Type': 'application/json',
    'Cookie': cookie
})
resp2 = urllib.request.urlopen(req2)
create_result = json.loads(resp2.read().decode())
print('Create result:', json.dumps(create_result, indent=2, ensure_ascii=False))
