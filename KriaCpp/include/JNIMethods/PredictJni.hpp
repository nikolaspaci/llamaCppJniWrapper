#ifndef PREDICT_JNI_HPP
#define PREDICT_JNI_HPP
#include <jni.h>

extern "C" JNIEXPORT void JNICALL
Java_com_nikolaspaci_app_llamallmlocal_LlamaApi_predict(JNIEnv *env, jobject, jlong session_ptr, jstring prompt_j, jobject modelParameters, jboolean enableThinking, jobject callback_obj);

extern "C" JNIEXPORT void JNICALL
Java_com_nikolaspaci_app_llamallmlocal_LlamaApi_predictWithMedia(JNIEnv *env, jobject, jlong session_ptr, jstring prompt_j, jbyteArray imageData, jobject modelParameters, jboolean enableThinking, jobject callback_obj);

#endif // PREDICT_JNI_HPP
