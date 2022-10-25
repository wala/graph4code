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
