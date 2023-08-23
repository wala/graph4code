#! /bin/bash
docker run --name g4c -p 4567:4567 java-graph4code > /tmp/data_rinse_server.log 2>&1 &
