package org.zenconverter.app.conversion

import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.media.MediaFormat

object VideoEncoderSupport {
    fun supportedMimeTypes(): Set<String> {
        val supportedTypes = mutableSetOf(VideoExportOptions.VIDEO_MIME_TYPE_H264)
        if (canEncode(VideoExportOptions.VIDEO_MIME_TYPE_H265)) {
            supportedTypes += VideoExportOptions.VIDEO_MIME_TYPE_H265
        }
        return supportedTypes
    }

    fun canEncode(mimeType: String): Boolean {
        if (mimeType == VideoExportOptions.VIDEO_MIME_TYPE_H264) return true
        val format = MediaFormat.createVideoFormat(mimeType, PROBE_WIDTH, PROBE_HEIGHT).apply {
            setInteger(MediaFormat.KEY_BIT_RATE, PROBE_BITRATE)
            setInteger(
                MediaFormat.KEY_COLOR_FORMAT,
                MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface
            )
            setInteger(MediaFormat.KEY_FRAME_RATE, PROBE_FRAME_RATE)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, PROBE_I_FRAME_INTERVAL)
        }
        return runCatching {
            MediaCodecList(MediaCodecList.REGULAR_CODECS).findEncoderForFormat(format) != null
        }.getOrDefault(false)
    }

    private const val PROBE_WIDTH = 1280
    private const val PROBE_HEIGHT = 720
    private const val PROBE_BITRATE = 2_500_000
    private const val PROBE_FRAME_RATE = 30
    private const val PROBE_I_FRAME_INTERVAL = 1
}
