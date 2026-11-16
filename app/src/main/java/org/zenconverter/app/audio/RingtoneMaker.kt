package org.zenconverter.app.audio

import android.content.ContentValues
import android.content.Context
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File

/**
 * Copies a trimmed audio file into the device's sounds folder and registers it as
 * a ringtone, alarm or notification sound. Kept separate from the conversion
 * service because it touches MediaStore, not the FFmpeg pipeline.
 */
object RingtoneMaker {
    enum class Slot(val folder: String, val setting: String) {
        Ringtone("Ringtones", RingtoneManager.TYPE_RINGTONE.toString()),
        Alarm("Alarms", RingtoneManager.TYPE_ALARM.toString()),
        Notification("Notifications", RingtoneManager.TYPE_NOTIFICATION.toString())
    }

    data class Result(val uri: Uri, val displayName: String)

    fun install(context: Context, source: File, displayName: String, slot: Slot): Result {
        val resolver = context.contentResolver
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        }
        val relativePath = "${Environment.DIRECTORY_RINGTONES}/${slot.folder}"
        val values = ContentValues().apply {
            put(MediaStore.Audio.Media.DISPLAY_NAME, displayName)
            put(MediaStore.Audio.Media.MIME_TYPE, "audio/mpeg")
            put(MediaStore.Audio.Media.IS_RINGTONE, slot == Slot.Ringtone)
            put(MediaStore.Audio.Media.IS_ALARM, slot == Slot.Alarm)
            put(MediaStore.Audio.Media.IS_NOTIFICATION, slot == Slot.Notification)
            put(MediaStore.Audio.Media.IS_MUSIC, false)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Audio.Media.RELATIVE_PATH, relativePath)
            }
        }
        val uri = resolver.insert(collection, values)
            ?: error("Could not register the sound")
        resolver.openOutputStream(uri).use { output ->
            requireNotNull(output) { "Could not open the sound output" }
            source.inputStream().use { input -> input.copyTo(output) }
        }
        runCatching {
            RingtoneManager.setActualDefaultRingtoneUri(
                context,
                when (slot) {
                    Slot.Ringtone -> RingtoneManager.TYPE_RINGTONE
                    Slot.Alarm -> RingtoneManager.TYPE_ALARM
                    Slot.Notification -> RingtoneManager.TYPE_NOTIFICATION
                },
                uri
            )
        }
        return Result(uri, displayName)
    }
}
