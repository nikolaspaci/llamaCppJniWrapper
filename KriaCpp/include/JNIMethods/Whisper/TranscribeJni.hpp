#ifndef TRANSCRIBE_JNI_HPP
#define TRANSCRIBE_JNI_HPP

#include <jni.h>

extern "C" JNIEXPORT void JNICALL
Java_com_nikolaspaci_app_llamallmlocal_WhisperApi_transcribePcm(
    JNIEnv *env,
    jobject thiz,
    jlong session_ptr,
    jfloatArray pcmData,
    jstring language,
    jboolean translate,
    jobject callback_obj);

extern "C" JNIEXPORT void JNICALL
Java_com_nikolaspaci_app_llamallmlocal_WhisperApi_stopTranscribe(
    JNIEnv *env,
    jobject thiz,
    jlong session_ptr);

#endif // TRANSCRIBE_JNI_HPP
