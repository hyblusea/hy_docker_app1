import pymysql
conn = pymysql.connect(host='localhost', user='root', password='tradingx', database='tradingx')
cur = conn.cursor()
cur.execute("DESCRIBE kline_daily")
columns = cur.fetchall()
print("kline_daily 表结构:")
for col in columns:
    print(col)
print("\n前5条数据:")
cur.execute("SELECT ts_code, trade_date, close FROM kline_daily LIMIT 5")
rows = cur.fetchall()
for r in rows:
    print(r)
conn.close()
