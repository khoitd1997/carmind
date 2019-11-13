#!/bin/bash

set -e
curr_dir="$( cd "$( dirname "${BASH_SOURCE[0]}" )" >/dev/null 2>&1 && pwd )"

cd ${curr_dir}

nrfjprog -f nrf52 --program s132_nrf52_7.0.1_softdevice.hex --sectorerase
nrfjprog -f nrf52 --reset