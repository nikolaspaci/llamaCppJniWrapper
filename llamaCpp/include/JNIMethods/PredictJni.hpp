#ifndef PREDICT_JNI_HPP
#define PREDICT_JNI_HPP
#include <jni.h>

extern "C" JNIEXPORT jstring JNICALL
Java_com_nikolaspaci_app_LlamaWrapper__predict(JNIEnv *env, jobject /* this */, jlong full_ctx_ptr, jstring prompt_j);

#endif // PREDICT_JNI_HPP
