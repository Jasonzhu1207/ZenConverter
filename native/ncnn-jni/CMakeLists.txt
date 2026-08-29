cmake_minimum_required(VERSION 3.22.1)
project("zenconverter_ncnn" C CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# If NCNN_SDK_DIR is specified, look for ncnn there; otherwise fallback to local include and jniLibs
if(DEFINED NCNN_SDK_DIR AND EXISTS "${NCNN_SDK_DIR}")
    set(ncnn_DIR "${NCNN_SDK_DIR}/lib/cmake/ncnn")
    find_package(ncnn REQUIRED)
else()
    include_directories(${CMAKE_CURRENT_SOURCE_DIR}/include/ncnn)
    add_library(ncnn SHARED IMPORTED)
    set_target_properties(ncnn PROPERTIES
        IMPORTED_LOCATION "${CMAKE_CURRENT_SOURCE_DIR}/../../app/src/main/jniLibs/arm64-v8a/libncnn.so"
    )
endif()

add_library(zen_ncnn SHARED
    zen_ncnn_jni.cpp
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
