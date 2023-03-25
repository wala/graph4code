#! /bin/bash
docker rm -f github
docker build --no-cache --tag gh_server .
