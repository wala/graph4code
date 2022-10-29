import requests, json


rest_url = 'http://127.0.0.1:8001/api/github_search'
payload = {
    "query": "houses.csv",
    }
ret = requests.get(rest_url, params=payload)
data = json.loads(ret.content)
for res in data:
    print(res['Repo Name'])
    print(res['File name'])
    print(res['URL'])
    print('*'*20)