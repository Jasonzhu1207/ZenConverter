#include <jni.h>
#include <android/bitmap.h>
#include <android/log.h>
#include <algorithm>
#include <string>
#include <vector>
#include <cmath>

#include "net.h"
#include "gpu.h"
#include "cpu.h"

#define TAG "ZenNcnnJNI"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

static int g_gpu_count = 0;
static bool g_gpu_instance_initialized = false;

extern "C" {

JNIEXPORT jint JNICALL
Java_org_zenconverter_app_model_NcnnNative_nativeInit(JNIEnv* env, jclass clazz) {
    if (!g_gpu_instance_initialized) {
        ncnn::create_gpu_instance();
        g_gpu_instance_initialized = true;
        g_gpu_count = ncnn::get_gpu_count();
        LOGI("NCNN GPU instance created. Available GPU count: %d", g_gpu_count);
    }
    return g_gpu_count;
}

JNIEXPORT void JNICALL
Java_org_zenconverter_app_model_NcnnNative_nativeDestroy(JNIEnv* env, jclass clazz) {
    if (g_gpu_instance_initialized) {
        ncnn::destroy_gpu_instance();
        g_gpu_instance_initialized = false;
        g_gpu_count = 0;
        LOGI("NCNN GPU instance destroyed.");
    }
}

JNIEXPORT jint JNICALL
Java_org_zenconverter_app_model_NcnnNative_nativeGetGpuCount(JNIEnv* env, jclass clazz) {
    if (!g_gpu_instance_initialized) {
        ncnn::create_gpu_instance();
        g_gpu_instance_initialized = true;
        g_gpu_count = ncnn::get_gpu_count();
    }
    return g_gpu_count;
}

JNIEXPORT jstring JNICALL
Java_org_zenconverter_app_model_NcnnNative_nativeGetGpuDeviceName(JNIEnv* env, jclass clazz, jint deviceIndex) {
    if (!g_gpu_instance_initialized) {
        ncnn::create_gpu_instance();
        g_gpu_instance_initialized = true;
        g_gpu_count = ncnn::get_gpu_count();
    }
    if (deviceIndex < 0 || deviceIndex >= g_gpu_count) {
        return env->NewStringUTF("CPU");
    }
    const ncnn::GpuInfo& info = ncnn::get_gpu_info(deviceIndex);
    return env->NewStringUTF(info.device_name());
}

JNIEXPORT jint JNICALL
Java_org_zenconverter_app_model_NcnnNative_nativeRealEsrganUpscale(
    JNIEnv* env,
    jclass clazz,
    jobject srcBitmap,
    jobject dstBitmap,
    jstring paramPath,
    jstring binPath,
    jint scale,
    jint tileSize,
    jint tilePad,
    jint gpuIndex,
    jobject progressCallback,
    jobject cancelCheck
) {
    if (srcBitmap == nullptr || dstBitmap == nullptr || paramPath == nullptr || binPath == nullptr) {
        LOGE("Invalid arguments passed to nativeRealEsrganUpscale");
        return -1;
    }

    AndroidBitmapInfo srcInfo;
    if (AndroidBitmap_getInfo(env, srcBitmap, &srcInfo) < 0 || srcInfo.format != ANDROID_BITMAP_FORMAT_RGBA_8888) {
        LOGE("Failed to get srcBitmap info or format is not RGBA_8888");
        return -1;
    }

    AndroidBitmapInfo dstInfo;
    if (AndroidBitmap_getInfo(env, dstBitmap, &dstInfo) < 0 || dstInfo.format != ANDROID_BITMAP_FORMAT_RGBA_8888) {
        LOGE("Failed to get dstBitmap info or format is not RGBA_8888");
        return -1;
    }

    int srcW = (int)srcInfo.width;
    int srcH = (int)srcInfo.height;
    int dstW = (int)dstInfo.width;
    int dstH = (int)dstInfo.height;

    if (dstW != srcW * scale || dstH != srcH * scale) {
        LOGE("Destination bitmap dimensions (%dx%d) do not match src dimensions (%dx%d) * scale (%d)",
             dstW, dstH, srcW, srcH, scale);
        return -1;
    }

    const char* paramPathStr = env->GetStringUTFChars(paramPath, nullptr);
    const char* binPathStr = env->GetStringUTFChars(binPath, nullptr);

    ncnn::Net net;
    bool useVulkan = (gpuIndex >= 0 && gpuIndex < g_gpu_count);
    net.opt.use_vulkan_compute = useVulkan;
    net.opt.use_fp16_packed = true;
    net.opt.use_fp16_storage = true;
    net.opt.use_fp16_arithmetic = true;
    net.opt.use_int8_storage = true;
    net.opt.use_int8_arithmetic = true;
    net.opt.num_threads = std::max(1, std::min(6, (int)ncnn::get_cpu_count()));

    if (useVulkan) {
        net.set_vulkan_device(gpuIndex);
        LOGI("Running Real-ESRGAN on Vulkan GPU index %d (%s)...",
             gpuIndex, ncnn::get_gpu_info(gpuIndex).device_name());
    } else {
        LOGI("Running Real-ESRGAN on multi-core CPU (%d threads)...", net.opt.num_threads);
    }

    int loadParamRet = net.load_param(paramPathStr);
    int loadModelRet = net.load_model(binPathStr);

    env->ReleaseStringUTFChars(paramPath, paramPathStr);
    env->ReleaseStringUTFChars(binPath, binPathStr);

    if (loadParamRet != 0 || loadModelRet != 0) {
        LOGE("Failed to load Real-ESRGAN model: param=%d, bin=%d", loadParamRet, loadModelRet);
        return -3;
    }

    void* srcPixels = nullptr;
    if (AndroidBitmap_lockPixels(env, srcBitmap, &srcPixels) < 0 || srcPixels == nullptr) {
        LOGE("Failed to lock source bitmap pixels");
        return -1;
    }

    void* dstPixels = nullptr;
    if (AndroidBitmap_lockPixels(env, dstBitmap, &dstPixels) < 0 || dstPixels == nullptr) {
        AndroidBitmap_unlockPixels(env, srcBitmap);
        LOGE("Failed to lock destination bitmap pixels");
        return -1;
    }

    int effectiveTileSize = tileSize > 0 ? tileSize : 200;
    int effectiveTilePad = tilePad >= 0 ? tilePad : 10;

    int tileCols = (srcW + effectiveTileSize - 1) / effectiveTileSize;
    int tileRows = (srcH + effectiveTileSize - 1) / effectiveTileSize;
    int totalTiles = tileCols * tileRows;
    int completedTiles = 0;

    jclass cancelClass = nullptr;
    jmethodID midIsCancelled = nullptr;
    if (cancelCheck != nullptr) {
        cancelClass = env->GetObjectClass(cancelCheck);
        if (cancelClass != nullptr) {
            midIsCancelled = env->GetMethodID(cancelClass, "isCancelled", "()Z");
            if (midIsCancelled == nullptr && env->ExceptionCheck()) {
                env->ExceptionClear();
            }
        }
    }

    jclass progressClass = nullptr;
    jmethodID midOnProgress = nullptr;
    if (progressCallback != nullptr) {
        progressClass = env->GetObjectClass(progressCallback);
        if (progressClass != nullptr) {
            midOnProgress = env->GetMethodID(progressClass, "onProgress", "(F)V");
            if (midOnProgress == nullptr && env->ExceptionCheck()) {
                env->ExceptionClear();
            }
        }
    }

    int resultCode = 0;
    const float inv255 = 1.0f / 255.0f;
    uint32_t srcStride = srcInfo.stride;
    uint32_t dstStride = dstInfo.stride;

    for (int row = 0; row < tileRows; row++) {
        for (int col = 0; col < tileCols; col++) {
            if (cancelCheck != nullptr && midIsCancelled != nullptr) {
                jboolean cancelled = env->CallBooleanMethod(cancelCheck, midIsCancelled);
                if (env->ExceptionCheck()) {
                    env->ExceptionClear();
                    cancelled = JNI_TRUE;
                }
                if (cancelled) {
                    LOGI("Real-ESRGAN inference cancelled by user");
                    resultCode = -2;
                    goto cleanup;
                }
            }

            int tx = col * effectiveTileSize;
            int ty = row * effectiveTileSize;
            int tw = std::min(effectiveTileSize, srcW - tx);
            int th = std::min(effectiveTileSize, srcH - ty);

            int x0 = std::max(0, tx - effectiveTilePad);
            int y0 = std::max(0, ty - effectiveTilePad);
            int x1 = std::min(srcW, tx + tw + effectiveTilePad);
            int y1 = std::min(srcH, ty + th + effectiveTilePad);

            int cropW = x1 - x0;
            int cropH = y1 - y0;

            ncnn::Mat inTile(cropW, cropH, 3);
            float* ptrR = inTile.channel(0);
            float* ptrG = inTile.channel(1);
            float* ptrB = inTile.channel(2);

            for (int y = 0; y < cropH; y++) {
                const uint8_t* srcRow = (const uint8_t*)srcPixels + (y0 + y) * srcStride + x0 * 4;
                int rowOffset = y * cropW;
                for (int x = 0; x < cropW; x++) {
                    uint8_t r = srcRow[x * 4 + 0];
                    uint8_t g = srcRow[x * 4 + 1];
                    uint8_t b = srcRow[x * 4 + 2];
                    uint8_t a = srcRow[x * 4 + 3];

                    if (a == 255) {
                        ptrR[rowOffset + x] = (float)r * inv255;
                        ptrG[rowOffset + x] = (float)g * inv255;
                        ptrB[rowOffset + x] = (float)b * inv255;
                    } else {
                        float fa = (float)a * inv255;
                        float oneMinusA = 1.0f - fa;
                        ptrR[rowOffset + x] = ((float)r * inv255) * fa + oneMinusA;
                        ptrG[rowOffset + x] = ((float)g * inv255) * fa + oneMinusA;
                        ptrB[rowOffset + x] = ((float)b * inv255) * fa + oneMinusA;
                    }
                }
            }

            ncnn::Extractor ex = net.create_extractor();
            ex.input("data", inTile);
            ncnn::Mat outTile;
            int extractRet = ex.extract("output", outTile);

            if (extractRet != 0 || outTile.empty()) {
                LOGE("Failed during ncnn extract for tile (%d, %d), ret=%d", col, row, extractRet);
                resultCode = -4;
                goto cleanup;
            }

            int coreW = tw * scale;
            int coreH = th * scale;
            int srcOffsetX = (tx - x0) * scale;
            int srcOffsetY = (ty - y0) * scale;
            int dstStartX = tx * scale;
            int dstStartY = ty * scale;

            for (int dy = 0; dy < coreH; dy++) {
                int srcY = srcOffsetY + dy;
                const float* outR = outTile.channel(0).row(srcY) + srcOffsetX;
                const float* outG = outTile.channel(1).row(srcY) + srcOffsetX;
                const float* outB = outTile.channel(2).row(srcY) + srcOffsetX;

                uint8_t* dstRow = (uint8_t*)dstPixels + (dstStartY + dy) * dstStride + dstStartX * 4;

                for (int dx = 0; dx < coreW; dx++) {
                    float r = outR[dx] * 255.0f + 0.5f;
                    float g = outG[dx] * 255.0f + 0.5f;
                    float b = outB[dx] * 255.0f + 0.5f;

                    dstRow[dx * 4 + 0] = (uint8_t)std::min(std::max(r, 0.0f), 255.0f);
                    dstRow[dx * 4 + 1] = (uint8_t)std::min(std::max(g, 0.0f), 255.0f);
                    dstRow[dx * 4 + 2] = (uint8_t)std::min(std::max(b, 0.0f), 255.0f);
                    dstRow[dx * 4 + 3] = 255;
                }
            }

            completedTiles++;
            if (progressCallback != nullptr && midOnProgress != nullptr) {
                float progressVal = (float)completedTiles / (float)totalTiles;
                env->CallVoidMethod(progressCallback, midOnProgress, progressVal);
                if (env->ExceptionCheck()) {
                    env->ExceptionClear();
                }
            }
        }
    }

cleanup:
    AndroidBitmap_unlockPixels(env, dstBitmap);
    AndroidBitmap_unlockPixels(env, srcBitmap);

    return resultCode;
}

} // extern "C"
