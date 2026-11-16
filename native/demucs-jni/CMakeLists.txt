cmake_minimum_required(VERSION 3.22.1)
project("zenconverter_demucs" CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_POSITION_INDEPENDENT_CODE ON)

# DEMUCS_CPP_DIR must point at a checkout of https://github.com/sevagh/demucs.cpp
# with its submodules (vendor/eigen) present.
if(NOT DEFINED DEMUCS_CPP_DIR OR NOT EXISTS "${DEMUCS_CPP_DIR}/src/model.hpp")
  message(FATAL_ERROR "DEMUCS_CPP_DIR is not set or invalid: '${DEMUCS_CPP_DIR}'")
endif()
if(NOT EXISTS "${DEMUCS_CPP_DIR}/vendor/eigen/Eigen/Dense")
  message(FATAL_ERROR "demucs.cpp submodules are missing (vendor/eigen). Clone with --recurse-submodules.")
endif()

message(STATUS "Using demucs.cpp from: ${DEMUCS_CPP_DIR}")

# demucs.cpp's own release flags use -march=native, which is invalid when cross
# compiling for Android. Build the library with plain -O3 and let the toolchain
# pick the target architecture.
set(CMAKE_CXX_FLAGS_RELEASE "-O3 -DNDEBUG -fno-unsafe-math-optimizations -freciprocal-math -fno-signed-zeros")
set(CMAKE_C_FLAGS_RELEASE "-O3 -DNDEBUG")

file(GLOB DEMUCS_SOURCES "${DEMUCS_CPP_DIR}/src/*.cpp")

add_library(zen_demucs SHARED
    zen_demucs_jni.cpp
    ${DEMUCS_SOURCES}
)

target_include_directories(zen_demucs PRIVATE
    "${DEMUCS_CPP_DIR}/src"
    "${DEMUCS_CPP_DIR}/vendor/eigen"
)

find_package(OpenMP REQUIRED)

find_library(log-lib log)

target_link_libraries(zen_demucs
    PRIVATE
    OpenMP::OpenMP_CXX
    ${log-lib}
)

# Android 15+ requires 16 KB page-aligned ELF LOAD segments
target_link_options(zen_demucs PRIVATE "LINKER:-z,max-page-size=16384")
