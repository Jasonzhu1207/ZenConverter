#include <jni.h>
#include <android/bitmap.h>
#include <android/log.h>
#include <algorithm>
#include <string>
#include <vector>
#include <cmath>
#include <mutex>

#include "net.h"
#include "gpu.h"
#include "cpu.h"
#include "layer.h"

#define TAG "ZenNcnnJNI"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

static int g_gpu_count = 0;
static bool g_gpu_instance_initialized = false;
static ncnn::Net* g_rife_net = nullptr;
static std::mutex g_rife_mutex;

class Warp : public ncnn::Layer
{
public:
    Warp()
    {
        one_blob_only = false;
        support_inplace = false;
    }

    virtual int forward(const std::vector<ncnn::Mat>& bottom_blobs, std::vector<ncnn::Mat>& top_blobs, const ncnn::Option& opt) const
    {
        if (bottom_blobs.size() < 2) return -1;
        const ncnn::Mat& image = bottom_blobs[0];
        const ncnn::Mat& flow = bottom_blobs[1];

        int w = image.w;
        int h = image.h;
        int channels = image.c;

        ncnn::Mat& top_blob = top_blobs[0];
        top_blob.create(w, h, channels, sizeof(float), opt.blob_allocator);
        if (top_blob.empty())
            return -100;

        const float* flow_x_ptr = flow.channel(0);
        const float* flow_y_ptr = flow.channel(1);

        #pragma omp parallel for num_threads(opt.num_threads)
        for (int q = 0; q < channels; q++)
        {
            const float* image_ptr = image.channel(q);
            float* top_ptr = top_blob.channel(q);

            for (int y = 0; y < h; y++)
            {
                for (int x = 0; x < w; x++)
                {
                    int idx = y * w + x;
                    float fx = (float)x + flow_x_ptr[idx];
                    float fy = (float)y + flow_y_ptr[idx];

                    int x0 = (int)floorf(fx);
                    int y0 = (int)floorf(fy);
                    int x1 = x0 + 1;
                    int y1 = y0 + 1;

                    float wx1 = fx - (float)x0;
                    float wy1 = fy - (float)y0;
                    float wx0 = 1.0f - wx1;
                    float wy0 = 1.0f - wy1;

                    x0 = std::max(0, std::min(w - 1, x0));
                    x1 = std::max(0, std::min(w - 1, x1));
                    y0 = std::max(0, std::min(h - 1, y0));
                    y1 = std::max(0, std::min(h - 1, y1));

                    float v00 = image_ptr[y0 * w + x0];
                    float v01 = image_ptr[y0 * w + x1];
                    float v10 = image_ptr[y1 * w + x0];
                    float v11 = image_ptr[y1 * w + x1];

                    top_ptr[idx] = wy0 * (wx0 * v00 + wx1 * v01) + wy1 * (wx0 * v10 + wx1 * v11);
                }
            }
        }

        return 0;
    }
};

DEFINE_LAYER_CREATOR(Warp)

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

JNIEXPORT jint JNICALL
Java_org_zenconverter_app_model_NcnnNative_nativeRifeInit(
    JNIEnv* env,
    jclass clazz,
    jstring paramPath,
    jstring binPath,
    jint gpuIndex
) {
    std::lock_guard<std::mutex> lock(g_rife_mutex);
    if (g_rife_net != nullptr) {
        delete g_rife_net;
        g_rife_net = nullptr;
    }

    if (paramPath == nullptr || binPath == nullptr) {
        LOGE("Invalid model paths passed to nativeRifeInit");
        return -1;
    }

    const char* paramPathStr = env->GetStringUTFChars(paramPath, nullptr);
    const char* binPathStr = env->GetStringUTFChars(binPath, nullptr);

    g_rife_net = new ncnn::Net();
    g_rife_net->register_custom_layer("rife.Warp", Warp_layer_creator);
    g_rife_net->register_custom_layer("Warp", Warp_layer_creator);

    bool useVulkan = (gpuIndex >= 0 && gpuIndex < g_gpu_count);
    g_rife_net->opt.use_vulkan_compute = useVulkan;
    g_rife_net->opt.use_fp16_packed = useVulkan;
    g_rife_net->opt.use_fp16_storage = useVulkan;
    g_rife_net->opt.use_fp16_arithmetic = false;
    g_rife_net->opt.use_int8_storage = true;
    g_rife_net->opt.num_threads = std::max(1, std::min(6, (int)ncnn::get_cpu_count()));

    if (useVulkan) {
        g_rife_net->set_vulkan_device(gpuIndex);
    }

    int loadParamRet = g_rife_net->load_param(paramPathStr);
    int loadModelRet = g_rife_net->load_model(binPathStr);

    env->ReleaseStringUTFChars(paramPath, paramPathStr);
    env->ReleaseStringUTFChars(binPath, binPathStr);

    if (loadParamRet != 0 || loadModelRet != 0) {
        LOGE("Failed to load RIFE model in nativeRifeInit: param=%d, bin=%d", loadParamRet, loadModelRet);
        delete g_rife_net;
        g_rife_net = nullptr;
        return -3;
    }

    LOGI("RIFE model initialized successfully (useVulkan=%d, threads=%d)", (int)useVulkan, g_rife_net->opt.num_threads);
    return 0;
}

JNIEXPORT void JNICALL
Java_org_zenconverter_app_model_NcnnNative_nativeRifeDestroy(JNIEnv* env, jclass clazz) {
    std::lock_guard<std::mutex> lock(g_rife_mutex);
    if (g_rife_net != nullptr) {
        delete g_rife_net;
        g_rife_net = nullptr;
        LOGI("RIFE model destroyed.");
    }
}

JNIEXPORT jint JNICALL
Java_org_zenconverter_app_model_NcnnNative_nativeRifeInterpolate(
    JNIEnv* env,
    jclass clazz,
    jobject srcBitmap0,
    jobject srcBitmap1,
    jobject dstBitmap,
    jstring paramPath,
    jstring binPath,
    jint gpuIndex,
    jobject cancelCheck
) {
    std::lock_guard<std::mutex> lock(g_rife_mutex);
    if (g_rife_net == nullptr) {
        if (paramPath == nullptr || binPath == nullptr) {
            LOGE("RIFE model not initialized");
            return -1;
        }
        const char* paramPathStr = env->GetStringUTFChars(paramPath, nullptr);
        const char* binPathStr = env->GetStringUTFChars(binPath, nullptr);

        g_rife_net = new ncnn::Net();
        g_rife_net->register_custom_layer("rife.Warp", Warp_layer_creator);
        g_rife_net->register_custom_layer("Warp", Warp_layer_creator);

        bool useVulkan = (gpuIndex >= 0 && gpuIndex < g_gpu_count);
        g_rife_net->opt.use_vulkan_compute = useVulkan;
        g_rife_net->opt.use_fp16_packed = useVulkan;
        g_rife_net->opt.use_fp16_storage = useVulkan;
        g_rife_net->opt.use_fp16_arithmetic = false;
        g_rife_net->opt.use_int8_storage = true;
        g_rife_net->opt.num_threads = std::max(1, std::min(6, (int)ncnn::get_cpu_count()));

        if (useVulkan) {
            g_rife_net->set_vulkan_device(gpuIndex);
        }

        int loadParamRet = g_rife_net->load_param(paramPathStr);
        int loadModelRet = g_rife_net->load_model(binPathStr);

        env->ReleaseStringUTFChars(paramPath, paramPathStr);
        env->ReleaseStringUTFChars(binPath, binPathStr);

        if (loadParamRet != 0 || loadModelRet != 0) {
            LOGE("Failed to auto-load RIFE model: param=%d, bin=%d", loadParamRet, loadModelRet);
            delete g_rife_net;
            g_rife_net = nullptr;
            return -3;
        }
    }

    if (srcBitmap0 == nullptr || srcBitmap1 == nullptr || dstBitmap == nullptr) {
        LOGE("Invalid arguments passed to nativeRifeInterpolate");
        return -1;
    }

    AndroidBitmapInfo info0, info1, dstInfo;
    if (AndroidBitmap_getInfo(env, srcBitmap0, &info0) < 0 || info0.format != ANDROID_BITMAP_FORMAT_RGBA_8888 ||
        AndroidBitmap_getInfo(env, srcBitmap1, &info1) < 0 || info1.format != ANDROID_BITMAP_FORMAT_RGBA_8888 ||
        AndroidBitmap_getInfo(env, dstBitmap, &dstInfo) < 0 || dstInfo.format != ANDROID_BITMAP_FORMAT_RGBA_8888) {
        LOGE("Failed to get bitmap info or format is not RGBA_8888");
        return -1;
    }

    int w = (int)info0.width;
    int h = (int)info0.height;
    if ((int)info1.width != w || (int)info1.height != h || (int)dstInfo.width != w || (int)dstInfo.height != h) {
        LOGE("Dimensions mismatch between src0, src1, dst (%dx%d vs %dx%d)", w, h, (int)dstInfo.width, (int)dstInfo.height);
        return -1;
    }

    void* pix0 = nullptr;
    void* pix1 = nullptr;
    void* pixDst = nullptr;
    if (AndroidBitmap_lockPixels(env, srcBitmap0, &pix0) < 0 || pix0 == nullptr ||
        AndroidBitmap_lockPixels(env, srcBitmap1, &pix1) < 0 || pix1 == nullptr ||
        AndroidBitmap_lockPixels(env, dstBitmap, &pixDst) < 0 || pixDst == nullptr) {
        if (pix0) AndroidBitmap_unlockPixels(env, srcBitmap0);
        if (pix1) AndroidBitmap_unlockPixels(env, srcBitmap1);
        if (pixDst) AndroidBitmap_unlockPixels(env, dstBitmap);
        LOGE("Failed to lock bitmap pixels");
        return -1;
    }

    int padW = (w + 31) / 32 * 32;
    int padH = (h + 31) / 32 * 32;

    ncnn::Mat in0(padW, padH, 3);
    ncnn::Mat in1(padW, padH, 3);
    const float inv255 = 1.0f / 255.0f;

    for (int y = 0; y < h; y++) {
        const uint8_t* row0 = (const uint8_t*)pix0 + y * info0.stride;
        const uint8_t* row1 = (const uint8_t*)pix1 + y * info1.stride;
        float* r0 = in0.channel(0).row(y);
        float* g0 = in0.channel(1).row(y);
        float* b0 = in0.channel(2).row(y);
        float* r1 = in1.channel(0).row(y);
        float* g1 = in1.channel(1).row(y);
        float* b1 = in1.channel(2).row(y);
        for (int x = 0; x < w; x++) {
            r0[x] = (float)row0[x * 4 + 0] * inv255;
            g0[x] = (float)row0[x * 4 + 1] * inv255;
            b0[x] = (float)row0[x * 4 + 2] * inv255;
            r1[x] = (float)row1[x * 4 + 0] * inv255;
            g1[x] = (float)row1[x * 4 + 1] * inv255;
            b1[x] = (float)row1[x * 4 + 2] * inv255;
        }
        for (int x = w; x < padW; x++) {
            r0[x] = r0[w - 1]; g0[x] = g0[w - 1]; b0[x] = b0[w - 1];
            r1[x] = r1[w - 1]; g1[x] = g1[w - 1]; b1[x] = b1[w - 1];
        }
    }
    for (int y = h; y < padH; y++) {
        for (int c = 0; c < 3; c++) {
            memcpy(in0.channel(c).row(y), in0.channel(c).row(h - 1), padW * sizeof(float));
            memcpy(in1.channel(c).row(y), in1.channel(c).row(h - 1), padW * sizeof(float));
        }
    }

    ncnn::Extractor ex = g_rife_net->create_extractor();
    ex.input("in0", in0);
    ex.input("in1", in1);

    ncnn::Mat in2(1, 1, 1);
    in2[0] = 0.5f;
    ex.input("in2", in2);

    ncnn::Mat outMat;
    int extractRet = ex.extract("out0", outMat);
    if (extractRet != 0 || outMat.empty()) {
        extractRet = ex.extract("out", outMat);
    }
    if (extractRet != 0 || outMat.empty()) {
        extractRet = ex.extract("output", outMat);
    }

    int resultCode = 0;
    if (extractRet != 0 || outMat.empty()) {
        LOGE("RIFE inference failed to extract output, ret=%d", extractRet);
        resultCode = -4;
    } else {
        for (int y = 0; y < h; y++) {
            const float* r = outMat.channel(0).row(y);
            const float* g = outMat.channel(1).row(y);
            const float* b = outMat.channel(2).row(y);
            uint8_t* dstRow = (uint8_t*)pixDst + y * dstInfo.stride;
            for (int x = 0; x < w; x++) {
                float rv = r[x] * 255.0f + 0.5f;
                float gv = g[x] * 255.0f + 0.5f;
                float bv = b[x] * 255.0f + 0.5f;
                dstRow[x * 4 + 0] = (uint8_t)std::min(std::max(rv, 0.0f), 255.0f);
                dstRow[x * 4 + 1] = (uint8_t)std::min(std::max(gv, 0.0f), 255.0f);
                dstRow[x * 4 + 2] = (uint8_t)std::min(std::max(bv, 0.0f), 255.0f);
                dstRow[x * 4 + 3] = 255;
            }
        }
    }

    AndroidBitmap_unlockPixels(env, dstBitmap);
    AndroidBitmap_unlockPixels(env, srcBitmap1);
    AndroidBitmap_unlockPixels(env, srcBitmap0);

    return resultCode;
}

} // extern "C"
