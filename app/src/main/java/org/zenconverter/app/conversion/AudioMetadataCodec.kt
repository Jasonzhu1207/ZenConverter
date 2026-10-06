package org.zenconverter.app.conversion

import java.io.ByteArrayOutputStream
import java.io.EOFException
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.io.PushbackInputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.Base64
import java.util.Locale

internal object AudioMetadataCodec {
    const val MAX_COVER_BYTES = 16L * 1024 * 1024
    const val MAX_LYRICS_BYTES = 256L * 1024
    private const val MAX_METADATA_BYTES = 64 * 1024 * 1024
    private const val MAX_TEXT_BYTES = 64 * 1024

    data class AudioLyrics(
        val text: String,
        val language: String = "und",
        val description: String = "",
        val sourceKey: String = "lyrics"
    ) {
        val commentKey: String
            get() = if (language == "und" && description.isEmpty()) "lyrics" else
                "lyrics-${if (description.isEmpty()) "" else "$description-"}$language"
    }

    data class AudioCover(
        val bytes: ByteArray,
        val mimeType: String,
        val width: Int,
        val height: Int,
        val sha256: String,
        val depth: Int
    )

    data class AudioMetadataSnapshot(
        val fields: Map<String, String> = emptyMap(),
        val lyrics: AudioLyrics? = null,
        val cover: AudioCover? = null,
        val pictureStreamIndex: Long? = null,
        val flacStreamInfo: ByteArray? = null
    ) {
        val hasRequiredMetadata: Boolean
            get() = fields.isNotEmpty() || lyrics != null || cover != null
    }

    data class Verification(val success: Boolean, val diagnostic: String? = null)
    private data class Frame(val id: String, val flags: Int, val payload: ByteArray)
    private data class Id3(val version: Int, val audioOffset: Long, val frames: List<Frame>, val padding: Int)
    private data class Picture(val type: Int, val mime: String, val bytes: ByteArray, val width: Int = 0, val height: Int = 0)
    private data class Flac(val tags: List<Pair<String, String>>, val pictures: List<Picture>, val streamInfo: ByteArray)

    private val aliases = mapOf(
        "title" to "title", "artist" to "artist", "performer" to "artist", "album" to "album",
        "album_artist" to "album_artist", "albumartist" to "album_artist", "album-artist" to "album_artist",
        "album artist" to "album_artist", "track" to "track", "tracknumber" to "track",
        "track_number" to "track", "disc" to "disc", "discnumber" to "disc", "disc_number" to "disc",
        "date" to "date", "year" to "date", "genre" to "genre", "composer" to "composer",
        "comment" to "comment", "description" to "comment", "copyright" to "copyright",
        "publisher" to "publisher", "organization" to "publisher", "label" to "publisher"
    )
    private val frameFields = mapOf(
        "TIT2" to "title", "TPE1" to "artist", "TALB" to "album", "TPE2" to "album_artist",
        "TRCK" to "track", "TPOS" to "disc", "TDRC" to "date", "TYER" to "date",
        "TCON" to "genre", "TCOM" to "composer", "TCOP" to "copyright", "TPUB" to "publisher"
    )

    fun snapshotFromTags(tags: List<Pair<String, String>>): AudioMetadataSnapshot {
        val fields = linkedMapOf<String, String>()
        tags.forEach { (key, value) ->
            aliases[key.lowercase(Locale.ROOT)]?.let { field ->
                if (value.isNotBlank() && field !in fields) {
                    require(value.toByteArray(Charsets.UTF_8).size <= MAX_TEXT_BYTES) { "text-limit:$field" }
                    fields[field] = value
                }
            }
        }
        val lyric = tags.filter { isLyricsKey(it.first) && it.second.isNotBlank() }
            .sortedBy { if (it.first.startsWith("lyrics-", true)) 0 else 1 }
            .firstOrNull()?.let { (key, value) ->
                require(value.toByteArray(Charsets.UTF_8).size <= MAX_LYRICS_BYTES) { "lyrics-limit" }
                val normalized = key.lowercase(Locale.ROOT)
                val suffix = if (normalized.startsWith("lyrics-")) key.substring(7) else ""
                val candidate = suffix.substringAfterLast('-')
                val lang = candidate.lowercase(Locale.ROOT).takeIf { it.length == 3 && it.all { c -> c in 'a'..'z' } } ?: "und"
                val description = if (lang == "und" && candidate != "und") suffix else suffix.removeSuffix(candidate).trimEnd('-')
                AudioLyrics(value, lang, description, normalized)
            }
        return AudioMetadataSnapshot(fields, lyric)
    }

    fun coverFromBytes(bytes: ByteArray, hintedMimeType: String? = null): AudioCover? = runCatching {
        require(bytes.isNotEmpty() && bytes.size <= MAX_COVER_BYTES) { "cover-limit" }
        val mime: String
        val width: Int
        val height: Int
        val depth: Int
        if (bytes.hasMagicBytes(byteArrayOf(137.toByte(), 80, 78, 71, 13, 10, 26, 10))) {
            require(bytes.size >= 45 && be(bytes, 8) == 13 && String(bytes, 12, 4, Charsets.US_ASCII) == "IHDR") { "png-header" }
            mime = "image/png"
            width = be(bytes, 16)
            height = be(bytes, 20)
            depth = u(bytes[24]) * when (u(bytes[25])) { 0, 3 -> 1; 2 -> 3; 4 -> 2; 6 -> 4; else -> error("png-color") }
        } else {
            require(bytes.size >= 12 && u(bytes[0]) == 255 && u(bytes[1]) == 216) { "jpeg-header" }
            mime = "image/jpeg"
            var dimensions: Triple<Int, Int, Int>? = null
            var offset = 2
            while (offset + 4 <= bytes.size) {
                require(u(bytes[offset]) == 255) { "jpeg-marker" }
                while (offset < bytes.size && u(bytes[offset]) == 255) offset++
                require(offset + 2 <= bytes.size) { "jpeg-truncated" }
                val marker = u(bytes[offset++])
                if (marker == 218 || marker == 217) break
                if (marker == 1 || marker in 208..215) continue
                val length = (u(bytes[offset]) shl 8) or u(bytes[offset + 1])
                require(length >= 2 && length <= bytes.size - offset) { "jpeg-length" }
                if (marker in listOf(192, 193, 194, 195, 197, 198, 199, 201, 202, 203, 205, 206, 207)) {
                    require(length >= 8) { "jpeg-sof" }
                    val h = (u(bytes[offset + 3]) shl 8) or u(bytes[offset + 4])
                    val w = (u(bytes[offset + 5]) shl 8) or u(bytes[offset + 6])
                    dimensions = Triple(w, h, u(bytes[offset + 2]) * u(bytes[offset + 7]))
                }
                offset += length
            }
            val info = dimensions ?: error("jpeg-sof-missing")
            width = info.first
            height = info.second
            depth = info.third
        }
        require(width > 0 && height > 0 && depth > 0) { "cover-dimensions" }
        require(hintedMimeType.isNullOrBlank() || hintedMimeType.equals(mime, true) ||
            (mime == "image/jpeg" && hintedMimeType.equals("image/jpg", true))) { "cover-mime" }
        AudioCover(bytes, mime, width, height, hash(bytes), depth)
    }.getOrNull()

    fun pictureBlock(cover: AudioCover): ByteArray = ByteArrayOutputStream().apply {
        putBe(3)
        putString(cover.mimeType)
        putString("")
        putBe(cover.width)
        putBe(cover.height)
        putBe(cover.depth)
        putBe(0)
        putBe(cover.bytes.size)
        write(cover.bytes)
    }.toByteArray()

    fun opusPictureMetadata(cover: AudioCover): String = Base64.getEncoder().encodeToString(pictureBlock(cover))

    fun verifyMp3(file: File, snapshot: AudioMetadataSnapshot): Verification = checked("mp3-verify") {
        val id3 = file.inputStream().buffered().use { readId3(it) }
        if (id3 == null) {
            require(!snapshot.hasRequiredMetadata) { "missing-id3" }
        } else {
            verifySnapshot(snapshotFromId3(id3), snapshot)
            if (snapshot.lyrics != null) {
                require(id3.frames.any { it.id == "USLT" && runCatching { parseLyrics(it.payload) }.getOrNull()?.text == snapshot.lyrics.text }) { "missing-uslt" }
                require(id3.frames.none { it.id == "TXXX" && isLegacyLyricsTxxx(it.payload) }) { "invalid-txxx-uslt" }
            }
        }
    }

    fun repairAndVerifyMp3(file: File, snapshot: AudioMetadataSnapshot, cancelled: () -> Boolean = { false }): Verification = checked("mp3-repair") {
        val existing = file.inputStream().buffered().use { readId3(it) }
        val version = existing?.version ?: 4
        val frames = existing?.frames.orEmpty().toMutableList()
        frames.removeAll { it.id == "TXXX" && isLegacyLyricsTxxx(it.payload) }
        snapshot.fields.forEach { (field, value) ->
            val frameId = if (field == "comment") "COMM" else frameFields.entries.firstOrNull { it.value == field }?.key ?: error("unsupported-field:$field")
            val id = if (version == 3 && frameId == "TDRC") "TYER" else frameId
            frames.removeAll { frameFields[it.id] == field || (field == "comment" && it.id == "COMM") }
            val payload = if (id == "COMM") byteArrayOf(1) + "und".toByteArray() + utf16("") + byteArrayOf(0, 0) + utf16(value)
                else byteArrayOf(1) + utf16(value)
            frames += Frame(id, 0, payload)
        }
        snapshot.lyrics?.let { lyrics ->
            frames.removeAll { it.id == "USLT" }
            val language = lyrics.language.takeIf { it.length == 3 && it.all { c -> c in 'a'..'z' } } ?: "und"
            frames += Frame("USLT", 0, byteArrayOf(1) + language.toByteArray(Charsets.US_ASCII) + utf16(lyrics.description) + byteArrayOf(0, 0) + utf16(lyrics.text))
        }
        snapshot.cover?.let { cover ->
            // The validated source cover is authoritative. Replace all output
            // APIC frames so a stale or malformed encoder copy cannot survive.
            frames.removeAll { it.id == "APIC" }
            frames += Frame("APIC", 0, byteArrayOf(0) + cover.mimeType.toByteArray(Charsets.ISO_8859_1) +
                byteArrayOf(0, 3, 0) + cover.bytes)
        }
        if (!snapshot.hasRequiredMetadata && existing == null) return@checked
        if (!snapshot.hasRequiredMetadata && existing != null && frames == existing.frames) return@checked
        val body = ByteArrayOutputStream()
        frames.forEach { frame ->
            body.write(frame.id.toByteArray(Charsets.US_ASCII))
            if (version == 4) body.putSynch(frame.payload.size) else body.putBe(frame.payload.size)
            body.write(frame.flags ushr 8)
            body.write(frame.flags and 255)
            body.write(frame.payload)
        }
        require(body.size() + 1024 <= MAX_METADATA_BYTES) { "id3-limit" }
        body.write(ByteArray(existing?.padding?.coerceAtLeast(1024) ?: 1024))
        require(body.size() <= MAX_METADATA_BYTES) { "id3-limit" }
        val temp = File.createTempFile("audio-id3-", ".tmp", file.parentFile)
        try {
            val copiedDigest = MessageDigest.getInstance("SHA-256")
            temp.outputStream().buffered().use { output ->
                output.write(byteArrayOf(73, 68, 51, version.toByte(), 0, 0))
                output.putSynch(body.size())
                output.write(body.toByteArray())
                file.inputStream().buffered().use { input ->
                    input.skipExactly(existing?.audioOffset ?: 0L)
                    transfer(input, output, copiedDigest, cancelled)
                }
            }
            val readDigest = temp.inputStream().buffered().use { input ->
                input.skipExactly(10L + body.size())
                digestRest(input, cancelled)
            }
            require(copiedDigest.digest().contentEquals(readDigest)) { "audio-payload-changed" }
            val verification = verifyMp3(temp, snapshot)
            require(verification.success) { verification.diagnostic ?: "id3-verification" }
            checkCancelled(cancelled)
            replaceVerified(temp, file)
        } finally {
            temp.delete()
        }
    }

    fun verifyOpus(file: File, snapshot: AudioMetadataSnapshot): Verification = checked("opus-verify") {
        val packets = mutableListOf<ByteArray>()
        file.inputStream().buffered().use { input -> walkOgg(input, false) { packet, index -> packets += packet; index < 1 } }
        validateOpusHead(packets.firstOrNull() ?: error("missing-opus-head"))
        val tagsPacket = packets.getOrNull(1)
        if (tagsPacket == null) {
            require(!snapshot.hasRequiredMetadata) { "missing-opus-tags" }
            return@checked
        }
        val comments = readOpusTags(tagsPacket)
        verifySnapshot(snapshotFromTags(comments).copy(cover = matchingCover(picturesFromComments(comments), snapshot.cover)), snapshot)
    }

    fun verifyFlac(file: File, snapshot: AudioMetadataSnapshot): Verification = checked("flac-verify") {
        val flac = file.inputStream().buffered().use { readFlac(it) }
        verifySnapshot(snapshotFromTags(flac.tags).copy(cover = matchingCover(flac.pictures, snapshot.cover)), snapshot)
    }

    fun audioFingerprint(file: File, target: String, cancelled: () -> Boolean = { false }): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            when (target) {
                "mp3" -> {
                    readId3(input)
                    transfer(input, null, digest, cancelled)
                }
                "flac" -> {
                    val flac = readFlac(input)
                    digest.update(flac.streamInfo)
                    transfer(input, null, digest, cancelled)
                }
                "opus" -> {
                    var audioPackets = 0
                    val granule = walkOgg(input, true) { packet, index ->
                        checkCancelled(cancelled)
                        if (index == 0) validateOpusHead(packet)
                        if (index != 1) {
                            digest.update(byteArrayOf((packet.size ushr 24).toByte(), (packet.size ushr 16).toByte(), (packet.size ushr 8).toByte(), packet.size.toByte()))
                            digest.update(packet)
                        }
                        if (index > 1) audioPackets++
                        true
                    }
                    require(audioPackets > 0) { "missing-opus-audio" }
                    digest.update(granule)
                }
                else -> error("unsupported-audio-hash")
            }
        }
        return hex(digest.digest())
    }

    fun verifyFlacPcm(file: File, sourceStreamInfo: ByteArray): Verification = checked("flac-pcm") {
        val actual = file.inputStream().buffered().use { readFlac(it).streamInfo }
        require(actual.copyOfRange(10, 18).contentEquals(sourceStreamInfo.copyOfRange(10, 18))) { "flac-pcm-properties-changed" }
        val sourceMd5 = sourceStreamInfo.copyOfRange(18, 34)
        if (sourceMd5.any { it != 0.toByte() }) require(actual.copyOfRange(18, 34).contentEquals(sourceMd5)) { "flac-pcm-md5-changed" }
    }

    fun flacSampleRate(info: ByteArray): Int = (u(info[10]) shl 12) or (u(info[11]) shl 4) or (u(info[12]) ushr 4)
    fun flacChannels(info: ByteArray): Int = ((u(info[12]) ushr 1) and 7) + 1
    fun flacBitDepth(info: ByteArray): Int = (((u(info[12]) and 1) shl 4) or (u(info[13]) ushr 4)) + 1

    fun replaceVerified(temp: File, destination: File) {
        Files.move(temp.toPath(), destination.toPath(), StandardCopyOption.REPLACE_EXISTING)
    }

    private fun verifySnapshot(actual: AudioMetadataSnapshot, expected: AudioMetadataSnapshot) {
        expected.fields.forEach { (key, value) -> require(actual.fields[key] == value) { "field-mismatch:$key" } }
        expected.lyrics?.let { lyrics ->
            require(actual.lyrics?.text == lyrics.text) { "lyrics-mismatch" }
            require(actual.lyrics.language == lyrics.language && actual.lyrics.description == lyrics.description) { "lyrics-qualifier-mismatch" }
        }
        expected.cover?.let { cover ->
            require(actual.cover?.sha256 == cover.sha256 && actual.cover.mimeType == cover.mimeType && actual.cover.width == cover.width && actual.cover.height == cover.height) { "cover-mismatch" }
        }
    }

    private fun snapshotFromId3(id3: Id3): AudioMetadataSnapshot {
        val tags = mutableListOf<Pair<String, String>>()
        val pictures = mutableListOf<Picture>()
        var lyric: AudioLyrics? = null
        id3.frames.forEach { frame ->
            when (frame.id) {
                "USLT" -> if (lyric == null) lyric = parseLyrics(frame.payload)
                "APIC" -> pictures += parseApic(frame.payload)
                "COMM" -> runCatching { parsePair(frame.payload, 4) }.getOrNull()?.let { (_, text) -> tags += "comment" to text }
                "TXXX" -> runCatching { parsePair(frame.payload) }.getOrNull()?.let { tags += it }
                else -> frameFields[frame.id]?.let {
                    require(frame.payload.isNotEmpty()) { "empty-id3-text" }
                    tags += it to decode(frame.payload, 1, frame.payload.size, u(frame.payload[0]))
                }
            }
        }
        val snapshot = snapshotFromTags(tags)
        return snapshot.copy(lyrics = lyric ?: snapshot.lyrics, cover = primaryCover(pictures))
    }

    private fun primaryCover(pictures: List<Picture>): AudioCover? {
        if (pictures.isEmpty()) return null
        return checkedCover(pictures.firstOrNull { it.type == 3 } ?: pictures.first())
    }
    private fun matchingCover(pictures: List<Picture>, expected: AudioCover?): AudioCover? {
        if (expected == null) return null
        return pictures.firstOrNull { hash(it.bytes) == expected.sha256 }?.let { checkedCover(it) }
    }
    private fun checkedCover(picture: Picture): AudioCover {
        val cover = coverFromBytes(picture.bytes, picture.mime) ?: error("unsupported-or-invalid-cover")
        require((picture.width == 0 || picture.width == cover.width) && (picture.height == 0 || picture.height == cover.height)) { "picture-dimensions-mismatch" }
        return cover
    }

    /** Reads only metadata headers of the three native music containers. */
    fun readSource(raw: InputStream): AudioMetadataSnapshot? {
        val input = PushbackInputStream(raw, 4)
        val magic = ByteArray(4)
        var count = 0
        while (count < magic.size) {
            val n = input.read(magic, count, magic.size - count)
            if (n < 0) break
            count += n
        }
        input.unread(magic, 0, count)
        return when {
            count >= 3 && String(magic, 0, 3, Charsets.US_ASCII) == "ID3" -> snapshotFromId3(readId3(input)!!)
            magic.hasMagic("fLaC") -> {
                val flac = readFlac(input)
                snapshotFromTags(flac.tags).copy(cover = primaryCover(flac.pictures), flacStreamInfo = flac.streamInfo)
            }
            magic.hasMagic("OggS") -> {
                val packets = mutableListOf<ByteArray>()
                walkOgg(input, false) { packet, index -> packets += packet; index < 1 }
                if (!packets.firstOrNull().hasMagic("OpusHead")) return null
                validateOpusHead(packets.first())
                val comments = packets.getOrNull(1)?.let(::readOpusTags).orEmpty()
                snapshotFromTags(comments).copy(cover = primaryCover(picturesFromComments(comments)))
            }
            else -> null
        }
    }

    private fun readId3(input: InputStream): Id3? {
        if (input.markSupported()) input.mark(10)
        val header = input.takeExact(10)
        if (!header.hasMagic("ID3")) {
            if (input.markSupported()) input.reset()
            return null
        }
        val version = u(header[3]); require(version in 3..4) { "unsupported-id3-version" }
        val flags = u(header[5])
        require(flags and (if (version == 4) 0x0f else 0x1f) == 0) { "id3-flags" }
        val size = synch(header, 6)
        require(size <= MAX_METADATA_BYTES) { "id3-limit" }
        val stored = input.takeExact(size)
        val body = if (flags and 0x80 != 0 && version == 3) deunsync(stored) else stored
        var offset = 0
        if (flags and 0x40 != 0) {
            val ext = if (version == 4) synch(body, 0) else be(body, 0) + 4
            require(ext in 4..body.size) { "id3-extended-header" }; offset = ext
        }
        val frames = mutableListOf<Frame>()
        while (offset < body.size && body[offset] != 0.toByte()) {
            require(body.size - offset >= 10) { "id3-truncated-frame" }
            val id = String(body, offset, 4, Charsets.US_ASCII)
            require(id.all { it in 'A'..'Z' || it in '0'..'9' }) { "id3-frame-id" }
            val length = if (version == 4) synch(body, offset + 4) else be(body, offset + 4)
            require(length in 0..(body.size - offset - 10)) { "id3-frame-size" }
            var frameFlags = (u(body[offset + 8]) shl 8) or u(body[offset + 9])
            var payload = body.copyOfRange(offset + 10, offset + 10 + length)
            require(frameFlags and (if (version == 4) 0x4c else 0xe0) == 0) { "unsupported-id3-frame-flags:$id" }
            if (version == 4) {
                if (frameFlags and 2 != 0 || flags and 0x80 != 0) payload = deunsync(payload)
                if (frameFlags and 1 != 0) { require(payload.size >= 4) { "id3-data-length" }; payload = payload.copyOfRange(4, payload.size) }
                frameFlags = frameFlags and 0xff00
            }
            frames += Frame(id, frameFlags, payload)
            offset += 10 + length
        }
        require((offset until body.size).all { body[it] == 0.toByte() }) { "id3-padding" }
        var footerSize = 0
        if (version == 4 && flags and 0x10 != 0) {
            require(input.takeExact(10).hasMagic("3DI")) { "id3-footer" }; footerSize = 10
        }
        return Id3(version, 10L + size + footerSize, frames, body.size - offset)
    }

    private fun deunsync(bytes: ByteArray): ByteArray = ByteArrayOutputStream().apply {
        var i = 0
        while (i < bytes.size) {
            write(u(bytes[i]))
            if (u(bytes[i]) == 255 && i + 1 < bytes.size && bytes[i + 1] == 0.toByte()) i++
            i++
        }
    }.toByteArray()
    private fun parsePair(payload: ByteArray, start: Int = 1): Pair<String, String> {
        require(payload.isNotEmpty()) { "empty-id3-text" }
        val encoding = u(payload[0]); val (description, next) = terminated(payload, start, encoding)
        return description to decode(payload, next, payload.size, encoding, payload, start)
    }
    private fun parseLyrics(payload: ByteArray): AudioLyrics {
        require(payload.size >= 5) { "invalid-uslt" }
        val (description, text) = parsePair(payload, 4)
        require(description.toByteArray(Charsets.UTF_8).size <= MAX_TEXT_BYTES) { "lyrics-description-limit" }
        require(text.toByteArray(Charsets.UTF_8).size <= MAX_LYRICS_BYTES) { "lyrics-limit" }
        val lang = String(payload, 1, 3, Charsets.US_ASCII).lowercase(Locale.ROOT)
            .takeIf { it.length == 3 && it.all { c -> c in 'a'..'z' } } ?: "und"
        return AudioLyrics(text, lang, description)
    }
    private fun parseApic(payload: ByteArray): Picture {
        require(payload.size >= 5) { "invalid-apic" }
        val mimeEnd = (1 until payload.size).firstOrNull { payload[it] == 0.toByte() } ?: error("apic-mime")
        require(mimeEnd + 2 <= payload.size) { "apic-type" }
        val mime = String(payload, 1, mimeEnd - 1, Charsets.ISO_8859_1)
        val (_, data) = terminated(payload, mimeEnd + 2, u(payload[0]))
        return Picture(u(payload[mimeEnd + 1]), mime, payload.copyOfRange(data, payload.size))
    }
    private fun terminated(bytes: ByteArray, start: Int, encoding: Int): Pair<String, Int> {
        require(start <= bytes.size) { "id3-text-offset" }
        val step = if (encoding == 1 || encoding == 2) 2 else 1
        var end = start
        while (end + step <= bytes.size) {
            if ((0 until step).all { bytes[end + it] == 0.toByte() }) return decode(bytes, start, end, encoding) to (end + step)
            end += step
        }
        error("id3-missing-text-terminator")
    }
    private fun decode(bytes: ByteArray, start: Int, end: Int, encoding: Int, reference: ByteArray = bytes, referenceStart: Int = start): String {
        require(start <= end && end <= bytes.size) { "id3-text-range" }
        val charset = when (encoding) {
            0 -> Charsets.ISO_8859_1
            1 -> {
                val hasBom = end - start >= 2 && ((u(bytes[start]) == 255 && u(bytes[start + 1]) == 254) || (u(bytes[start]) == 254 && u(bytes[start + 1]) == 255))
                val littleEndian = if (hasBom) u(bytes[start]) == 255 else
                    referenceStart + 1 < reference.size && u(reference[referenceStart]) == 255 && u(reference[referenceStart + 1]) == 254
                if (littleEndian) Charsets.UTF_16LE else Charsets.UTF_16BE
            }
            2 -> Charsets.UTF_16BE
            3 -> Charsets.UTF_8
            else -> error("id3-text-encoding")
        }
        val text = String(bytes, start, end - start, charset).removePrefix("\uFEFF").trimEnd('\u0000')
        require(text.toByteArray(Charsets.UTF_8).size <= MAX_METADATA_BYTES) { "id3-text-limit" }
        return text
    }
    private fun utf16(value: String): ByteArray = byteArrayOf(255.toByte(), 254.toByte()) + value.toByteArray(Charsets.UTF_16LE)

    private fun readOpusTags(packet: ByteArray): List<Pair<String, String>> {
        require(packet.hasMagic("OpusTags")) { "invalid-opus-tags" }
        return readComments(packet, 8)
    }
    private fun readComments(bytes: ByteArray, start: Int = 0): List<Pair<String, String>> {
        val cursor = Cursor(bytes, start); cursor.blobLe()
        val count = cursor.le(); require(count in 0..100_000 && count <= (bytes.size - cursor.offset) / 4) { "comment-count" }
        val tags = mutableListOf<Pair<String, String>>()
        repeat(count) {
            val text = String(cursor.blobLe(), Charsets.UTF_8); val equals = text.indexOf('=')
            require(equals > 0) { "invalid-comment" }; tags += text.substring(0, equals) to text.substring(equals + 1)
        }
        return tags
    }
    private fun picturesFromComments(comments: List<Pair<String, String>>): List<Picture> = comments
        .filter { it.first.equals("METADATA_BLOCK_PICTURE", true) }
        .map { (_, value) -> require(value.length <= MAX_METADATA_BYTES) { "picture-base64-limit" }; readPicture(Base64.getDecoder().decode(value)) }
    private fun readPicture(bytes: ByteArray): Picture {
        val c = Cursor(bytes); val type = c.be(); val mime = String(c.blobBe(), Charsets.UTF_8)
        c.blobBe(); val width = c.be(); val height = c.be(); c.be(); c.be(); val data = c.blobBe()
        require(c.offset == bytes.size && data.size <= MAX_COVER_BYTES) { "picture-size" }
        return Picture(type, mime, data, width, height)
    }

    private fun readFlac(input: InputStream): Flac {
        require(input.takeExact(4).hasMagic("fLaC")) { "invalid-flac" }
        val tags = mutableListOf<Pair<String, String>>(); val pictures = mutableListOf<Picture>(); var info: ByteArray? = null; var total = 0L; var blocks = 0
        do {
            val header = input.takeExact(4); val type = u(header[0]) and 127
            val length = (u(header[1]) shl 16) or (u(header[2]) shl 8) or u(header[3]); total += length + 4
            require(total <= MAX_METADATA_BYTES && ++blocks <= 10_000) { "flac-metadata-limit" }
            when (type) {
                0 -> { require(info == null && blocks == 1 && length == 34) { "invalid-flac-streaminfo" }; info = input.takeExact(length) }
                4 -> tags += readComments(input.takeExact(length))
                6 -> pictures += readPicture(input.takeExact(length))
                else -> input.skipExactly(length.toLong())
            }
        } while (u(header[0]) and 128 == 0)
        return Flac(tags, pictures, info ?: error("missing-streaminfo"))
    }

    /** Returns the final granule bytes when reading a complete, single Opus stream. */
    private fun walkOgg(input: InputStream, complete: Boolean, packet: (ByteArray, Int) -> Boolean): ByteArray {
        var serial: Int? = null; var sequence = 0; var index = 0; var continued = false; var sawEos = false; var granule = ByteArray(8); val current = ByteArrayOutputStream(); var headerBytes = 0L
        while (true) {
            val first = input.read(); if (first < 0) break; require(!sawEos) { "chained-ogg-not-supported" }
            val header = byteArrayOf(first.toByte()) + input.takeExact(26)
            require(header.hasMagic("OggS") && header[4] == 0.toByte()) { "invalid-ogg-page" }
            val flags = u(header[5]); require(flags and 0xf8 == 0) { "ogg-flags" }
            val pageSerial = le(header, 14)
            if (serial == null) { require(flags and 2 != 0 && le(header, 18) == 0) { "ogg-bos" }; serial = pageSerial }
            require(serial == pageSerial && le(header, 18) == sequence++) { "ogg-page-sequence" }
            require((flags and 1 != 0) == continued) { "ogg-continuation" }
            val laces = input.takeExact(u(header[26])); val data = input.takeExact(laces.sumOf { u(it) })
            val checksum = le(header, 22); header.fill(0, 22, 26); require(oggCrc(header + laces + data) == checksum) { "ogg-crc" }
            granule = header.copyOfRange(6, 14); var offset = 0
            for (lace in laces) {
                val length = u(lace); require(current.size().toLong() + length <= MAX_METADATA_BYTES) { "ogg-packet-limit" }
                current.write(data, offset, length); offset += length; continued = length == 255
                if (!continued) {
                    val value = current.toByteArray(); current.reset()
                    if (index < 2) { headerBytes += value.size; require(headerBytes <= MAX_METADATA_BYTES) { "ogg-header-limit" } }
                    if (!packet(value, index++)) return granule
                }
            }
            sawEos = flags and 4 != 0; if (sawEos) require(!continued && current.size() == 0) { "ogg-truncated-packet" }
        }
        require(!complete || sawEos) { "ogg-missing-eos" }; require(current.size() == 0) { "ogg-truncated-packet" }; return granule
    }
    private val crcTable = IntArray(256) { value -> var crc = value shl 24; repeat(8) { crc = (crc shl 1) xor if (crc < 0) 0x04c11db7 else 0 }; crc }
    private fun oggCrc(bytes: ByteArray): Int { var crc = 0; bytes.forEach { crc = (crc shl 8) xor crcTable[((crc ushr 24) xor u(it)) and 255] }; return crc }
    private fun validateOpusHead(bytes: ByteArray) {
        require(bytes.hasMagic("OpusHead") && bytes.size >= 19) { "invalid-opus-head" }
        require(u(bytes[8]) < 16 && u(bytes[9]) > 0) { "invalid-opus-head" }
        // Channel-mapping-family 0 uses the 19-byte header. Mapped streams
        // carry the additional stream/coupled-channel/mapping table bytes.
        if (u(bytes[18]) != 0) require(bytes.size >= 21 + u(bytes[9])) { "invalid-opus-mapping" }
    }
    private fun isLyricsKey(key: String): Boolean = key.equals("lyrics", true) || key.equals("unsyncedlyrics", true) || key.equals("uslt", true) || key.startsWith("lyrics-", true)
    private fun isLegacyLyricsTxxx(payload: ByteArray): Boolean = runCatching {
        parsePair(payload).first.equals("USLT", true)
    }.getOrDefault(false)
    private fun checked(phase: String, block: () -> Unit): Verification = try { block(); Verification(true) } catch (e: java.util.concurrent.CancellationException) { throw e } catch (e: Exception) { Verification(false, "$phase:${e.message ?: e.javaClass.simpleName}") }
    private fun checkCancelled(cancelled: () -> Boolean) { if (cancelled()) throw java.util.concurrent.CancellationException("audio-metadata-cancelled") }
    private fun transfer(input: InputStream, output: OutputStream?, digest: MessageDigest, cancelled: () -> Boolean) { val buffer = ByteArray(128 * 1024); while (true) { checkCancelled(cancelled); val n = input.read(buffer); if (n < 0) break; if (n == 0) continue; output?.write(buffer, 0, n); digest.update(buffer, 0, n) } }
    private fun digestRest(input: InputStream, cancelled: () -> Boolean): ByteArray = MessageDigest.getInstance("SHA-256").let { transfer(input, null, it, cancelled); it.digest() }
    private fun hash(bytes: ByteArray): String = hex(MessageDigest.getInstance("SHA-256").digest(bytes))
    private fun hex(bytes: ByteArray): String = bytes.joinToString("") { u(it).toString(16).padStart(2, '0') }
    private fun u(byte: Byte): Int = byte.toInt() and 255
    private fun ByteArray?.hasMagic(text: String): Boolean = this != null && hasMagicBytes(text.toByteArray(Charsets.US_ASCII))
    private fun ByteArray.hasMagicBytes(magic: ByteArray): Boolean = size >= magic.size && magic.indices.all { this[it] == magic[it] }
    private fun InputStream.takeExact(length: Int): ByteArray { require(length in 0..MAX_METADATA_BYTES) { "metadata-length" }; val bytes = ByteArray(length); var offset = 0; while (offset < length) { val n = read(bytes, offset, length - offset); if (n < 0) throw EOFException("metadata-truncated"); if (n > 0) offset += n }; return bytes }
    private fun InputStream.skipExactly(length: Long) { var remaining = length; while (remaining > 0) { val n = skip(remaining); if (n > 0) remaining -= n else { if (read() < 0) throw EOFException("metadata-truncated"); remaining-- } } }
    private fun synch(bytes: ByteArray, offset: Int): Int { require(offset >= 0 && bytes.size - offset >= 4 && (offset until offset + 4).all { u(bytes[it]) < 128 }) { "invalid-synchsafe" }; return (u(bytes[offset]) shl 21) or (u(bytes[offset + 1]) shl 14) or (u(bytes[offset + 2]) shl 7) or u(bytes[offset + 3]) }
    private fun be(bytes: ByteArray, offset: Int): Int { require(offset >= 0 && bytes.size - offset >= 4) { "truncated-uint" }; return (u(bytes[offset]) shl 24) or (u(bytes[offset + 1]) shl 16) or (u(bytes[offset + 2]) shl 8) or u(bytes[offset + 3]) }
    private fun le(bytes: ByteArray, offset: Int): Int { require(offset >= 0 && bytes.size - offset >= 4) { "truncated-uint" }; return u(bytes[offset]) or (u(bytes[offset + 1]) shl 8) or (u(bytes[offset + 2]) shl 16) or (u(bytes[offset + 3]) shl 24) }
    private fun OutputStream.putBe(value: Int) { write(value ushr 24); write(value ushr 16); write(value ushr 8); write(value) }
    private fun OutputStream.putSynch(value: Int) { write((value ushr 21) and 127); write((value ushr 14) and 127); write((value ushr 7) and 127); write(value and 127) }
    private fun OutputStream.putString(text: String) { val bytes = text.toByteArray(Charsets.UTF_8); putBe(bytes.size); write(bytes) }
    private class Cursor(val bytes: ByteArray, var offset: Int = 0) { fun be(): Int = be(bytes, offset).also { offset += 4 }; fun le(): Int = le(bytes, offset).also { offset += 4 }; fun blobBe(): ByteArray = blob(be()); fun blobLe(): ByteArray = blob(le()); private fun blob(length: Int): ByteArray { require(length >= 0 && length <= bytes.size - offset) { "metadata-length" }; return bytes.copyOfRange(offset, offset + length).also { offset += length } } }
}
