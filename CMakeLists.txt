cmake_minimum_required(VERSION 3.14)
project(carmind C CXX ASM)

# message("Flags are ${CMAKE_CXX_FLAGS}")

set(common_flags "-D__HEAP_SIZE=8192 -D__STACK_SIZE=8192")
set(CMAKE_ASM_FLAGS "${CMAKE_ASM_FLAGS} ${common_flags}")
set(CMAKE_C_FLAGS "${CMAKE_C_FLAGS} ${common_flags} -Og -Wall")
set(CMAKE_CXX_FLAGS
    "${CMAKE_CXX_FLAGS} ${common_flags} -std=c++17 -fno-rtti -Wno-comment -Og -fno-exceptions"
)
set(CMAKE_EXE_LINKER_FLAGS
    "${CMAKE_EXE_LINKER_FLAGS} -T${CMAKE_SOURCE_DIR}/gcc_nrf52.ld")
set(CMAKE_EXPORT_COMPILE_COMMANDS ON)
add_subdirectory(nrf_sdk)

include_directories(SYSTEM ${NRF_INCS})
include_directories(src)

# add_subdirectory(debug_utils)

add_executable(${PROJECT_NAME}.elf ${NRF_SRCS} main.c)
# target_link_libraries(${PROJECT_NAME}.elf PUBLIC signal_analyzer_common swo)
