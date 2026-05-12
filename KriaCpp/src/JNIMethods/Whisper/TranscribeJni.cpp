#include "JNIMethods/Whisper/TranscribeJni.hpp"
#include "session/WhisperSession.hpp"
#include <chrono>
#include <string>
#include <vector>

struct CallbackCtx {
    JNIEnv *env;
    jobject callback_obj;
    jmethodID on_segment;
    WhisperSession *session;
};

static void on_new_segment(struct whisper_context *ctx, struct whisper_state * /*state*/, int n_new, void *user_data) {
    auto *cbCtx = reinterpret_cast<CallbackCtx *>(user_data);
    if (!cbCtx || !cbCtx->on_segment) return;

    const int n_segments = whisper_full_n_segments(ctx);
    const int start = n_segments - n_new;
    for (int i = start; i < n_segments; ++i) {
        const char *text = whisper_full_get_segment_text(ctx, i);
        const int64_t t0 = whisper_full_get_segment_t0(ctx, i) * 10;
        const int64_t t1 = whisper_full_get_segment_t1(ctx, i) * 10;
        if (text) {
            jstring text_j = cbCtx->env->NewStringUTF(text);
            cbCtx->env->CallVoidMethod(cbCtx->callback_obj, cbCtx->on_segment, text_j,
                                       static_cast<jlong>(t0), static_cast<jlong>(t1));
            cbCtx->env->DeleteLocalRef(text_j);
        }
    }
}

static bool on_encoder_begin(struct whisper_context * /*ctx*/, struct whisper_state * /*state*/, void *user_data) {
    auto *cbCtx = reinterpret_cast<CallbackCtx *>(user_data);
    if (!cbCtx || !cbCtx->session) return true;
    return !cbCtx->session->cancelRequested.load();
}

static bool on_abort(void *user_data) {
    auto *cbCtx = reinterpret_cast<CallbackCtx *>(user_data);
    if (!cbCtx || !cbCtx->session) return false;
    return cbCtx->session->cancelRequested.load();
}

extern "C" JNIEXPORT void JNICALL
Java_com_nikolaspaci_app_llamallmlocal_WhisperApi_transcribePcm(
    JNIEnv *env,
    jobject /* this */,
    jlong session_ptr,
    jfloatArray pcmData,
    jstring language,
    jboolean translate,
    jobject callback_obj) {

    jclass callback_class = env->GetObjectClass(callback_obj);
    if (callback_class == nullptr) return;

    jmethodID on_segment_method  = env->GetMethodID(callback_class, "onSegment",  "(Ljava/lang/String;JJ)V");
    jmethodID on_complete_method = env->GetMethodID(callback_class, "onComplete", "(Ljava/lang/String;D)V");
    jmethodID on_error_method    = env->GetMethodID(callback_class, "onError",    "(Ljava/lang/String;)V");

    if (on_complete_method == nullptr || on_error_method == nullptr) {
        env->ExceptionClear();
        return;
    }
    if (on_segment_method == nullptr) {
        env->ExceptionClear();
    }

    auto *session = reinterpret_cast<WhisperSession *>(session_ptr);
    if (!session || !session->ctx) {
        env->CallVoidMethod(callback_obj, on_error_method,
                            env->NewStringUTF("Erreur: session whisper invalide."));
        return;
    }

    std::lock_guard<std::mutex> lock(session->transcribeMutex);
    session->cancelRequested.store(false);

    jsize pcmLen = env->GetArrayLength(pcmData);
    if (pcmLen <= 0) {
        env->CallVoidMethod(callback_obj, on_error_method,
                            env->NewStringUTF("Erreur: audio vide."));
        return;
    }

    std::vector<float> pcm(pcmLen);
    env->GetFloatArrayRegion(pcmData, 0, pcmLen, pcm.data());

    const char *lang_c = env->GetStringUTFChars(language, nullptr);
    std::string lang_str(lang_c ? lang_c : "auto");
    env->ReleaseStringUTFChars(language, lang_c);

    CallbackCtx cbCtx{env, callback_obj, on_segment_method, session};

    whisper_full_params params = whisper_full_default_params(WHISPER_SAMPLING_GREEDY);
    params.n_threads        = session->n_threads;
    params.translate        = (translate == JNI_TRUE);
    params.language         = lang_str.c_str();
    params.detect_language  = (lang_str == "auto");
    params.print_progress   = false;
    params.print_realtime   = false;
    params.print_timestamps = false;
    params.print_special    = false;
    params.no_context       = true;
    params.single_segment   = false;
    params.suppress_blank   = true;

    if (on_segment_method) {
        params.new_segment_callback           = on_new_segment;
        params.new_segment_callback_user_data = &cbCtx;
    }
    params.encoder_begin_callback           = on_encoder_begin;
    params.encoder_begin_callback_user_data = &cbCtx;
    params.abort_callback                   = on_abort;
    params.abort_callback_user_data         = &cbCtx;

    auto start_time = std::chrono::high_resolution_clock::now();
    const int rc = whisper_full(session->ctx, params, pcm.data(), static_cast<int>(pcm.size()));
    auto end_time = std::chrono::high_resolution_clock::now();
    const double duration_sec =
        std::chrono::duration_cast<std::chrono::milliseconds>(end_time - start_time).count() / 1000.0;

    if (session->cancelRequested.load()) {
        env->CallVoidMethod(callback_obj, on_complete_method, env->NewStringUTF(""), duration_sec);
        return;
    }

    if (rc != 0) {
        env->CallVoidMethod(callback_obj, on_error_method,
                            env->NewStringUTF("Erreur lors de la transcription."));
        return;
    }

    std::string full_text;
    const int n_segments = whisper_full_n_segments(session->ctx);
    for (int i = 0; i < n_segments; ++i) {
        const char *text = whisper_full_get_segment_text(session->ctx, i);
        if (text) full_text += text;
    }

    jstring text_j = env->NewStringUTF(full_text.c_str());
    env->CallVoidMethod(callback_obj, on_complete_method, text_j, duration_sec);
    env->DeleteLocalRef(text_j);
}

extern "C" JNIEXPORT void JNICALL
Java_com_nikolaspaci_app_llamallmlocal_WhisperApi_stopTranscribe(
    JNIEnv * /*env*/,
    jobject /* this */,
    jlong session_ptr) {
    auto *session = reinterpret_cast<WhisperSession *>(session_ptr);
    if (session) {
        session->cancelRequested.store(true);
    }
}
