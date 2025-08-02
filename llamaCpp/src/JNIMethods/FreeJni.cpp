#include "JNIMethods/FreeJni.hpp"
#include "session/LlamaSession.hpp"
extern "C" JNIEXPORT void JNICALL
Java_com_nikolaspaci_app_llamallmlocal_LlamaApi_free(JNIEnv *env, jobject /* this */, jlong session_ptr) {
    auto* session = reinterpret_cast<LlamaSession*>(session_ptr);
    if (session) {
        delete session;
    }
}
