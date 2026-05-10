package com.nikolaspaci.app.llamallmlocal.util

import android.os.Build
import android.util.Log
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.google.firebase.firestore.FirebaseFirestore
import com.nikolaspaci.app.llamallmlocal.LlamaApi
import kotlinx.coroutines.CancellationException
import java.io.PrintWriter
import java.io.StringWriter
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RemoteErrorLogger @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val crashlytics: FirebaseCrashlytics
) {

    fun log(source: String, throwable: Throwable, extras: Map<String, Any?> = emptyMap()) {
        if (throwable is CancellationException) return
        Log.e(TAG, "[$source] ${throwable.message}", throwable)
        try { crashlytics.recordException(throwable) } catch (_: Throwable) { /* never let the reporter throw */ }

        val sw = StringWriter()
        throwable.printStackTrace(PrintWriter(sw))

        val nativeLogTail = try {
            LlamaApi.getNativeLogTail().takeLast(15000)
        } catch (_: Throwable) {
            null
        }

        val payload = mutableMapOf<String, Any?>(
            "source" to source,
            "message" to (throwable.message ?: throwable::class.java.simpleName),
            "exceptionClass" to throwable::class.java.name,
            "stackTrace" to sw.toString().take(8000),
            "nativeLogTail" to nativeLogTail,
            "deviceModel" to Build.MODEL,
            "deviceManufacturer" to Build.MANUFACTURER,
            "deviceProduct" to Build.PRODUCT,
            "androidSdk" to Build.VERSION.SDK_INT,
            "androidRelease" to Build.VERSION.RELEASE,
            "abi" to (Build.SUPPORTED_ABIS.firstOrNull() ?: "unknown"),
            "soc" to runCatching { Build.SOC_MODEL }.getOrNull(),
            "timestamp" to System.currentTimeMillis()
        )
        payload.putAll(extras)

        runCatching {
            firestore.collection(COLLECTION)
                .add(payload)
                .addOnSuccessListener { ref -> Log.i(TAG, "Firestore error written: ${ref.id}") }
                .addOnFailureListener { e -> Log.w(TAG, "Firestore log failed", e) }
        }.onFailure { Log.w(TAG, "Firestore submit threw", it) }
    }

    companion object {
        private const val TAG = "RemoteErrorLogger"
        private const val COLLECTION = "client_errors"
    }
}
