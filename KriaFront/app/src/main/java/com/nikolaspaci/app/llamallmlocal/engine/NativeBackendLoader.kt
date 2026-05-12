package com.nikolaspaci.app.llamallmlocal.engine

import android.content.Context
import android.util.Log
import com.nikolaspaci.app.llamallmlocal.LlamaApi
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Loads, once per process, the dynamic ggml backends shipped as
 * `libggml-cpu-*.so` variants (and Vulkan/etc. when present) via
 * `LlamaApi.loadBackends(nativeLibDir)` → native `ggml_backend_load_all_from_path()`.
 *
 * The ggml device registry is process-global and shared by llama.cpp and
 * whisper.cpp. Any native init (llama, whisper, ...) must invoke
 * [ensureLoaded] beforehand, otherwise `ggml_backend_dev_backend_reg` aborts
 * the process with SIGABRT when the registry is empty.
 *
 * Idempotency is enforced Kotlin-side via double-checked locking — kept even
 * though the native loader dedupes by `reg*` pointer, to stay immune to
 * future backends that would return a fresh registration on each call.
 */
@Singleton
class NativeBackendLoader @Inject constructor(
    @ApplicationContext private val context: Context
) {
    @Volatile private var loaded = false
    private val lock = Any()

    fun ensureLoaded() {
        if (loaded) return
        synchronized(lock) {
            if (loaded) return
            val nativeLibDir = resolveNativeLibDir()
            Log.i(TAG, "Loading GGML backends from: $nativeLibDir")
            LlamaApi.loadBackends(nativeLibDir)
            loaded = true
        }
    }

    private fun resolveNativeLibDir(): String {
        val fallback = context.applicationInfo.nativeLibraryDir
        return try {
            val cl = context.classLoader as? dalvik.system.BaseDexClassLoader
            val rawLibPath = cl?.findLibrary("jniKriaCppWrapper") ?: return fallback
            val parent = File(rawLibPath).parentFile ?: return fallback
            val isReal = parent.isDirectory && !parent.absolutePath.contains(".apk!")
            val hasGgml = parent.list()?.any { it.startsWith("libggml-cpu") } == true
            if (isReal && hasGgml) parent.absolutePath else fallback
        } catch (t: Throwable) {
            Log.w(TAG, "resolveNativeLibDir fell back to applicationInfo", t)
            fallback
        }
    }

    companion object {
        private const val TAG = "NativeBackendLoader"
    }
}
