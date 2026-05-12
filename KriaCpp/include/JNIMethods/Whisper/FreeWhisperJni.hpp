#ifndef FREE_WHISPER_JNI_HPP
#define FREE_WHISPER_JNI_HPP

#include <jni.h>

extern "C" JNIEXPORT void JNICALL
Java_com_nikolaspaci_app_llamallmlocal_WhisperApi_free(
    JNIEnv *env,
    jobject thiz,
    jlong session_ptr);

#endif // FREE_WHISPER_JNI_HPP
