#ifndef INIT_JNI_HPP
#define INIT_JNI_HPP
#include <jni.h>

extern "C" JNIEXPORT jlong JNICALL
Java_com_nikolaspaci_app_LlamaWrapper__init(JNIEnv *env, jobject /* this */, jstring modelPath);
#endif // INIT_JNI_HPP