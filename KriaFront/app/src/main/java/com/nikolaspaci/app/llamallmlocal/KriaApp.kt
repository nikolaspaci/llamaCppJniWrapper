package com.nikolaspaci.app.llamallmlocal

import android.app.Application
import android.os.Build
import android.util.Log
import com.google.firebase.crashlytics.ktx.crashlytics
import com.google.firebase.ktx.Firebase
import com.nikolaspaci.app.llamallmlocal.engine.ModelLifecycleManager
import com.nikolaspaci.app.llamallmlocal.util.HardwareCapabilities
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class KriaApp : Application() {

    @Inject lateinit var hardwareCapabilities: HardwareCapabilities
    @Inject lateinit var modelLifecycleManager: ModelLifecycleManager

    override fun onCreate() {
        super.onCreate()
        registerDeviceCrashlyticsKeys()
        modelLifecycleManager.attach()
    }

    private fun registerDeviceCrashlyticsKeys() {
        try {
            val caps = hardwareCapabilities.detect(this)
            val crashlytics = Firebase.crashlytics

            crashlytics.setCustomKey("device_model", Build.MODEL)
            crashlytics.setCustomKey("soc_model", Build.SOC_MODEL)
            crashlytics.setCustomKey("cpu_cores", caps.cpuCores)
            crashlytics.setCustomKey("cpu_arch", caps.cpuArchitecture)
            crashlytics.setCustomKey("total_ram_gb", "%.1f".format(caps.totalRamBytes / (1024.0 * 1024.0 * 1024.0)))
            crashlytics.setCustomKey("available_ram_gb", "%.1f".format(caps.availableRamBytes / (1024.0 * 1024.0 * 1024.0)))
            crashlytics.setCustomKey("has_vulkan", caps.hasVulkan)
            crashlytics.setCustomKey("gpu_name", caps.gpuName ?: "unknown")
            crashlytics.setCustomKey("gpu_vram_mb", (caps.gpuVramBytes / (1024 * 1024)).toInt())
            crashlytics.setCustomKey("has_npu", caps.hasNpu)
            crashlytics.setCustomKey("npu_type", caps.npuType ?: "none")
        } catch (e: Exception) {
            Log.e("KriaApp", "Failed to register Crashlytics keys", e)
        }
    }
}
