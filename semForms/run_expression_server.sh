#! /bin/bash
docker run --name g4c --net elastic -e ELASTIC_PASSWORD=$ELASTIC_PASSWORD -e ELASTIC_HOST=es01 -p 4567:4567 java-graph4code > /tmp/expression_server.log 2>&1 &
