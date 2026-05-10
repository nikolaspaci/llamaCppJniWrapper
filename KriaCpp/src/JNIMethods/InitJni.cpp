#include "JNIMethods/InitJni.hpp"
#include "session/LlamaSession.hpp"
#include "llama-cpp.h"
#include "ggml-backend.h"

#ifdef __ANDROID__
#include "ggml.h"
#include "util/NativeLogBuffer.hpp"
#include <android/log.h>

static void kria_native_log_callback(ggml_log_level level, const char *text, void * /*user_data*/) {
    if (text == nullptr) return;
    int prio;
    const char* level_str;
    switch (level) {
        case GGML_LOG_LEVEL_ERROR: prio = ANDROID_LOG_ERROR; level_str = "E"; break;
        case GGML_LOG_LEVEL_WARN:  prio = ANDROID_LOG_WARN;  level_str = "W"; break;
        case GGML_LOG_LEVEL_INFO:  prio = ANDROID_LOG_INFO;  level_str = "I"; break;
        case GGML_LOG_LEVEL_DEBUG: prio = ANDROID_LOG_DEBUG; level_str = "D"; break;
        case GGML_LOG_LEVEL_CONT:  prio = ANDROID_LOG_INFO;  level_str = "I"; break;
        default:                   prio = ANDROID_LOG_INFO;  level_str = "V"; break;
    }
    __android_log_write(prio, "llama.cpp", text);
    // Callback is invoked through a C ABI from ggml/llama.cpp threads —
    // any escaping C++ exception (e.g. std::bad_alloc from buffer push) is UB.
    try {
        kria::NativeLogBuffer::instance().push(level_str, "llama.cpp", text);
    } catch (...) {
        // Drop the line silently rather than corrupt the caller.
    }
}
#endif

extern "C" JNIEXPORT void JNICALL
Java_com_nikolaspaci_app_llamallmlocal_LlamaApi_loadBackends(JNIEnv *env, jobject /* this */, jstring nativeLibDir) {
#ifdef __ANDROID__
    // Redirect llama.cpp / ggml logs to Android logcat (tag: llama.cpp)
    llama_log_set(kria_native_log_callback, nullptr);
    ggml_log_set(kria_native_log_callback, nullptr);
#endif
    const char *path = env->GetStringUTFChars(nativeLibDir, 0);
    ggml_backend_load_all_from_path(path);
    env->ReleaseStringUTFChars(nativeLibDir, path);
    llama_backend_init();
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_nikolaspaci_app_llamallmlocal_LlamaApi_init(JNIEnv *env, jobject /* this */, jstring modelPath, jobject modelParameters) {

    // Find the ModelParameter class and its fields
    jclass modelParamsClass = env->FindClass("com/nikolaspaci/app/llamallmlocal/data/database/ModelParameter");
    jfieldID temperatureField = env->GetFieldID(modelParamsClass, "temperature", "F");
    jfieldID topKField = env->GetFieldID(modelParamsClass, "topK", "I");
    jfieldID topPField = env->GetFieldID(modelParamsClass, "topP", "F");
    jfieldID minPField = env->GetFieldID(modelParamsClass, "minP", "F");
    jfieldID contextSizeField = env->GetFieldID(modelParamsClass, "contextSize", "I");
    jfieldID threadCountField = env->GetFieldID(modelParamsClass, "threadCount", "I");
    jfieldID useGpuField = env->GetFieldID(modelParamsClass, "useGpu", "Z");
    jfieldID gpuLayersField = env->GetFieldID(modelParamsClass, "gpuLayers", "I");

    // Get the values from the modelParameters object
    jfloat temperature = env->GetFloatField(modelParameters, temperatureField);
    jint topK = env->GetIntField(modelParameters, topKField);
    jfloat topP = env->GetFloatField(modelParameters, topPField);
    jfloat minP = env->GetFloatField(modelParameters, minPField);
    jint contextSize = env->GetIntField(modelParameters, contextSizeField);
    jint threadCount = env->GetIntField(modelParameters, threadCountField);
    jboolean useGpu = env->GetBooleanField(modelParameters, useGpuField);
    jint gpuLayers = env->GetIntField(modelParameters, gpuLayersField);


    // Prepare the parameters for the model and context.
    llama_model_params model_params = llama_model_default_params();
    model_params.use_mmap = true; // Use memory-mapped files for model loading.
    model_params.use_mlock = false;
    if (useGpu) {
        model_params.n_gpu_layers = gpuLayers;
    }
    llama_context_params ctx_params = llama_context_default_params();

    ctx_params.n_ctx = contextSize;
    ctx_params.n_threads = threadCount;
    ctx_params.n_threads_batch = threadCount;
    ctx_params.no_perf = true; // Disable performance monitoring.

    // Create a session on the heap
    auto* session = new LlamaSession();
    session->sparams.temp = temperature;
    session->sparams.top_k = topK;
    session->sparams.top_p = topP;
    session->sparams.min_p = minP;


    // Load the model and assign the raw pointer to the unique_ptr
    const char *path = env->GetStringUTFChars(modelPath, 0);
    session->model.reset(llama_model_load_from_file(path, model_params));
    env->ReleaseStringUTFChars(modelPath, path);

    if (!session->model) {
        delete session; // Cleanup the session if the model failed to load
        return 0;
    }

    // Create the context and assign it to the unique_ptr
    session->context.reset(llama_init_from_model(session->model.get(), ctx_params));
    if (!session->context) {
        delete session; // The model's unique_ptr will be automatically released here
        return 0;
    }

    // Initialize chat templates and detect thinking support
    session->chatTemplates = common_chat_templates_init(session->model.get(), "");
    if (session->chatTemplates) {
        session->thinkingSupported = common_chat_templates_support_enable_thinking(session->chatTemplates.get());
    }

    // Return the pointer to the session
    return reinterpret_cast<jlong>(session);
}
