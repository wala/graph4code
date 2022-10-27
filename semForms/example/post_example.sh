#! /bin/bash

# uncomment if you want to see the results of just the analysis as a JSON file
#curl -X POST -H "Content-Type: application/json" -d @test.json localhost:4567/analyze_code

# post test.json to index all the expressions in it.
curl -X POST -H "Content-Type: application/json" -d @test.json localhost:4567/index
