# NCNN Real-ESRGAN JNI Source

This directory contains the reproducible source for `libzen_ncnn.so`. It links
Tencent NCNN with Vulkan GPU acceleration to execute Real-ESRGAN image super-resolution.

## Build Instructions

On a Linux machine or GitHub Actions runner with Android NDK installed:

```bash
cd native/ncnn-jni
bash build-arm64-v8a.sh
```

The script compiles `libzen_ncnn.so` and copies it to `app/src/main/jniLibs/arm64-v8a/libzen_ncnn.so`.
