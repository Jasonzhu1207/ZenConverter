#include <jni.h>

#include <android/log.h>

#include <algorithm>
#include <cmath>
#include <cstdint>
#include <cstdio>
#include <string>
#include <vector>

#include <Eigen/Core>
#include <Eigen/Dense>

#include "model.hpp"

#define LOG_TAG "StemNative"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, LOG_TAG, __VA_ARGS__)

namespace {

constexpr int kSampleRate = 44100;
constexpr int kSourceCount = 4;

// demucs.cpp emits sources in this order (see cli-apps/demucs.cpp).
const char *kSourceNames[kSourceCount] = {"drums", "bass", "other", "vocals"};

std::string toUtf8(JNIEnv *env, jstring value) {
    if (value == nullptr) {
        return {};
    }
    const char *chars = env->GetStringUTFChars(value, nullptr);
    if (chars == nullptr) {
        return {};
    }
    std::string result(chars);
    env->ReleaseStringUTFChars(value, chars);
    return result;
}

void writeLittleEndian32(std::vector<uint8_t> &out, uint32_t value) {
    out.push_back(static_cast<uint8_t>(value & 0xFF));
    out.push_back(static_cast<uint8_t>((value >> 8) & 0xFF));
    out.push_back(static_cast<uint8_t>((value >> 16) & 0xFF));
    out.push_back(static_cast<uint8_t>((value >> 24) & 0xFF));
}

void writeLittleEndian16(std::vector<uint8_t> &out, uint16_t value) {
    out.push_back(static_cast<uint8_t>(value & 0xFF));
    out.push_back(static_cast<uint8_t>((value >> 8) & 0xFF));
}

// Writes a canonical 44-byte-header stereo 16-bit PCM WAV, which every player and
// the app's own FFmpeg pipeline can read back.
bool writeStereoWav16(const std::string &path, const Eigen::MatrixXf &stereo) {
    const int sampleCount = static_cast<int>(stereo.cols());
    const int channelCount = 2;
    const int bitsPerSample = 16;
    const int byteRate = kSampleRate * channelCount * bitsPerSample / 8;
    const int blockAlign = channelCount * bitsPerSample / 8;
    const uint32_t dataBytes = static_cast<uint32_t>(sampleCount) * blockAlign;

    std::vector<uint8_t> header;
    header.reserve(44);
    const char *riff = "RIFF";
    header.insert(header.end(), riff, riff + 4);
    writeLittleEndian32(header, 36 + dataBytes);
    const char *wave = "WAVE";
    header.insert(header.end(), wave, wave + 4);
    const char *fmt = "fmt ";
    header.insert(header.end(), fmt, fmt + 4);
    writeLittleEndian32(header, 16);
    writeLittleEndian16(header, 1);  // PCM
    writeLittleEndian16(header, static_cast<uint16_t>(channelCount));
    writeLittleEndian32(header, static_cast<uint32_t>(kSampleRate));
    writeLittleEndian32(header, static_cast<uint32_t>(byteRate));
    writeLittleEndian16(header, static_cast<uint16_t>(blockAlign));
    writeLittleEndian16(header, static_cast<uint16_t>(bitsPerSample));
    const char *data = "data";
    header.insert(header.end(), data, data + 4);
    writeLittleEndian32(header, dataBytes);

    FILE *file = fopen(path.c_str(), "wb");
    if (file == nullptr) {
        return false;
    }
    fwrite(header.data(), 1, header.size(), file);

    std::vector<int16_t> interleaved(static_cast<size_t>(sampleCount) * channelCount);
    for (int i = 0; i < sampleCount; ++i) {
        for (int channel = 0; channel < channelCount; ++channel) {
            const float sample = std::clamp(stereo(channel, i), -1.0f, 1.0f);
            interleaved[static_cast<size_t>(i) * channelCount + channel] =
                static_cast<int16_t>(std::lround(sample * 32767.0f));
        }
    }
    fwrite(interleaved.data(), sizeof(int16_t), interleaved.size(), file);
    fclose(file);
    return true;
}

// Linear resampling. The Kotlin side already decodes at 44.1 kHz, so this only
// runs for callers that hand us a different rate.
Eigen::MatrixXf resampleTo44100(const float *samples, int sampleCount, int sampleRateHz) {
    if (sampleRateHz == kSampleRate || sampleCount <= 0) {
        Eigen::MatrixXf mono(1, sampleCount);
        for (int i = 0; i < sampleCount; ++i) {
            mono(0, i) = samples[i];
        }
        return mono;
    }
    const double ratio = static_cast<double>(kSampleRate) / static_cast<double>(sampleRateHz);
    const int outCount = static_cast<int>(std::floor(sampleCount * ratio));
    Eigen::MatrixXf mono(1, outCount);
    for (int i = 0; i < outCount; ++i) {
        const double position = i / ratio;
        const int index = static_cast<int>(position);
        const double fraction = position - index;
        const float a = samples[std::clamp(index, 0, sampleCount - 1)];
        const float b = samples[std::clamp(index + 1, 0, sampleCount - 1)];
        mono(0, i) = static_cast<float>(a + (b - a) * fraction);
    }
    return mono;
}

}  // namespace

extern "C" {

JNIEXPORT jint JNICALL
Java_org_zenconverter_app_model_StemNative_nativeStemInit(JNIEnv *, jobject) {
    LOGI("Demucs separator ready (demucs.cpp)");
    return 0;
}

JNIEXPORT jint JNICALL
Java_org_zenconverter_app_model_StemNative_nativeStemSeparate(
    JNIEnv *env,
    jobject,
    jfloatArray samples,
    jint sampleRateHz,
    jstring modelPath,
    jstring outputDir,
    jobject progressCallback,
    jobject cancelCheck) {
    if (samples == nullptr || modelPath == nullptr || outputDir == nullptr) {
        return -1;
    }
    const jsize sampleCount = env->GetArrayLength(samples);
    if (sampleCount <= 0) {
        return -1;
    }

    std::vector<float> pcm(static_cast<size_t>(sampleCount));
    env->GetFloatArrayRegion(samples, 0, sampleCount, pcm.data());

    const std::string modelFile = toUtf8(env, modelPath);
    const std::string outDir = toUtf8(env, outputDir);

    jmethodID progressMethod = nullptr;
    if (progressCallback != nullptr) {
        jclass progressClass = env->GetObjectClass(progressCallback);
        progressMethod = env->GetMethodID(progressClass, "onProgress", "(F)V");
    }
    jmethodID cancelMethod = nullptr;
    if (cancelCheck != nullptr) {
        jclass cancelClass = env->GetObjectClass(cancelCheck);
        cancelMethod = env->GetMethodID(cancelClass, "isCancelled", "()Z");
    }

    auto *model = new (std::nothrow) demucscpp::demucs_model();
    if (model == nullptr) {
        LOGW("Could not allocate the Demucs model");
        return -1;
    }

    if (!demucscpp::load_demucs_model(modelFile, model)) {
        LOGW("Failed to load Demucs model at %s", modelFile.c_str());
        delete model;
        return -1;
    }

    const Eigen::MatrixXf mono = resampleTo44100(pcm.data(), sampleCount, sampleRateHz);

    // demucs.cpp works on stereo; a mono source is duplicated into both channels.
    Eigen::MatrixXf audio(2, mono.cols());
    audio.row(0) = mono.row(0);
    audio.row(1) = mono.row(0);

    demucscpp::ProgressCallback callback =
        [env, progressCallback, progressMethod](float progress, const std::string &) {
            if (progressCallback != nullptr && progressMethod != nullptr) {
                env->CallVoidMethod(progressCallback, progressMethod, static_cast<jfloat>(progress));
            }
        };

    Eigen::Tensor3dXf targets;
    try {
        targets = demucscpp::demucs_inference(*model, audio, callback);
    } catch (const std::exception &exception) {
        LOGW("Demucs inference threw: %s", exception.what());
        delete model;
        return -1;
    } catch (...) {
        LOGW("Demucs inference threw an unknown error");
        delete model;
        return -1;
    }

    const int sourceCount = model->is_4sources ? 4 : 6;
    const int writeCount = std::min(sourceCount, kSourceCount);

    bool cancelled = false;
    if (cancelCheck != nullptr && cancelMethod != nullptr) {
        cancelled = env->CallBooleanMethod(cancelCheck, cancelMethod) == JNI_TRUE;
    }

    int written = 0;
    if (!cancelled) {
        for (int source = 0; source < writeCount; ++source) {
            Eigen::MatrixXf stem(2, targets.dimension(2));
            for (int channel = 0; channel < 2; ++channel) {
                for (int i = 0; i < targets.dimension(2); ++i) {
                    stem(channel, i) = targets(source, channel, i);
                }
            }
            const std::string path = outDir + "/" + kSourceNames[source] + ".wav";
            if (writeStereoWav16(path, stem)) {
                ++written;
            } else {
                LOGW("Failed to write stem %s", path.c_str());
            }
        }
    }

    delete model;
    return cancelled ? -1 : written;
}

}  // extern "C"
