#!/bin/bash
set -e
curr_dir="$( cd "$( dirname "${BASH_SOURCE[0]}" )" >/dev/null 2>&1 && pwd )"

# generate cmake files from sdk
cd ${curr_dir}/nrf_sdk
make -f sdk_wrapper.mk

cd ${curr_dir}
mkdir -p build 
cd build

# do build
cmake .. -DCMAKE_TOOLCHAIN_FILE=../nrf_sdk/toolchain.cmake && cmake --build .

# flash the firmware
arm-none-eabi-objcopy -O ihex carmind.elf carmind.hex
nrfjprog -f nrf52 --program carmind.hex --sectorerase --verify
nrfjprog -f nrf52 --reset