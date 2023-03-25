#! /bin/bash
docker run --name github --net elastic -e GH_TOKEN=$GH_TOKEN -p 8001:8001 gh_server > /tmp/gh_server.log 2>&1 &
