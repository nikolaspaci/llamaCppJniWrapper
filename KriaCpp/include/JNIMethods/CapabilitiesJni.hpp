#ifndef CAPABILITIES_JNI_HPP
#define CAPABILITIES_JNI_HPP

#include <jni.h>

extern "C" {
    JNIEXPORT jboolean JNICALL Java_com_nikolaspaci_app_llamallmlocal_LlamaApi_supportsThinking(JNIEnv*, jobject, jlong);
    JNIEXPORT jboolean JNICALL Java_com_nikolaspaci_app_llamallmlocal_LlamaApi_hasVision(JNIEnv*, jobject, jlong);
    JNIEXPORT jboolean JNICALL Java_com_nikolaspaci_app_llamallmlocal_LlamaApi_initMultimodal(JNIEnv*, jobject, jlong, jstring);
}

#endif // CAPABILITIES_JNI_HPP
