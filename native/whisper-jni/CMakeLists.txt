cmake_minimum_required(VERSION 3.22.1)
project("zenconverter_whisper" C CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# WHISPER_CPP_DIR must be passed in via -DWHISPER_CPP_DIR=<path>
# It points at a checkout of https://github.com/ggml-org/whisper.cpp
if(NOT DEFINED WHISPER_CPP_DIR OR NOT EXISTS "${WHISPER_CPP_DIR}/include/whisper.h")
  message(FATAL_ERROR "WHISPER_CPP_DIR is not set or invalid: '${WHISPER_CPP_DIR}'")
endif()

message(STATUS "Using whisper.cpp from: ${WHISPER_CPP_DIR}")

set(WHISPER_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(WHISPER_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(WHISPER_BUILD_SERVER OFF CACHE BOOL "" FORCE)
# Static ggml is linked into our own shared object; there is no need to ship a
# second .so, which keeps the APK to a single Whisper binary.
set(BUILD_SHARED_LIBS OFF CACHE BOOL "" FORCE)

add_subdirectory("${WHISPER_CPP_DIR}" "${CMAKE_CURRENT_BINARY_DIR}/whisper.cpp" EXCLUDE_FROM_ALL)

add_library(zen_whisper SHARED
    zen_whisper_jni.cpp
)

target_include_directories(zen_whisper PRIVATE
    "${WHISPER_CPP_DIR}/include"
    "${WHISPER_CPP_DIR}/ggml/include"
)

find_library(log-lib log)

target_link_libraries(zen_whisper
    PRIVATE
    whisper
    ${log-lib}
)

# Android 15+ requires 16 KB page-aligned ELF LOAD segments
target_link_options(zen_whisper PRIVATE "LINKER:-z,max-page-size=16384")
