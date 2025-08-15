#ifndef RESTOREHISTORYJNI_HPP
#define RESTOREHISTORYJNI_HPP

#include "jni.h"

extern "C" JNIEXPORT void JNICALL
Java_com_nikolaspaci_app_llamallmlocal_LlamaApi_restoreHistory(
    JNIEnv *env,
    jobject /* this */,
    jlong session_ptr,
    jobjectArray messages
);

#endif // RESTOREHISTORYJNI_HPP
