#!/usr/bin/env bash

conda activate codebreaker
export PYTHON_VERSION="3.7"
export OUTPUT_DIR='/data/mods.22'

python ../get_base_module $PYTHON_VERSION $OUTPUT_DIR