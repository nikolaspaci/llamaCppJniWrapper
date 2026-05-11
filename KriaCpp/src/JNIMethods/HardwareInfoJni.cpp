#include "JNIMethods/HardwareInfoJni.hpp"

#ifdef GGML_USE_VULKAN
#include "ggml-vulkan.h"
#endif

extern "C" {


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
