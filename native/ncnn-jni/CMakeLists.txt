cmake_minimum_required(VERSION 3.22.1)
project("zenconverter_ncnn" C CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# NCNN_ABI_DIR must be passed in via -DNCNN_ABI_DIR=<path>
# It points to the arm64-v8a directory from the NCNN Android Vulkan prebuilt zip,
# e.g. ncnn-sdk-extract/ncnn-20240410-android-vulkan/arm64-v8a
if(NOT DEFINED NCNN_ABI_DIR OR NOT EXISTS "${NCNN_ABI_DIR}/include/ncnn")
  message(FATAL_ERROR "NCNN_ABI_DIR is not set or invalid: '${NCNN_ABI_DIR}'")
endif()

message(STATUS "Using NCNN SDK from: ${NCNN_ABI_DIR}")

# Use ncnn's own cmake config so all transitive deps (glslang, SPIRV, etc.) are pulled in automatically
set(ncnn_DIR "${NCNN_ABI_DIR}/lib/cmake/ncnn")
find_package(ncnn REQUIRED)

add_library(zen_ncnn SHARED
    zen_ncnn_jni.cpp
)

target_include_directories(zen_ncnn PRIVATE
    "${NCNN_ABI_DIR}/include"
    "${NCNN_ABI_DIR}/include/ncnn"
)

find_library(jnigraphics-lib jnigraphics)
find_library(log-lib log)
find_library(android-lib android)

target_link_libraries(zen_ncnn
    PRIVATE
    ncnn
    ${jnigraphics-lib}
    ${log-lib}
    ${android-lib}
)

# Android 15+ requires 16 KB page-aligned ELF LOAD segments
target_link_options(zen_ncnn PRIVATE "LINKER:-z,max-page-size=16384")
