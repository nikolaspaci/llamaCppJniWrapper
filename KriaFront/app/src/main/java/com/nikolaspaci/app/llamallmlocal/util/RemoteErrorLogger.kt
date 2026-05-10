package com.nikolaspaci.app.llamallmlocal.util

import android.os.Build
import android.util.Log
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.google.firebase.firestore.FirebaseFirestore
import com.nikolaspaci.app.llamallmlocal.LlamaApi
import kotlinx.coroutines.CancellationException
import java.io.PrintWriter
import java.io.StringWriter
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RemoteErrorLogger @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val crashlytics: FirebaseCrashlytics
) {

    // Stable across the process lifetime so checkpoints from the same hung
    // session can be grouped together when reading Firestore.
    private val sessionId: String = UUID.randomUUID().toString()

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
            "sessionId" to sessionId,
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

        try {
            firestore.collection(ERRORS_COLLECTION)
                .add(payload)
                .addOnSuccessListener { ref -> Log.i(TAG, "Firestore error written: ${ref.id}") }
                .addOnFailureListener { e -> Log.w(TAG, "Firestore log failed", e) }
        } catch (t: Throwable) { Log.w(TAG, "Firestore submit threw", t) }
    }

    /**
     * Lightweight breadcrumb written to a separate collection. Use this around
     * suspect-hang code paths so a stuck session leaves a trail of completed
     * milestones; the absence of the next checkpoint pinpoints where it stalled.
     * No native log tail / no stack trace — keep it cheap.
     */
    fun checkpoint(name: String, extras: Map<String, Any?> = emptyMap()) {
        Log.d(TAG, "checkpoint: $name $extras")
        val payload = mutableMapOf<String, Any?>(
            "sessionId" to sessionId,
            "name" to name,
            "deviceModel" to Build.MODEL,
            "soc" to runCatching { Build.SOC_MODEL }.getOrNull(),
            "androidSdk" to Build.VERSION.SDK_INT,
            "abi" to (Build.SUPPORTED_ABIS.firstOrNull() ?: "unknown"),
            "timestamp" to System.currentTimeMillis()
        )
        payload.putAll(extras)
        try {
            firestore.collection(CHECKPOINTS_COLLECTION)
                .add(payload)
                .addOnFailureListener { e -> Log.w(TAG, "checkpoint $name failed", e) }
        } catch (t: Throwable) { Log.w(TAG, "checkpoint submit threw", t) }
    }

    companion object {
        private const val TAG = "RemoteErrorLogger"
        private const val ERRORS_COLLECTION = "client_errors"
        private const val CHECKPOINTS_COLLECTION = "client_checkpoints"
    }
}
