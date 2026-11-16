#include <jni.h>

#include <android/log.h>

#include <atomic>
#include <string>
#include <vector>

#include "whisper.h"

#define LOG_TAG "WhisperNative"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, LOG_TAG, __VA_ARGS__)

namespace {

struct CallbackContext {
    JavaVM *vm = nullptr;
    jobject progressCallback = nullptr;
    jmethodID progressMethod = nullptr;
    jobject cancelCheck = nullptr;
    jmethodID cancelMethod = nullptr;
};

// Progress and abort callbacks run on the whisper worker thread, which is not
// attached to the JVM, so every call has to attach first and detach after.
class ScopedEnv {
public:
    ScopedEnv(JavaVM *vm, bool *attached)
        : vm_(vm), attached_(attached) {
        if (vm_ == nullptr) {
            return;
        }
        jint result = vm_->GetEnv(reinterpret_cast<void **>(&env_), JNI_VERSION_1_6);
        if (result == JNI_EDETACHED) {
            if (vm_->AttachCurrentThread(&env_, nullptr) == JNI_OK) {
                *attached_ = true;
            } else {
                env_ = nullptr;
            }
        }
    }

    ~ScopedEnv() {
        if (*attached_ && vm_ != nullptr) {
            vm_->DetachCurrentThread();
        }
    }

    JNIEnv *env() const { return env_; }

private:
    JavaVM *vm_;
    JNIEnv *env_ = nullptr;
    bool *attached_;
};

void progressCallbackBridge(struct whisper_context *, struct whisper_state *, int progress, void *userData) {
    auto *context = static_cast<CallbackContext *>(userData);
    if (context == nullptr || context->progressCallback == nullptr) {
        return;
    }
    bool attached = false;
    ScopedEnv env(context->vm, &attached);
    if (env.env() == nullptr) {
        return;
    }
    env.env()->CallVoidMethod(
        context->progressCallback,
        context->progressMethod,
        static_cast<jfloat>(progress) / 100.0f);
}

bool abortCallbackBridge(void *userData) {
    auto *context = static_cast<CallbackContext *>(userData);
    if (context == nullptr || context->cancelCheck == nullptr) {
        return false;
    }
    bool attached = false;
    ScopedEnv env(context->vm, &attached);
    if (env.env() == nullptr) {
        return false;
    }
    return env.env()->CallBooleanMethod(context->cancelCheck, context->cancelMethod) == JNI_TRUE;
}

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

void appendEscaped(std::string &out, const char *text) {
    for (const char *cursor = text; cursor != nullptr && *cursor != '\0'; ++cursor) {
        const unsigned char c = static_cast<unsigned char>(*cursor);
        switch (c) {
            case '"': out += "\\\""; break;
            case '\\': out += "\\\\"; break;
            case '\n': out += "\\n"; break;
            case '\r': out += "\\r"; break;
            case '\t': out += "\\t"; break;
            default:
                if (c < 0x20) {
                    char buffer[8];
                    snprintf(buffer, sizeof(buffer), "\\u%04x", c);
                    out += buffer;
                } else {
                    out += static_cast<char>(c);
                }
        }
    }
}

// whisper.cpp reports token timestamps in centiseconds.
double centisecondsToSeconds(int64_t value) {
    return static_cast<double>(value) / 100.0;
}

bool isSpecialToken(const char *text) {
    if (text == nullptr) {
        return true;
    }
    const std::string token(text);
    if (token.empty()) {
        return true;
    }
    const char first = token.front();
    const char last = token.back();
    if (first == '[' && last == ']') {
        return true;
    }
    if (first == '<' && last == '>') {
        return true;
    }
    if (first == '(' && last == ')') {
        return true;
    }
    return false;
}

}  // namespace

extern "C" {

JNIEXPORT jint JNICALL
Java_org_zenconverter_app_model_WhisperNative_nativeInit(JNIEnv *, jobject) {
    LOGI("whisper.cpp build: %s", whisper_version());
    return 0;
}

JNIEXPORT void JNICALL
Java_org_zenconverter_app_model_WhisperNative_nativeDestroy(JNIEnv *, jobject) {
    // Nothing is held between calls: each transcription owns its context.
}

JNIEXPORT jstring JNICALL
Java_org_zenconverter_app_model_WhisperNative_nativeTranscribe(
    JNIEnv *env,
    jobject,
    jfloatArray samples,
    jint sampleRateHz,
    jstring modelPath,
    jstring language,
    jobject progressCallback,
    jobject cancelCheck) {
    if (samples == nullptr || modelPath == nullptr) {
        return nullptr;
    }

    const jsize sampleCount = env->GetArrayLength(samples);
    if (sampleCount <= 0) {
        return nullptr;
    }
    std::vector<float> pcm(static_cast<size_t>(sampleCount));
    env->GetFloatArrayRegion(samples, 0, sampleCount, pcm.data());

    const std::string model = toUtf8(env, modelPath);
    const std::string languageCode = toUtf8(env, language);

    whisper_context_params contextParams = whisper_context_default_params();
    contextParams.use_gpu = false;

    struct whisper_context *context = whisper_init_from_file_with_params(model.c_str(), contextParams);
    if (context == nullptr) {
        LOGW("Failed to load Whisper model at %s", model.c_str());
        return nullptr;
    }

    CallbackContext callbacks;
    callbacks.vm = nullptr;
    env->GetJavaVM(&callbacks.vm);
    if (progressCallback != nullptr) {
        callbacks.progressCallback = env->NewGlobalRef(progressCallback);
        jclass progressClass = env->GetObjectClass(progressCallback);
        callbacks.progressMethod = env->GetMethodID(progressClass, "onProgress", "(F)V");
    }
    if (cancelCheck != nullptr) {
        callbacks.cancelCheck = env->NewGlobalRef(cancelCheck);
        jclass cancelClass = env->GetObjectClass(cancelCheck);
        callbacks.cancelMethod = env->GetMethodID(cancelClass, "isCancelled", "()Z");
    }

    whisper_full_params params = whisper_full_default_params(WHISPER_SAMPLING_GREEDY);
    params.print_progress = false;
    params.print_realtime = false;
    params.print_timestamps = false;
    params.print_special = false;
    params.translate = false;
    params.token_timestamps = true;
    params.n_threads = 4;
    params.language = languageCode.empty() ? "auto" : languageCode.c_str();
    params.progress_callback = callbacks.progressCallback != nullptr ? progressCallbackBridge : nullptr;
    params.progress_callback_user_data = &callbacks;
    params.abort_callback = callbacks.cancelCheck != nullptr ? abortCallbackBridge : nullptr;
    params.abort_callback_user_data = &callbacks;

    std::string json;
    const int result = whisper_full(context, params, pcm.data(), sampleCount);
    if (result == 0) {
        json += "{\"segments\":[";
        const int segmentCount = whisper_full_n_segments(context);
        for (int segmentIndex = 0; segmentIndex < segmentCount; ++segmentIndex) {
            if (segmentIndex > 0) {
                json += ",";
            }
            const int64_t t0 = whisper_full_get_segment_t0(context, segmentIndex);
            const int64_t t1 = whisper_full_get_segment_t1(context, segmentIndex);
            const char *text = whisper_full_get_segment_text(context, segmentIndex);

            char header[96];
            snprintf(
                header,
                sizeof(header),
                "{\"start\":%.3f,\"end\":%.3f,\"text\":\"",
                centisecondsToSeconds(t0),
                centisecondsToSeconds(t1));
            json += header;
            appendEscaped(json, text);
            json += "\",\"words\":[";

            const int tokenCount = whisper_full_n_tokens(context, segmentIndex);
            bool firstWord = true;
            for (int tokenIndex = 0; tokenIndex < tokenCount; ++tokenIndex) {
                const char *tokenText = whisper_full_get_token_text(context, segmentIndex, tokenIndex);
                if (isSpecialToken(tokenText)) {
                    continue;
                }
                const whisper_token_data tokenData =
                    whisper_full_get_token_data(context, segmentIndex, tokenIndex);
                if (!firstWord) {
                    json += ",";
                }
                firstWord = false;

                char wordHeader[96];
                snprintf(
                    wordHeader,
                    sizeof(wordHeader),
                    "{\"word\":\"");
                json += wordHeader;
                // Leading spaces mark a new word; keep the token text as whisper
                // produced it and let the Kotlin side trim.
                appendEscaped(json, tokenText);
                char wordFooter[64];
                snprintf(
                    wordFooter,
                    sizeof(wordFooter),
                    "\",\"start\":%.3f,\"end\":%.3f}",
                    centisecondsToSeconds(tokenData.t0),
                    centisecondsToSeconds(tokenData.t1));
                json += wordFooter;
            }
            json += "]}";
        }
        json += "]}";
    } else {
        LOGW("whisper_full failed with code %d", result);
    }

    if (callbacks.progressCallback != nullptr) {
        env->DeleteGlobalRef(callbacks.progressCallback);
    }
    if (callbacks.cancelCheck != nullptr) {
        env->DeleteGlobalRef(callbacks.cancelCheck);
    }
    whisper_free(context);

    if (json.empty()) {
        return nullptr;
    }
    return env->NewStringUTF(json.c_str());
}

}  // extern "C"
