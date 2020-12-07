#!/bin/bash

ulimit -t $1
shift
"$@"

