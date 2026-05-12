#ifndef INIT_WHISPER_JNI_HPP
#define INIT_WHISPER_JNI_HPP

#include <jni.h>

extern "C" JNIEXPORT jlong JNICALL
Java_com_nikolaspaci_app_llamallmlocal_WhisperApi_init(
    JNIEnv *env,
    jobject thiz,
    jstring modelPath,
    jint nThreads,
    jboolean useGpu);

#endif // INIT_WHISPER_JNI_HPP
