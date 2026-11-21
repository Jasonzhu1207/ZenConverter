package org.zenconverter.app.markdown

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import android.util.Base64
import android.util.Log
import org.zenconverter.app.conversion.MarkdownExportOptions
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLDecoder
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

internal data class ResolvedMedia(
    val imageFile: File?,
    val widthPx: Int,
    val heightPx: Int,
    val durationMs: Long? = null,
    val isVideo: Boolean = false,
    val badgeLabel: String? = null
)

internal data class MarkdownMediaCollection(
    val images: Map<String, ResolvedMedia>,
    val videos: Map<String, ResolvedMedia>
)

internal class MarkdownMediaResolver(
    private val context: Context,
    private val options: MarkdownExportOptions,
    private val mediaCacheDir: File,
    private val isCancelled: () -> Boolean,
    private val ffmpegExtractFrame: (videoUri: Uri?, localPath: String?, outputJpg: File) -> Boolean
) {
    private val imageCache = linkedMapOf<String, ResolvedMedia>()
    private val videoCache = linkedMapOf<String, ResolvedMedia>()
    private val folderLookupCache = mutableMapOf<Pair<String, String>, Pair<String, Boolean>?>()

    private var remoteFetchCount = 0
    private var remoteTotalBytes = 0L
    private var nextFileIndex = 0

    fun resolveAll(
        document: ParsedMarkdownDocument,
        onProgress: (Float) -> Unit
    ): MarkdownMediaCollection {
        mediaCacheDir.mkdirs()
        val imageSpecs = linkedMapOf<String, String>() // source -> alt
        val videoSpecs = linkedMapOf<String, Pair<String?, String?>>() // source -> (posterUrl, title)

        fun collectFromSpans(spans: List<MdSpan>) {
            for (span in spans) {
                if (span is MdSpan.InlineImageSpan && span.source.isNotBlank()) {
                    if (MarkdownStyleParser.isLikelyVideoSource(span.source)) {
                        videoSpecs.putIfAbsent(span.source, null to span.alt)
                    } else {
                        imageSpecs.putIfAbsent(span.source, span.alt)
                    }
                }
            }
        }

        fun collectFromBlocks(blocks: List<MdBlock>) {
            for (block in blocks) {
                when (block) {
                    is MdBlock.HeadingBlock -> collectFromSpans(block.spans)
                    is MdBlock.ParagraphBlock -> collectFromSpans(block.spans)
                    is MdBlock.BlockQuoteBlock -> collectFromBlocks(block.children)
                    is MdBlock.DetailsBlock -> {
                        collectFromSpans(block.summary)
                        collectFromBlocks(block.children)
                    }
                    is MdBlock.ListBlock -> block.items.forEach { collectFromBlocks(it.blocks) }
                    is MdBlock.TableBlock -> {
                        (block.headerRows + block.bodyRows).forEach { row ->
                            row.forEach { cell -> collectFromSpans(cell.spans) }
                        }
                    }
                    is MdBlock.ImageBlock -> {
                        if (block.source.isNotBlank()) {
                            imageSpecs.putIfAbsent(block.source, block.alt)
                        }
                    }
                    is MdBlock.VideoBlock -> {
                        if (block.source.isNotBlank()) {
                            videoSpecs.putIfAbsent(block.source, block.posterUrl to block.title)
                        }
                    }
                    is MdBlock.FootnotesSectionBlock -> {
                        block.entries.forEach { collectFromSpans(it.spans) }
                    }
                    is MdBlock.FrontMatterBlock,
                    is MdBlock.CodeBlock,
                    is MdBlock.ThematicBreakBlock,
                    is MdBlock.MathBlock -> Unit
                }
            }
        }

        collectFromBlocks(document.blocks)

        val totalItems = imageSpecs.size + videoSpecs.size
        var completed = 0

        for ((source, alt) in imageSpecs) {
            if (isCancelled()) break
            resolveImage(source, alt)
            completed++
            if (totalItems > 0) {
                onProgress(0.05f + 0.30f * (completed.toFloat() / totalItems))
            }
        }

        for ((source, meta) in videoSpecs) {
            if (isCancelled()) break
            resolveVideo(source, meta.first)
            completed++
            if (totalItems > 0) {
                onProgress(0.05f + 0.30f * (completed.toFloat() / totalItems))
            }
        }

        return MarkdownMediaCollection(
            images = imageCache.toMap(),
            videos = videoCache.toMap()
        )
    }

    private fun resolveImage(source: String, alt: String): ResolvedMedia {
        imageCache[source]?.let { return it }
        val resolved = runCatching {
            resolveImageInternal(source, alt)
        }.getOrElse { error ->
            Log.w(TAG, "Failed to resolve markdown image $source", error)
            ResolvedMedia(imageFile = null, widthPx = 0, heightPx = 0)
        }
        imageCache[source] = resolved
        return resolved
    }

    private fun resolveImageInternal(source: String, alt: String): ResolvedMedia {
        val trimmed = source.trim()
        if (trimmed.isEmpty()) return ResolvedMedia(null, 0, 0)

        if (trimmed.startsWith("data:", ignoreCase = true)) {
            return resolveDataUriImage(trimmed, alt)
        }

        if (isHttpUrl(trimmed)) {
            if (!options.fetchRemoteMedia) {
                return ResolvedMedia(null, 0, 0)
            }
            if (remoteFetchCount >= MAX_REMOTE_ITEMS || remoteTotalBytes >= MAX_REMOTE_TOTAL_BYTES) {
                return ResolvedMedia(null, 0, 0)
            }
            remoteFetchCount++
            val downloadResult = downloadRemoteToTempFile(trimmed, MAX_IMAGE_BYTES)
                ?: return ResolvedMedia(null, 0, 0)
            try {
                val contentType = downloadResult.contentType.orEmpty().lowercase(Locale.US)
                if (contentType.contains("svg") || looksLikeSvgFile(downloadResult.file)) {
                    val label = extractSvgBadgeLabel(downloadResult.file, alt)
                    return ResolvedMedia(imageFile = null, widthPx = 0, heightPx = 0, badgeLabel = label)
                }
                return normalizeRasterImageFile(downloadResult.file)
                    ?: ResolvedMedia(null, 0, 0)
            } finally {
                downloadResult.file.delete()
            }
        }

        // Local relative path inside authorized SAF folder
        val localUri = resolveLocalAssetUri(trimmed) ?: return ResolvedMedia(null, 0, 0)
        val tempRaw = createTempFile("raw_local_", ".bin")
        return try {
            val copiedBytes = copyUriToTempFile(localUri, tempRaw, MAX_IMAGE_BYTES)
            if (copiedBytes <= 0L) return ResolvedMedia(null, 0, 0)
            if (trimmed.substringBefore('?').endsWith(".svg", ignoreCase = true) || looksLikeSvgFile(tempRaw)) {
                val label = extractSvgBadgeLabel(tempRaw, alt)
                return ResolvedMedia(imageFile = null, widthPx = 0, heightPx = 0, badgeLabel = label)
            }
            normalizeRasterImageFile(tempRaw) ?: ResolvedMedia(null, 0, 0)
        } finally {
            tempRaw.delete()
        }
    }

    private fun resolveDataUriImage(dataUri: String, alt: String): ResolvedMedia {
        val commaIdx = dataUri.indexOf(',')
        if (commaIdx <= 5) return ResolvedMedia(null, 0, 0)
        val header = dataUri.substring(5, commaIdx).lowercase(Locale.US)
        val payload = dataUri.substring(commaIdx + 1)
        if (header.contains("svg+xml")) {
            val decoded = runCatching {
                if (header.contains(";base64")) {
                    String(Base64.decode(payload, Base64.DEFAULT), Charsets.UTF_8)
                } else {
                    URLDecoder.decode(payload, "UTF-8")
                }
            }.getOrNull()
            val label = decoded?.let { extractSvgText(it) }?.takeIf { it.isNotBlank() }
                ?: alt.takeIf { it.isNotBlank() }
                ?: "SVG"
            return ResolvedMedia(imageFile = null, widthPx = 0, heightPx = 0, badgeLabel = label)
        }
        if (!header.contains(";base64")) return ResolvedMedia(null, 0, 0)
        val bytes = runCatching { Base64.decode(payload, Base64.DEFAULT) }.getOrNull()
            ?: return ResolvedMedia(null, 0, 0)
        if (bytes.isEmpty() || bytes.size > MAX_IMAGE_BYTES) return ResolvedMedia(null, 0, 0)

        val tempRaw = createTempFile("raw_data_", ".bin")
        return try {
            tempRaw.writeBytes(bytes)
            normalizeRasterImageFile(tempRaw) ?: ResolvedMedia(null, 0, 0)
        } finally {
            tempRaw.delete()
        }
    }

    private fun resolveVideo(source: String, posterUrl: String?): ResolvedMedia {
        videoCache[source]?.let { return it }
        val resolved = runCatching {
            resolveVideoInternal(source, posterUrl)
        }.getOrElse { error ->
            Log.w(TAG, "Failed to resolve markdown video $source", error)
            ResolvedMedia(imageFile = null, widthPx = 0, heightPx = 0, isVideo = true)
        }
        videoCache[source] = resolved
        return resolved
    }

    private fun resolveVideoInternal(source: String, posterUrl: String?): ResolvedMedia {
        val trimmed = source.trim()
        if (trimmed.isEmpty()) return ResolvedMedia(null, 0, 0, isVideo = true)

        if (isHttpUrl(trimmed)) {
            if (!options.fetchRemoteMedia) {
                return ResolvedMedia(null, 0, 0, isVideo = true)
            }
            if (remoteFetchCount < MAX_REMOTE_ITEMS && remoteTotalBytes < MAX_REMOTE_TOTAL_BYTES) {
                remoteFetchCount++
                // Attempt 1: Range-based streaming first frame extraction via MediaMetadataRetriever
                val streamed = extractRemoteVideoFrameViaRetriever(trimmed)
                if (streamed != null) return streamed

                // Attempt 2: Bounded download <= 25 MiB + FFmpeg first-frame extraction
                val downloadResult = downloadRemoteToTempFile(trimmed, MAX_VIDEO_DOWNLOAD_BYTES)
                if (downloadResult != null) {
                    try {
                        val ffmpegFrame = extractVideoFrameViaFfmpeg(
                            videoUri = null,
                            localPath = downloadResult.file.absolutePath,
                            durationMs = null
                        )
                        if (ffmpegFrame != null) return ffmpegFrame
                    } finally {
                        downloadResult.file.delete()
                    }
                }
            }
        } else {
            val localUri = resolveLocalAssetUri(trimmed)
            if (localUri != null) {
                val localStreamed = extractLocalVideoFrameViaRetriever(localUri)
                if (localStreamed != null) return localStreamed

                val ffmpegFrame = extractVideoFrameViaFfmpeg(
                    videoUri = localUri,
                    localPath = null,
                    durationMs = null
                )
                if (ffmpegFrame != null) return ffmpegFrame
            }
        }

        // Attempt 3: Fallback to <video poster="...">
        if (!posterUrl.isNullOrBlank()) {
            val poster = resolveImage(posterUrl, "video")
            if (poster.imageFile != null) {
                return poster.copy(isVideo = true)
            }
        }

        return ResolvedMedia(imageFile = null, widthPx = 0, heightPx = 0, isVideo = true)
    }

    private fun extractLocalVideoFrameViaRetriever(uri: Uri): ResolvedMedia? {
        if (isCancelled()) return null
        val pfd = runCatching { context.contentResolver.openFileDescriptor(uri, "r") }.getOrNull()
        val retriever = MediaMetadataRetriever()
        return try {
            val dataSourceSet = runCatching {
                retriever.setDataSource(context, uri)
                true
            }.getOrElse {
                if (pfd != null) {
                    runCatching {
                        retriever.setDataSource(pfd.fileDescriptor)
                        true
                    }.getOrDefault(false)
                } else {
                    false
                }
            }
            if (!dataSourceSet) return null
            extractFrameFromRetriever(retriever)
        } finally {
            runCatching { retriever.release() }
            runCatching { pfd?.close() }
        }
    }

    private fun extractRemoteVideoFrameViaRetriever(url: String): ResolvedMedia? {
        if (isCancelled()) return null
        val executor = Executors.newSingleThreadExecutor()
        val retriever = MediaMetadataRetriever()
        val future = executor.submit<ResolvedMedia?> {
            try {
                retriever.setDataSource(url, mapOf("User-Agent" to USER_AGENT))
                extractFrameFromRetriever(retriever)
            } catch (t: Throwable) {
                Log.d(TAG, "Remote MediaMetadataRetriever failed for $url", t)
                null
            }
        }
        return try {
            future.get(VIDEO_PROBE_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        } catch (_: Throwable) {
            future.cancel(true)
            null
        } finally {
            runCatching { retriever.release() }
            executor.shutdownNow()
        }
    }

    private fun extractFrameFromRetriever(retriever: MediaMetadataRetriever): ResolvedMedia? {
        if (isCancelled()) return null
        val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            ?.toLongOrNull()
            ?.takeIf { it > 0L }
        var bitmap: Bitmap? = null
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            bitmap = runCatching {
                retriever.getScaledFrameAtTime(
                    0L,
                    MediaMetadataRetriever.OPTION_CLOSEST_SYNC,
                    MAX_VIDEO_FRAME_DIM_PX,
                    MAX_VIDEO_FRAME_DIM_PX
                )
            }.getOrNull()
        }
        if (bitmap == null) {
            bitmap = runCatching {
                retriever.getFrameAtTime(0L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
            }.getOrNull()
        }
        if (bitmap == null) return null
        return try {
            saveVideoFrameBitmap(bitmap, durationMs)
        } finally {
            bitmap.recycle()
        }
    }

    private fun extractVideoFrameViaFfmpeg(
        videoUri: Uri?,
        localPath: String?,
        durationMs: Long?
    ): ResolvedMedia? {
        if (isCancelled()) return null
        val outFile = createTempFile("vframe_", ".jpg")
        val ok = runCatching {
            ffmpegExtractFrame(videoUri, localPath, outFile)
        }.getOrDefault(false)
        if (!ok || !outFile.isFile || outFile.length() <= 0L) {
            outFile.delete()
            return null
        }
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(outFile.absolutePath, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            outFile.delete()
            return null
        }
        return ResolvedMedia(
            imageFile = outFile,
            widthPx = bounds.outWidth,
            heightPx = bounds.outHeight,
            durationMs = durationMs,
            isVideo = true
        )
    }

    private fun saveVideoFrameBitmap(source: Bitmap, durationMs: Long?): ResolvedMedia? {
        val scaled = scaleBitmapDownIfNeeded(source, MAX_VIDEO_FRAME_DIM_PX)
        val outFile = createTempFile("vframe_", ".jpg")
        return try {
            FileOutputStream(outFile).use { out ->
                scaled.compress(Bitmap.CompressFormat.JPEG, 90, out)
            }
            ResolvedMedia(
                imageFile = outFile,
                widthPx = scaled.width,
                heightPx = scaled.height,
                durationMs = durationMs,
                isVideo = true
            )
        } catch (t: Throwable) {
            outFile.delete()
            null
        } finally {
            if (scaled !== source) {
                scaled.recycle()
            }
        }
    }

    private data class RemoteDownloadResult(
        val file: File,
        val contentType: String?
    )

    private fun downloadRemoteToTempFile(rawUrl: String, maxBytes: Long): RemoteDownloadResult? {
        var currentUrl = rawUrl
        var redirects = 0
        while (redirects <= MAX_REDIRECTS) {
            if (isCancelled()) return null
            val parsedUrl = runCatching { URL(currentUrl) }.getOrNull() ?: return null
            val protocol = parsedUrl.protocol.lowercase(Locale.US)
            if (protocol != "http" && protocol != "https") return null

            val connection = (parsedUrl.openConnection() as? HttpURLConnection) ?: return null
            var shouldCloseImmediately = true
            try {
                connection.requestMethod = "GET"
                connection.connectTimeout = CONNECT_TIMEOUT_MS
                connection.readTimeout = READ_TIMEOUT_MS
                connection.instanceFollowRedirects = false
                connection.setRequestProperty("User-Agent", USER_AGENT)
                connection.setRequestProperty("Accept", "*/*")

                val code = connection.responseCode
                if (code in 300..399) {
                    val location = connection.getHeaderField("Location")?.trim() ?: return null
                    val nextUrl = runCatching { URL(parsedUrl, location).toString() }.getOrNull() ?: return null
                    currentUrl = nextUrl
                    redirects++
                    continue
                }
                if (code !in 200..299) return null

                val contentLength = connection.contentLengthLong
                if (contentLength > maxBytes) return null
                if (contentLength > 0 && remoteTotalBytes + contentLength > MAX_REMOTE_TOTAL_BYTES) return null

                val outFile = createTempFile("remote_", ".bin")
                var bytesRead = 0L
                val buffer = ByteArray(32 * 1024)
                val success = try {
                    connection.inputStream.use { input ->
                        FileOutputStream(outFile).use { output ->
                            while (true) {
                                if (isCancelled()) return null
                                val read = input.read(buffer)
                                if (read == -1) break
                                bytesRead += read
                                if (bytesRead > maxBytes || remoteTotalBytes + bytesRead > MAX_REMOTE_TOTAL_BYTES) {
                                    return null
                                }
                                output.write(buffer, 0, read)
                            }
                        }
                    }
                    true
                } catch (_: Throwable) {
                    false
                }
                if (!success || bytesRead <= 0L) {
                    outFile.delete()
                    return null
                }
                remoteTotalBytes += bytesRead
                shouldCloseImmediately = false
                val contentType = connection.contentType
                connection.disconnect()
                return RemoteDownloadResult(outFile, contentType)
            } finally {
                if (shouldCloseImmediately) {
                    runCatching { connection.disconnect() }
                }
            }
        }
        return null
    }

    private fun normalizeRasterImageFile(rawFile: File): ResolvedMedia? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(rawFile.absolutePath, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        var sampleSize = 1
        val maxDim = maxOf(bounds.outWidth, bounds.outHeight)
        while (maxDim / sampleSize > MAX_DECODED_LONG_SIDE_PX * 2) {
            sampleSize *= 2
        }

        val decodeOpts = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val decoded = BitmapFactory.decodeFile(rawFile.absolutePath, decodeOpts) ?: return null
        val oriented = applyExifOrientation(decoded, rawFile)
        val finalBitmap = scaleBitmapDownIfNeeded(oriented, MAX_DECODED_LONG_SIDE_PX)
        val hasAlpha = finalBitmap.hasAlpha()
        val ext = if (hasAlpha) ".png" else ".jpg"
        val outFile = createTempFile("img_", ext)
        return try {
            FileOutputStream(outFile).use { out ->
                if (hasAlpha) {
                    finalBitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                } else {
                    finalBitmap.compress(Bitmap.CompressFormat.JPEG, 92, out)
                }
            }
            ResolvedMedia(
                imageFile = outFile,
                widthPx = finalBitmap.width,
                heightPx = finalBitmap.height
            )
        } catch (_: Throwable) {
            outFile.delete()
            null
        } finally {
            if (finalBitmap !== oriented) finalBitmap.recycle()
            if (oriented !== decoded) oriented.recycle()
            decoded.recycle()
        }
    }

    private fun applyExifOrientation(bitmap: Bitmap, file: File): Bitmap {
        val orientation = runCatching {
            ExifInterface(file.absolutePath).getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL
            )
        }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)

        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.setScale(-1f, 1f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.setRotate(180f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.setScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> {
                matrix.setRotate(90f)
                matrix.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.setRotate(90f)
            ExifInterface.ORIENTATION_TRANSVERSE -> {
                matrix.setRotate(-90f)
                matrix.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.setRotate(270f)
            else -> return bitmap
        }
        return runCatching {
            Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        }.getOrDefault(bitmap)
    }

    private fun scaleBitmapDownIfNeeded(source: Bitmap, maxDim: Int): Bitmap {
        val w = source.width
        val h = source.height
        if (w <= maxDim && h <= maxDim) return source
        val scale = minOf(maxDim.toFloat() / w, maxDim.toFloat() / h)
        val tw = (w * scale).toInt().coerceAtLeast(1)
        val th = (h * scale).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(source, tw, th, true)
    }

    private fun resolveLocalAssetUri(rawPath: String): Uri? {
        val treeUri = options.assetsTreeUri ?: return null
        val cleaned = rawPath
            .substringBefore('#')
            .substringBefore('?')
            .trim()
        if (cleaned.isEmpty()) return null
        if (cleaned.contains("://") && !cleaned.startsWith("file://", ignoreCase = true)) {
            return null
        }

        val decodedPath = runCatching {
            URLDecoder.decode(cleaned, "UTF-8")
        }.getOrDefault(cleaned).replace('\\', '/')

        val rawSegments = decodedPath.split('/').filter { it.isNotEmpty() && it != "." }
        if (rawSegments.isEmpty()) return null

        val rootDocId = runCatching { DocumentsContract.getTreeDocumentId(treeUri) }.getOrNull() ?: return null
        val baseParentId = options.sourceParentDocumentId ?: rootDocId

        // 1. Try relative to the .md file's parent directory when no leading ".." escapes it
        if (!rawSegments.first().equals("..")) {
            val normalized = normalizeRelativeSegments(rawSegments)
            if (normalized != null) {
                resolveSegmentsUnderFolder(treeUri, baseParentId, normalized)?.let { return it }
            }
        }

        // 2. Fallback: normalize relative to tree root (e.g., if user picked root project folder)
        val normalizedFromRoot = normalizeRelativeSegments(rawSegments.dropWhile { it == ".." })
        if (normalizedFromRoot != null && baseParentId != rootDocId) {
            resolveSegmentsUnderFolder(treeUri, rootDocId, normalizedFromRoot)?.let { return it }
        }
        return null
    }

    private fun normalizeRelativeSegments(segments: List<String>): List<String>? {
        val stack = mutableListOf<String>()
        for (seg in segments) {
            when (seg) {
                "", "." -> Unit
                ".." -> {
                    if (stack.isEmpty()) return null
                    stack.removeAt(stack.lastIndex)
                }
                else -> stack.add(seg)
            }
        }
        return stack.takeIf { it.isNotEmpty() }
    }

    private fun resolveSegmentsUnderFolder(
        treeUri: Uri,
        startFolderDocId: String,
        segments: List<String>
    ): Uri? {
        var currentFolderId = startFolderDocId
        for (i in segments.indices) {
            val targetName = segments[i]
            val expectDir = i < segments.lastIndex
            val child = findChildDocument(treeUri, currentFolderId, targetName) ?: return null
            val (childDocId, isDir) = child
            if (expectDir && !isDir) return null
            if (!expectDir) {
                return if (isDir) null else DocumentsContract.buildDocumentUriUsingTree(treeUri, childDocId)
            }
            currentFolderId = childDocId
        }
        return null
    }

    private fun findChildDocument(
        treeUri: Uri,
        parentDocId: String,
        targetName: String
    ): Pair<String, Boolean>? {
        val key = parentDocId to targetName.lowercase(Locale.US)
        if (folderLookupCache.containsKey(key)) {
            return folderLookupCache[key]
        }

        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentDocId)
        var exactMatch: Pair<String, Boolean>? = null
        var caseInsensitiveMatch: Pair<String, Boolean>? = null

        runCatching {
            context.contentResolver.query(
                childrenUri,
                arrayOf(
                    DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                    DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                    DocumentsContract.Document.COLUMN_MIME_TYPE
                ),
                null,
                null,
                null
            )?.use { cursor ->
                val idIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                val nameIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                val mimeIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE)
                while (cursor.moveToNext()) {
                    val docId = if (idIdx >= 0 && !cursor.isNull(idIdx)) cursor.getString(idIdx) else continue
                    val name = if (nameIdx >= 0 && !cursor.isNull(nameIdx)) cursor.getString(nameIdx) else continue
                    val mime = if (mimeIdx >= 0 && !cursor.isNull(mimeIdx)) cursor.getString(mimeIdx) else ""
                    val isDir = mime == DocumentsContract.Document.MIME_TYPE_DIR
                    if (name == targetName) {
                        exactMatch = docId to isDir
                        break
                    }
                    if (caseInsensitiveMatch == null && name.equals(targetName, ignoreCase = true)) {
                        caseInsensitiveMatch = docId to isDir
                    }
                }
            }
        }

        val resolved = exactMatch ?: caseInsensitiveMatch
        folderLookupCache[key] = resolved
        return resolved
    }

    private fun copyUriToTempFile(uri: Uri, target: File, maxBytes: Long): Long {
        var total = 0L
        val buffer = ByteArray(32 * 1024)
        context.contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(target).use { output ->
                while (true) {
                    if (isCancelled()) return -1L
                    val read = input.read(buffer)
                    if (read == -1) break
                    total += read
                    if (total > maxBytes) return -1L
                    output.write(buffer, 0, read)
                }
            }
        } ?: return -1L
        return total
    }

    private fun looksLikeSvgFile(file: File): Boolean {
        val head = runCatching {
            file.inputStream().use { input ->
                val buf = ByteArray(512)
                val count = input.read(buf)
                if (count > 0) String(buf, 0, count, Charsets.UTF_8) else ""
            }
        }.getOrDefault("")
        val lower = head.trimStart().lowercase(Locale.US)
        return lower.startsWith("<svg") || (lower.startsWith("<?xml") && lower.contains("<svg"))
    }

    private fun extractSvgBadgeLabel(file: File, alt: String): String {
        val text = runCatching {
            if (file.length() <= 256 * 1024) file.readText(Charsets.UTF_8) else ""
        }.getOrDefault("")
        val extracted = extractSvgText(text)
        return extracted.takeIf { it.isNotBlank() }
            ?: alt.takeIf { it.isNotBlank() }
            ?: "SVG"
    }

    private fun extractSvgText(svg: String): String {
        if (svg.isEmpty()) return ""
        val texts = Regex("""<text\b[^>]*>(.*?)</text>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
            .findAll(svg)
            .map { MarkdownStyleParser.decodeHtmlEntities(it.groupValues[1].replace(Regex("""<[^>]+>"""), "")).trim() }
            .filter { it.isNotEmpty() }
            .distinct()
            .toList()
        return texts.joinToString(" · ")
    }

    private fun createTempFile(prefix: String, suffix: String): File {
        val idx = nextFileIndex++
        return File(mediaCacheDir, "${prefix}${idx}$suffix")
    }

    private fun isHttpUrl(url: String): Boolean {
        return url.startsWith("http://", ignoreCase = true) || url.startsWith("https://", ignoreCase = true)
    }

    companion object {
        private const val TAG = "MarkdownMediaResolver"
        private const val USER_AGENT = "ZenConverter-Android"
        private const val MAX_REDIRECTS = 5
        private const val CONNECT_TIMEOUT_MS = 10_000
        private const val READ_TIMEOUT_MS = 15_000
        private const val VIDEO_PROBE_TIMEOUT_MS = 15_000L
        private const val MAX_IMAGE_BYTES = 12L * 1024 * 1024
        private const val MAX_VIDEO_DOWNLOAD_BYTES = 25L * 1024 * 1024
        private const val MAX_REMOTE_ITEMS = 40
        private const val MAX_REMOTE_TOTAL_BYTES = 60L * 1024 * 1024
        private const val MAX_DECODED_LONG_SIDE_PX = 2048
        private const val MAX_VIDEO_FRAME_DIM_PX = 1280
    }
}
