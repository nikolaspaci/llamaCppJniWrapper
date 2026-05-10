#include "JNIMethods/HardwareInfoJni.hpp"
#include "util/NativeLogBuffer.hpp"
#include <string>

#ifdef GGML_USE_VULKAN
#include "ggml-vulkan.h"
#endif

extern "C" {

JNIEXPORT jbyteArray JNICALL
Java_com_nikolaspaci_app_llamallmlocal_LlamaApi_getNativeLogTailBytes(
    JNIEnv *env,
    jobject /* this */
) {
    // Return raw UTF-8 bytes so Kotlin can decode safely; NewStringUTF expects
    // modified-UTF-8 and rejects valid UTF-8 (4-byte sequences, embedded NULs)
    // that llama.cpp can emit when it logs raw token bytes.
    std::string tail = kria::NativeLogBuffer::instance().dump();
    jbyteArray out = env->NewByteArray(static_cast<jsize>(tail.size()));
    if (out == nullptr) return nullptr;
    env->SetByteArrayRegion(out, 0, static_cast<jsize>(tail.size()),
                            reinterpret_cast<const jbyte*>(tail.data()));
    return out;
}


JNIEXPORT jboolean JNICALL
Java_com_nikolaspaci_app_llamallmlocal_LlamaApi_isVulkanAvailable(
    JNIEnv *env,
    jobject /* this */
) {
#ifdef GGML_USE_VULKAN
    return ggml_vk_has_vulkan() ? JNI_TRUE : JNI_FALSE;
#else
    return JNI_FALSE;
#endif
}

JNIEXPORT jstring JNICALL
Java_com_nikolaspaci_app_llamallmlocal_LlamaApi_getVulkanDeviceInfo(
    JNIEnv *env,
    jobject /* this */
) {
#ifdef GGML_USE_VULKAN
    if (!ggml_vk_has_vulkan()) {
        return env->NewStringUTF("Vulkan not available");
    }

    const char* device_info = ggml_vk_get_device_description(0);
    return env->NewStringUTF(device_info ? device_info : "Unknown GPU");
#else
    return env->NewStringUTF("Vulkan not compiled");
#endif
}

JNIEXPORT jint JNICALL
Java_com_nikolaspaci_app_llamallmlocal_LlamaApi_getRecommendedGpuLayers(
    JNIEnv *env,
    jobject /* this */
) {
#ifdef GGML_USE_VULKAN
    if (!ggml_vk_has_vulkan()) {
        return 0;
    }

    size_t vram = ggml_vk_get_device_memory(0);
    int recommended_layers = static_cast<int>(vram / (200 * 1024 * 1024));

    return recommended_layers > 0 ? recommended_layers : 0;
#else
    return 0;
#endif
}

JNIEXPORT jlong JNICALL
Java_com_nikolaspaci_app_llamallmlocal_LlamaApi_getVulkanVramBytes(
    JNIEnv *env,
    jobject /* this */
) {
#ifdef GGML_USE_VULKAN
    if (!ggml_vk_has_vulkan()) {
        return 0;
    }
    return static_cast<jlong>(ggml_vk_get_device_memory(0));
#else
    return 0;
#endif
}

} // extern "C"
