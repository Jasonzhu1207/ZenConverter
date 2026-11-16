package org.zenconverter.app.system

import org.junit.Assert.assertEquals
import org.junit.Test

class MediaKindsTest {

    @Test
    fun mimeTypeWinsWhenPresent() {
        assertEquals(MediaKinds.Kind.Audio, MediaKinds.of("audio/mpeg", "track.bin"))
        assertEquals(MediaKinds.Kind.Video, MediaKinds.of("video/mp4", "clip.bin"))
        assertEquals(MediaKinds.Kind.Image, MediaKinds.of("image/webp", "shot.bin"))
        assertEquals(MediaKinds.Kind.Document, MediaKinds.of("text/plain", "notes.bin"))
    }

    @Test
    fun extensionIsTheFallbackWhenMimeIsMissing() {
        assertEquals(MediaKinds.Kind.Audio, MediaKinds.of(null, "song.FLAC"))
        assertEquals(MediaKinds.Kind.Video, MediaKinds.of(null, "movie.mkv"))
        assertEquals(MediaKinds.Kind.Image, MediaKinds.of(null, "photo.HEIC"))
        assertEquals(MediaKinds.Kind.Document, MediaKinds.of(null, "book.epub"))
    }

    @Test
    fun extensionWinsForOpaqueOctetStreamMime() {
        // MediaStore reports many side-loaded files as octet-stream, so the
        // extension has to be trusted or they all land in Other.
        assertEquals(MediaKinds.Kind.Audio, MediaKinds.of("application/octet-stream", "mix.wav"))
        assertEquals(MediaKinds.Kind.Video, MediaKinds.of("application/octet-stream", "reel.webm"))
    }

    @Test
    fun unknownFilesLandInOther() {
        assertEquals(MediaKinds.Kind.Other, MediaKinds.of(null, "no-extension"))
        assertEquals(MediaKinds.Kind.Other, MediaKinds.of("application/zipx", "blob.xyz"))
    }

    @Test
    fun archivesAreDocuments() {
        assertEquals(MediaKinds.Kind.Document, MediaKinds.of("application/zip", "bundle.zip"))
        assertEquals(MediaKinds.Kind.Document, MediaKinds.of(null, "backup.7z"))
    }
}
