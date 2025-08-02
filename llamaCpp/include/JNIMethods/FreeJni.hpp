#ifndef FREEJNI_HPP
#define FREEJNI_HPP

#include "jni.h"

extern "C" JNIEXPORT void JNICALL
Java_com_nikolaspaci_app_llamallmlocal_LlamaApi_free(JNIEnv *env, jobject /* this */, jlong session_ptr) ;

#endif // FREEJNI_HPP