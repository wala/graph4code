#!/bin/bash

(
    /Users/dolby/anaconda3/envs/codebreaker/bin/python <<EOF
from $1 import $2
print(str($2))
EOF
) | awk -F\' '{ print $2; }'

