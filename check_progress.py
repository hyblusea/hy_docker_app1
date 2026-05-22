import json, subprocess
r = subprocess.run(['curl.exe', '-s', '-c', 'cookies.txt', '-X', 'POST', 'http://localhost:8080/api/auth/login', '-H', 'Content-Type: application/json', '-d', '{"username":"root","password":"SiU0blE3eSFiVjkkd1IzZA=="}'], capture_output=True, text=True)
r2 = subprocess.run(['curl.exe', '-s', '-b', 'cookies.txt', 'http://localhost:8080/api/training/tasks/28'], capture_output=True, text=True)
d = json.loads(r2.stdout)['data']
print(f"Status: {d['status']}")
print(f"Error: {d['errorMessage']}")
print(f"Progress: {d['progressPct']}%")
print(f"Current Epoch: {d['currentEpoch']}/{d['epochs']}")
print(f"Train Loss: {d['trainLoss']}")
print(f"Val Loss: {d['validLoss']}")
