from elasticsearch import Elasticsearch
import os

# Create the client instance
client = Elasticsearch(
    "https://localhost:9200",
    ca_certs="./http_ca.crt",
    basic_auth=("elastic", os.environ['ELASTIC_PASSWORD'])
)

# Successful response!
print(client.info())
print(client.indices.get_alias().keys())

match_all = {
    "query": {
        "match_all": {}
    }
}

print(client.search(index="expressions", body=match_all))
