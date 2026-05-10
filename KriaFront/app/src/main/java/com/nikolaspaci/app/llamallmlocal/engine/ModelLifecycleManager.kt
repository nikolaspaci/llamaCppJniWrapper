package com.nikolaspaci.app.llamallmlocal.engine

import android.util.Log
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ModelLifecycleManager @Inject constructor(
    private val engine: ModelEngine
) : DefaultLifecycleObserver {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var pendingUnload: Job? = null

    fun attach() {
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
    }

    override fun onStart(owner: LifecycleOwner) {
        pendingUnload?.cancel()
        pendingUnload = null
    }

    override fun onStop(owner: LifecycleOwner) {
        pendingUnload?.cancel()
        pendingUnload = scope.launch {
            delay(BACKGROUND_UNLOAD_DELAY_MS)
            performUnload("background timeout")
        }
    }

    fun unloadNow() {
        pendingUnload?.cancel()
        pendingUnload = scope.launch {
            performUnload("memory pressure")
        }
    }

    private suspend fun performUnload(reason: String) {
        if (!engine.isModelLoaded()) return
        Log.i(TAG, "Unloading model ($reason)")
        engine.stopPredict()
        engine.unloadModel()
    }

    companion object {
        private const val TAG = "ModelLifecycleManager"
        private const val BACKGROUND_UNLOAD_DELAY_MS = 30_000L
    }
}
