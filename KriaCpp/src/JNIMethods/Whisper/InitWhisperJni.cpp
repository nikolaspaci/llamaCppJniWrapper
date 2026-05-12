#include "JNIMethods/Whisper/InitWhisperJni.hpp"
#include "session/WhisperSession.hpp"

extern "C" JNIEXPORT jlong JNICALL
Java_com_nikolaspaci_app_llamallmlocal_WhisperApi_init(
    JNIEnv *env,
    jobject /* this */,
    jstring modelPath,
    jint nThreads,
    jboolean useGpu) {

    whisper_context_params cparams = whisper_context_default_params();
    cparams.use_gpu = (useGpu == JNI_TRUE);

    const char *path = env->GetStringUTFChars(modelPath, 0);
    whisper_context *ctx = whisper_init_from_file_with_params(path, cparams);
    env->ReleaseStringUTFChars(modelPath, path);

    if (!ctx) {
        return 0;
    }

    auto *session = new WhisperSession();
    session->ctx = ctx;
    session->n_threads = nThreads > 0 ? nThreads : 4;
    return reinterpret_cast<jlong>(session);
}
