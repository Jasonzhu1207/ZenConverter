package org.zenconverter.app.audio

import android.content.Context
import android.net.Uri
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.FFmpegKitConfig
import com.arthenica.ffmpegkit.FFprobeKit
import com.arthenica.ffmpegkit.ReturnCode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

/**
 * Reads and writes common audio tags (ID3 / Vorbis / MP4 atoms) through FFmpeg,
 * which already understands every container the converter supports. Metadata
 * never leaves the device: the tag write is a local re-mux into the cache.
 */
object AudioTagEditor {
    /** Field keys map to FFmpeg metadata names so a write is a straight mapping. */
    enum class Field(val ffmpegKey: String) {
        Title("title"),
        Artist("artist"),
        Album("album"),
        AlbumArtist("album_artist"),
        Track("track"),
        Year("date"),
        Genre("genre"),
        Comment("comment"),
        Lyrics("lyrics"),
        Composer("composer"),
        InitialKey("initial_key"),
        Bpm("bpm"),
        Language("language")
    }

    data class Tags(
        val title: String = "",
        val artist: String = "",
        val album: String = "",
        val albumArtist: String = "",
        val track: String = "",
        val year: String = "",
        val genre: String = "",
        val comment: String = "",
        val lyrics: String = "",
        val composer: String = "",
        val initialKey: String = "",
        val bpm: String = "",
        val language: String = "",
        val hasArtwork: Boolean = false
    ) {
        fun valueOf(field: Field): String = when (field) {
            Field.Title -> title
            Field.Artist -> artist
            Field.Album -> album
            Field.AlbumArtist -> albumArtist
            Field.Track -> track
            Field.Year -> year
            Field.Genre -> genre
            Field.Comment -> comment
            Field.Lyrics -> lyrics
            Field.Composer -> composer
            Field.InitialKey -> initialKey
            Field.Bpm -> bpm
            Field.Language -> language
        }

        fun with(field: Field, value: String): Tags = when (field) {
            Field.Title -> copy(title = value)
            Field.Artist -> copy(artist = value)
            Field.Album -> copy(album = value)
            Field.AlbumArtist -> copy(albumArtist = value)
            Field.Track -> copy(track = value)
            Field.Year -> copy(year = value)
            Field.Genre -> copy(genre = value)
            Field.Comment -> copy(comment = value)
            Field.Lyrics -> copy(lyrics = value)
            Field.Composer -> copy(composer = value)
            Field.InitialKey -> copy(initialKey = value)
            Field.Bpm -> copy(bpm = value)
            Field.Language -> copy(language = value)
        }
    }

    suspend fun read(context: Context, uri: Uri): Tags? = withContext(Dispatchers.IO) {
        val path = inputPathFor(context, uri) ?: return@withContext null
        val information = runCatching {
            FFprobeKit.getMediaInformation(path, PROBE_TIMEOUT_MS)?.getMediaInformation()
        }.getOrNull() ?: return@withContext null
        val streams = information.getStreams().orEmpty()
        val audio = streams.firstOrNull { it.getType().equals("audio", true) }
        val tags = mutableMapOf<String, String>()
        listOfNotNull(information.getTags(), audio?.getTags()).forEach { bag ->
            val keys = bag.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                val value = bag.optString(key)
                if (value.isNotBlank()) tags[key.lowercase(Locale.US)] = value
            }
        }
        Tags(
            title = tags["title"].orEmpty(),
            artist = tags["artist"].orEmpty(),
            album = tags["album"].orEmpty(),
            albumArtist = tags["album_artist"] ?: tags["albumartist"].orEmpty(),
            track = tags["track"].orEmpty(),
            year = tags["date"] ?: tags["year"].orEmpty(),
            genre = tags["genre"].orEmpty(),
            comment = tags["comment"].orEmpty(),
            lyrics = tags["lyrics"] ?: tags["unsyncedlyrics"].orEmpty(),
            composer = tags["composer"].orEmpty(),
            initialKey = tags["initial_key"] ?: tags["tkey"].orEmpty(),
            bpm = tags["bpm"] ?: tags["tbpm"].orEmpty(),
            language = tags["language"] ?: tags["lang"].orEmpty(),
            hasArtwork = streams.any { stream ->
                if (stream.getType().equals("video", true)) return@any true
                val streamTags = stream.getTags() ?: return@any false
                val keys = streamTags.keys()
                var found = false
                while (keys.hasNext()) {
                    if (keys.next().contains("picture", ignoreCase = true)) {
                        found = true
                        break
                    }
                }
                found
            }
        )
    }

    /**
     * Writes [tags] by re-muxing the streams without re-encoding, which keeps the
     * original audio quality. Artwork can be attached from [artworkUri].
     */
    suspend fun write(
        context: Context,
        sourceUri: Uri,
        tags: Tags,
        artworkUri: Uri?,
        target: File
    ): Boolean = withContext(Dispatchers.IO) {
        val input = inputPathFor(context, sourceUri) ?: return@withContext false
        val arguments = mutableListOf(
            "-hide_banner", "-nostdin", "-i", input
        )
        if (artworkUri != null) {
            val artworkPath = inputPathFor(context, artworkUri)
            if (artworkPath != null) {
                arguments += listOf("-i", artworkPath)
            }
        }
        arguments += listOf("-map", "0:a", "-c:a", "copy")
        if (artworkUri != null) {
            arguments += listOf(
                "-map", "1:v",
                "-c:v", "mjpeg",
                "-disposition:v", "attached_pic",
                "-metadata:s:v", "title=Album cover",
                "-metadata:s:v", "comment=Cover (front)"
            )
        }
        Field.entries.forEach { field ->
            val value = tags.valueOf(field)
            if (value.isNotBlank()) {
                arguments += listOf("-metadata", "${field.ffmpegKey}=$value")
            }
        }
        arguments += listOf("-y", target.absolutePath)

        val session = FFmpegKit.executeWithArguments(arguments.toTypedArray())
        ReturnCode.isSuccess(session.getReturnCode())
    }

    /**
     * Removes every metadata field (and any attached artwork) by re-muxing the
     * streams with `-map_metadata -1`. Audio is copied, so quality is untouched.
     */
    suspend fun stripAll(context: Context, sourceUri: Uri, target: File): Boolean =
        withContext(Dispatchers.IO) {
            val input = inputPathFor(context, sourceUri) ?: return@withContext false
            val arguments = arrayOf(
                "-hide_banner", "-nostdin", "-i", input,
                "-map", "0:a", "-c:a", "copy",
                "-map_metadata", "-1",
                "-y", target.absolutePath
            )
            val session = FFmpegKit.executeWithArguments(arguments)
            ReturnCode.isSuccess(session.getReturnCode())
        }

    /** Expands a rename pattern like `%artist% - %title%` into a file name. */
    fun resolveNamePattern(pattern: String, tags: Tags, fallback: String): String {
        val resolved = pattern
            .replace("%artist%", tags.artist.ifBlank { "Unknown artist" })
            .replace("%album%", tags.album.ifBlank { "Unknown album" })
            .replace("%albumartist%", tags.albumArtist.ifBlank { tags.artist.ifBlank { "Unknown artist" } })
            .replace("%title%", tags.title.ifBlank { fallback })
            .replace("%track%", tags.track.ifBlank { "0" })
            .replace("%year%", tags.year.ifBlank { "0000" })
            .replace("%genre%", tags.genre)
            .replace("%composer%", tags.composer)
        return resolved.replace(Regex("[\\\\/:*?\"<>|]"), "_").trim().ifBlank { fallback }
    }

    private fun inputPathFor(context: Context, uri: Uri): String? {
        return runCatching {
            FFmpegKitConfig.getSafParameterForRead(context, uri)
        }.getOrNull() ?: runCatching {
            "/proc/self/fd/${context.contentResolver.openFileDescriptor(uri, "r")?.fd}"
        }.getOrNull()
    }

    private const val PROBE_TIMEOUT_MS = 10_000
}
