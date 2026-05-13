package com.nikolaspaci.app.llamallmlocal.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

data class StoredImage(
    val relativePath: String,
    val mimeType: String,
    val sizeBytes: Long
)

data class DeleteImagesResult(
    val deletedCount: Int,
    val freedBytes: Long
)

@Singleton
class ImageStorageManager @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private val conversationsRoot: File
        get() = File(context.filesDir, CONVERSATIONS_DIR)

    private fun conversationDir(conversationId: Long): File =
        File(conversationsRoot, conversationId.toString())

    private fun imagesDir(conversationId: Long): File =
        File(conversationDir(conversationId), IMAGES_DIR)

    fun absolutePathOf(relativePath: String): File =
        File(context.filesDir, relativePath)

    suspend fun copyFromUri(uri: Uri, conversationId: Long): StoredImage = withContext(Dispatchers.IO) {
        val rawMime = context.contentResolver.getType(uri) ?: "application/octet-stream"

        val targetDir = imagesDir(conversationId).apply { mkdirs() }
        val uuid = UUID.randomUUID().toString()

        val needsReencode = rawMime.equals(MIME_WEBP, ignoreCase = true)
        val (storedMime, ext) = if (needsReencode) MIME_JPEG to "jpg" else mimeToExt(rawMime)

        val target = File(targetDir, "$uuid.$ext")

        if (needsReencode) {
            val bitmap = decodeBitmap(uri) ?: error("Impossible de décoder l'image")
            val oriented = applyExifOrientation(uri, bitmap)
            FileOutputStream(target).use { out ->
                oriented.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
            }
            if (oriented !== bitmap) bitmap.recycle()
            oriented.recycle()
        } else {
            val bounds = readBounds(uri)
            val maxDim = maxOf(bounds.outWidth, bounds.outHeight)
            if (maxDim > MAX_DIMENSION_PX && maxDim > 0) {
                val bitmap = decodeBitmap(uri, sampleSizeFor(maxDim))
                    ?: error("Impossible de décoder l'image")
                val downscaled = downscale(bitmap, MAX_DIMENSION_PX)
                val oriented = applyExifOrientation(uri, downscaled)
                FileOutputStream(target).use { out ->
                    oriented.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
                }
                if (oriented !== downscaled) downscaled.recycle()
                if (downscaled !== bitmap) bitmap.recycle()
                oriented.recycle()
                return@withContext StoredImage(
                    relativePath = relativeOf(target),
                    mimeType = MIME_JPEG,
                    sizeBytes = target.length()
                )
            }

            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(target).use { output ->
                    input.copyTo(output)
                }
            } ?: error("Flux introuvable pour l'URI")
        }

        StoredImage(
            relativePath = relativeOf(target),
            mimeType = storedMime,
            sizeBytes = target.length()
        )
    }

    suspend fun readBytes(relativePath: String): ByteArray? = withContext(Dispatchers.IO) {
        val file = absolutePathOf(relativePath)
        if (file.exists()) file.readBytes() else null
    }

    suspend fun deleteConversationDir(conversationId: Long): Boolean = withContext(Dispatchers.IO) {
        val dir = conversationDir(conversationId)
        if (!dir.exists()) return@withContext true
        dir.deleteRecursively()
    }

    suspend fun totalSizeBytes(): Long = withContext(Dispatchers.IO) {
        if (!conversationsRoot.exists()) return@withContext 0L
        conversationsRoot.walkBottomUp()
            .filter { it.isFile }
            .sumOf { it.length() }
    }

    suspend fun countImages(): Int = withContext(Dispatchers.IO) {
        if (!conversationsRoot.exists()) return@withContext 0
        conversationsRoot.walkBottomUp()
            .filter { it.isFile && it.parentFile?.name == IMAGES_DIR }
            .count()
    }

    suspend fun deleteAllImages(): DeleteImagesResult = withContext(Dispatchers.IO) {
        if (!conversationsRoot.exists()) return@withContext DeleteImagesResult(0, 0L)
        var freed = 0L
        var deleted = 0
        conversationsRoot.walkBottomUp()
            .filter { it.isFile && it.parentFile?.name == IMAGES_DIR }
            .forEach { file ->
                val size = file.length()
                if (file.delete()) {
                    freed += size
                    deleted += 1
                }
            }
        DeleteImagesResult(deleted, freed)
    }

    private fun relativeOf(file: File): String =
        file.relativeTo(context.filesDir).path

    private fun readBounds(uri: Uri): BitmapFactory.Options {
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, opts)
        }
        return opts
    }

    private fun decodeBitmap(uri: Uri, sampleSize: Int = 1): Bitmap? {
        val opts = BitmapFactory.Options().apply { inSampleSize = sampleSize }
        return context.contentResolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, opts)
        }
    }

    private fun sampleSizeFor(maxDim: Int): Int {
        var sample = 1
        var current = maxDim
        while (current / 2 >= MAX_DIMENSION_PX) {
            sample *= 2
            current /= 2
        }
        return sample
    }

    private fun downscale(bitmap: Bitmap, maxDim: Int): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        val largest = maxOf(width, height)
        if (largest <= maxDim) return bitmap
        val scale = maxDim.toFloat() / largest
        val newW = (width * scale).toInt().coerceAtLeast(1)
        val newH = (height * scale).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(bitmap, newW, newH, true)
    }

    private fun applyExifOrientation(uri: Uri, bitmap: Bitmap): Bitmap {
        val orientation = readExifOrientation(uri)
        if (orientation == ExifInterface.ORIENTATION_NORMAL ||
            orientation == ExifInterface.ORIENTATION_UNDEFINED
        ) {
            return bitmap
        }
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> { matrix.postRotate(90f); matrix.postScale(-1f, 1f) }
            ExifInterface.ORIENTATION_TRANSVERSE -> { matrix.postRotate(270f); matrix.postScale(-1f, 1f) }
            else -> return bitmap
        }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    private fun readExifOrientation(uri: Uri): Int {
        return try {
            context.contentResolver.openInputStream(uri)?.use { stream: InputStream ->
                ExifInterface(stream).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL
                )
            } ?: ExifInterface.ORIENTATION_NORMAL
        } catch (_: Exception) {
            ExifInterface.ORIENTATION_NORMAL
        }
    }

    private fun mimeToExt(mime: String): Pair<String, String> = when (mime.lowercase()) {
        MIME_JPEG, "image/jpg" -> MIME_JPEG to "jpg"
        MIME_PNG -> MIME_PNG to "png"
        MIME_GIF -> MIME_GIF to "gif"
        MIME_BMP, "image/x-ms-bmp" -> MIME_BMP to "bmp"
        else -> MIME_JPEG to "jpg"
    }

    companion object {
        const val CONVERSATIONS_DIR = "conversations"
        const val IMAGES_DIR = "images"

        private const val MAX_DIMENSION_PX = 1568
        private const val JPEG_QUALITY = 90

        private const val MIME_JPEG = "image/jpeg"
        private const val MIME_PNG = "image/png"
        private const val MIME_GIF = "image/gif"
        private const val MIME_BMP = "image/bmp"
        private const val MIME_WEBP = "image/webp"
    }
}
