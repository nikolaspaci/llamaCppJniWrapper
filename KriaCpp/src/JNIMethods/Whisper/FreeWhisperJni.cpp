#include "JNIMethods/Whisper/FreeWhisperJni.hpp"
#include "session/WhisperSession.hpp"

extern "C" JNIEXPORT void JNICALL
Java_com_nikolaspaci_app_llamallmlocal_WhisperApi_free(
    JNIEnv * /*env*/,
    jobject /* this */,
    jlong session_ptr) {
    auto *session = reinterpret_cast<WhisperSession *>(session_ptr);
    if (session) {
        delete session;
    }
}
