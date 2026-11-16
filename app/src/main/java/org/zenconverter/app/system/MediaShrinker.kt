package org.zenconverter.app.system

import android.content.Context
import android.net.Uri
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.FFmpegKitConfig
import com.arthenica.ffmpegkit.ReturnCode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Re-encodes a large video into a smaller one so the storage cleaner can offer
 * "compress and keep the space". Runs entirely on the device through FFmpeg and
 * writes to a caller-owned temp file; the UI decides the final destination.
 */
object MediaShrinker {
    data class Shrink(
        val output: File,
        val inputBytes: Long,
        val outputBytes: Long
    ) {
        val savedBytes: Long get() = (inputBytes - outputBytes).coerceAtLeast(0)
    }

    suspend fun shrinkVideo(
        context: Context,
        sourceUri: Uri,
        target: File,
        inputBytes: Long,
        maxShortSidePixels: Int = 720,
        crf: Int = 28
    ): Shrink? = withContext(Dispatchers.IO) {
        val input = inputPathFor(context, sourceUri) ?: return@withContext null
        val arguments = arrayOf(
            "-hide_banner", "-nostdin",
            "-i", input,
            "-vf", "scale='if(gt(a,1),-2,$maxShortSidePixels)':'if(gt(a,1),$maxShortSidePixels,-2)'",
            "-c:v", "libx264", "-preset", "veryfast", "-crf", "$crf",
            "-c:a", "aac", "-b:a", "128k",
            "-movflags", "+faststart",
            "-y", target.absolutePath
        )
        val success = ReturnCode.isSuccess(
            FFmpegKit.executeWithArguments(arguments).getReturnCode()
        )
        if (!success || !target.isFile || target.length() <= 0L) return@withContext null
        Shrink(target, inputBytes, target.length())
    }

    private fun inputPathFor(context: Context, uri: Uri): String? = runCatching {
        FFmpegKitConfig.getSafParameterForRead(context, uri)
    }.getOrNull() ?: runCatching {
        "/proc/self/fd/${context.contentResolver.openFileDescriptor(uri, "r")?.fd}"
    }.getOrNull()
}
