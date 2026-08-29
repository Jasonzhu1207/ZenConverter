cmake_minimum_required(VERSION 3.22.1)
project("zenconverter_ncnn" C CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# NCNN_ABI_DIR must be passed in via -DNCNN_ABI_DIR=<path>
# It points to the arm64-v8a directory from the NCNN Android Vulkan prebuilt zip,
# e.g. ncnn-sdk-extract/ncnn-20240410-android-vulkan/arm64-v8a
if(NOT DEFINED NCNN_ABI_DIR OR NOT EXISTS "${NCNN_ABI_DIR}/include/ncnn")
  message(FATAL_ERROR "NCNN_ABI_DIR is not set or invalid: '${NCNN_ABI_DIR}'. Pass -DNCNN_ABI_DIR=<path_to_arm64-v8a>")
endif()

message(STATUS "Using NCNN SDK from: ${NCNN_ABI_DIR}")

# Import ncnn as a prebuilt static library
add_library(ncnn STATIC IMPORTED)
set_target_properties(ncnn PROPERTIES
    IMPORTED_LOCATION "${NCNN_ABI_DIR}/lib/libncnn.a"
    INTERFACE_INCLUDE_DIRECTORIES "${NCNN_ABI_DIR}/include"
)

add_library(zen_ncnn SHARED
    zen_ncnn_jni.cpp
)

target_include_directories(zen_ncnn PRIVATE
    "${NCNN_ABI_DIR}/include"
    "${NCNN_ABI_DIR}/include/ncnn"
)

find_library(vulkan-lib vulkan)
find_library(jnigraphics-lib jnigraphics)
find_library(log-lib log)
find_library(android-lib android)

target_link_libraries(zen_ncnn
    PRIVATE
    ncnn
    ${vulkan-lib}
    ${jnigraphics-lib}
    ${log-lib}
    ${android-lib}
)
