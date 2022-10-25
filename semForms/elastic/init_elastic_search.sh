#! /bin/bash

docker pull docker.elastic.co/elasticsearch/elasticsearch:8.4.3

# this step is needed - elastic search will not start otherwise
sudo sysctl -w vm.max_map_count=262144

# this step installs wait-for-it
sudo apt-get install wait-for-it

docker network create elastic

docker run --name es01 --net elastic -e ELASTIC_PASSWORD=$ELASTIC_PASSWORD -p 9200:9200 -p 9300:9300 docker.elastic.co/elasticsearch/elasticsearch:8.4.3 > /tmp/elastic_log 2>&1 &

wait-for-it localhost:9200 --timeout=10

sleep 10

docker cp es01:/usr/share/elasticsearch/config/certs/http_ca.crt .


