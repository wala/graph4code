#! /bin/bash
docker rm -f g4c
docker build --no-cache --tag java-graph4code .
