#ifndef PREDICT_JNI_HPP
#define PREDICT_JNI_HPP
#include <jni.h>

extern "C" JNIEXPORT jstring JNICALL
Java_com_nikolaspaci_app_llamallmlocal_LlamaApi_predict(JNIEnv *env, jobject /* this */, jlong session_ptr, jstring prompt_j);

#endif // PREDICT_JNI_HPP
