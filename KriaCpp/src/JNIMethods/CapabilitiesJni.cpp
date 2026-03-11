#include "JNIMethods/CapabilitiesJni.hpp"
#include "session/LlamaSession.hpp"

extern "C" JNIEXPORT jboolean JNICALL
Java_com_nikolaspaci_app_llamallmlocal_LlamaApi_supportsThinking(
    JNIEnv*, jobject, jlong session_ptr) {
    auto* session = reinterpret_cast<LlamaSession*>(session_ptr);
    return (session && session->thinkingSupported) ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_nikolaspaci_app_llamallmlocal_LlamaApi_hasVision(
    JNIEnv*, jobject, jlong session_ptr) {
    auto* session = reinterpret_cast<LlamaSession*>(session_ptr);
    return (session && session->hasVision) ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_nikolaspaci_app_llamallmlocal_LlamaApi_initMultimodal(
    JNIEnv* env, jobject, jlong session_ptr, jstring mmproj_path_j) {
    auto* session = reinterpret_cast<LlamaSession*>(session_ptr);
    if (!session || !session->model) {
        return JNI_FALSE;
    }

    const char* mmproj_path = env->GetStringUTFChars(mmproj_path_j, nullptr);

    mtmd_context_params mtmd_params = mtmd_context_params_default();
    mtmd_params.use_gpu = true;
    mtmd_params.print_timings = false;
    mtmd_params.n_threads = 4;

    session->mtmdCtx = mtmd_init_from_file(mmproj_path, session->model.get(), mtmd_params);
    env->ReleaseStringUTFChars(mmproj_path_j, mmproj_path);

    if (session->mtmdCtx) {
        session->hasVision = mtmd_support_vision(session->mtmdCtx);
        return JNI_TRUE;
    }
    return JNI_FALSE;
}
