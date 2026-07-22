package org.zenconverter.app.metadata

import android.content.Context
import android.content.ContentUris
import android.app.RecoverableSecurityException
import android.graphics.BitmapFactory
import android.media.ExifInterface
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.util.Log
import org.zenconverter.app.conversion.FileBasicInfoReader
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest
import java.util.Locale
import java.util.Properties
import java.util.UUID

enum class MetadataTargetKind {
    Image,
    Video
}

enum class MetadataMessageKey {
    Cleaned,
    Restored,
    NoRemovableMetadata,
    UnsupportedImageFormat,
    WritePermissionNeeded,
    CouldNotRead,
    CouldNotWrite,
    BackupMissing,
    BackupDoesNotMatch,
    InvalidJpeg
}

data class MetadataStatusMessage(
    val key: MetadataMessageKey,
    val detail: String? = null
)

sealed interface MetadataToolState {
    object Empty : MetadataToolState
    object Loading : MetadataToolState
    data class Ready(
        val inspection: MetadataInspection,
        val busy: Boolean = false,
        val message: MetadataStatusMessage? = null
    ) : MetadataToolState
    data class Error(val message: MetadataStatusMessage) : MetadataToolState
}

data class MetadataInspection(
    val uri: Uri,
    val displayName: String,
    val mimeType: String?,
    val sizeBytes: Long?,
    val kind: MetadataTargetKind,
    val formatLabel: String,
    val canWrite: Boolean,
    val editable: Boolean,
    val unsupportedMessage: MetadataMessageKey? = null,
    val width: Int? = null,
    val height: Int? = null,
    val durationMs: Long? = null,
    val bitrateBitsPerSecond: Long? = null,
    val frameRate: Float? = null,
    val hasRemovableMetadata: Boolean = false,
    val removableSegmentCount: Int = 0,
    val removableBytes: Long = 0L,
    val hasExif: Boolean = false,
    val hasXmp: Boolean = false,
    val hasIptc: Boolean = false,
    val hasComment: Boolean = false,
    val hasGps: Boolean = false,
    val capturedAt: String? = null,
    val camera: String? = null,
    val software: String? = null,
    val orientation: Int? = null,
    val description: String? = null,
    val artist: String? = null,
    val copyright: String? = null,
    val coreHash: String? = null,
    val backups: List<MetadataBackupInfo> = emptyList()
)

data class MetadataBackupInfo(
    val id: String,
    val createdAtMillis: Long,
    val segmentCount: Int,
    val segmentBytes: Long,
    val originalDisplayName: String
)

class MetadataOperationException(
    val status: MetadataStatusMessage,
    cause: Throwable? = null
) : IOException(status.key.name, cause)

object MetadataPrivacyManager {
    fun mediaStoreUriForWriteRequest(
        context: Context,
        inspection: MetadataInspection
    ): Uri? {
        if (inspection.kind != MetadataTargetKind.Image) return null
        return mediaStoreItemUriFor(context, inspection.uri)
    }

    fun canRequestMediaWrite(
        context: Context,
        uri: Uri,
        kind: MetadataTargetKind
    ): Boolean {
        return kind == MetadataTargetKind.Image && mediaStoreItemUriFor(context, uri) != null
    }

    fun isMediaWritePermissionFailure(throwable: Throwable): Boolean {
        return throwable.anyInChain { current ->
            current is RecoverableSecurityException ||
                current.message?.contains(READ_ONLY_MEDIA_MESSAGE, ignoreCase = true) == true ||
                current.message?.contains(MEDIASTORE_ACCESS_DENIED_MESSAGE, ignoreCase = true) == true
        }
    }

    fun recoverableSecurityExceptionFor(throwable: Throwable): RecoverableSecurityException? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null
        var recoverable: RecoverableSecurityException? = null
        throwable.anyInChain { current ->
            recoverable = current as? RecoverableSecurityException
            recoverable != null
        }
        return recoverable
    }

    fun inspect(
        context: Context,
        uri: Uri,
        displayName: String,
        sizeBytes: Long?,
        mimeType: String?,
        kind: MetadataTargetKind,
        canWrite: Boolean
    ): MetadataInspection {
        return when (kind) {
            MetadataTargetKind.Image -> inspectImage(context, uri, displayName, sizeBytes, mimeType, canWrite)
            MetadataTargetKind.Video -> inspectVideo(context, uri, displayName, sizeBytes, mimeType, canWrite)
        }
    }

    fun requireJpegWriteAccess(
        context: Context,
        inspection: MetadataInspection
    ) {
        if (!inspection.editable) {
            throw MetadataOperationException(MetadataStatusMessage(MetadataMessageKey.UnsupportedImageFormat))
        }
        if (!inspection.canWrite) {
            throw MetadataOperationException(MetadataStatusMessage(MetadataMessageKey.WritePermissionNeeded))
        }

        var firstFailure: Throwable? = null
        writeUriCandidates(context, inspection.uri).forEach { writeUri ->
            val failure = probeWriteAccess(context, writeUri)
            if (failure == null) return
            firstFailure = firstFailure.rememberWriteFailure(failure)
        }

        val failure = firstFailure
        throw MetadataOperationException(
            MetadataStatusMessage(MetadataMessageKey.CouldNotWrite, failure?.safeDetail()),
            failure
        )
    }

    fun cleanJpegInPlace(
        context: Context,
        inspection: MetadataInspection
    ): MetadataStatusMessage {
        if (!inspection.editable) {
            throw MetadataOperationException(MetadataStatusMessage(MetadataMessageKey.UnsupportedImageFormat))
        }
        if (!inspection.canWrite) {
            throw MetadataOperationException(MetadataStatusMessage(MetadataMessageKey.WritePermissionNeeded))
        }

        val workDir = File(context.cacheDir, "metadata-edit").apply { mkdirs() }
        val sourceCopy = File(workDir, "${UUID.randomUUID()}-source.jpg")
        val cleanedCopy = File(workDir, "${UUID.randomUUID()}-clean.jpg")
        val stagingBackupDir = File(workDir, "${UUID.randomUUID()}-backup").apply { mkdirs() }
        var committedBackupDir: File? = null

        try {
            copyUriToFile(context, inspection.uri, sourceCopy)
            val result = rewriteJpegWithoutRemovableMetadata(
                inputFile = sourceCopy,
                outputFile = cleanedCopy,
                backupDirectory = stagingBackupDir
            )
            if (!result.validJpeg) {
                throw MetadataOperationException(MetadataStatusMessage(MetadataMessageKey.InvalidJpeg))
            }
            if (result.removedSegmentCount <= 0) {
                stagingBackupDir.deleteRecursively()
                return MetadataStatusMessage(MetadataMessageKey.NoRemovableMetadata)
            }

            replaceUriFromFile(
                context = context,
                uri = inspection.uri,
                replacement = cleanedCopy,
                rollback = sourceCopy
            )
            committedBackupDir = commitBackupDirectory(
                context = context,
                inspection = inspection,
                result = result,
                stagingBackupDir = stagingBackupDir
            )
            return MetadataStatusMessage(MetadataMessageKey.Cleaned)
        } catch (exception: MetadataOperationException) {
            stagingBackupDir.deleteRecursively()
            committedBackupDir?.deleteRecursively()
            throw exception
        } catch (exception: Throwable) {
            Log.w(TAG, "Metadata clean failed", exception)
            stagingBackupDir.deleteRecursively()
            committedBackupDir?.deleteRecursively()
            throw MetadataOperationException(
                MetadataStatusMessage(MetadataMessageKey.CouldNotWrite, exception.safeDetail()),
                exception
            )
        } finally {
            sourceCopy.delete()
            cleanedCopy.delete()
            stagingBackupDir.deleteRecursively()
        }
    }

    fun restoreJpegMetadataInPlace(
        context: Context,
        inspection: MetadataInspection,
        backupId: String
    ): MetadataStatusMessage {
        if (!inspection.editable) {
            throw MetadataOperationException(MetadataStatusMessage(MetadataMessageKey.UnsupportedImageFormat))
        }
        if (!inspection.canWrite) {
            throw MetadataOperationException(MetadataStatusMessage(MetadataMessageKey.WritePermissionNeeded))
        }

        val backupDir = File(backupRoot(context), backupId)
        val manifest = readBackupManifest(backupDir)
            ?: throw MetadataOperationException(MetadataStatusMessage(MetadataMessageKey.BackupMissing))
        val expectedHash = manifest.getProperty(PROP_CORE_HASH).orEmpty()
        if (expectedHash.isBlank() || expectedHash != inspection.coreHash) {
            throw MetadataOperationException(MetadataStatusMessage(MetadataMessageKey.BackupDoesNotMatch))
        }

        val workDir = File(context.cacheDir, "metadata-edit").apply { mkdirs() }
        val sourceCopy = File(workDir, "${UUID.randomUUID()}-source.jpg")
        val strippedCopy = File(workDir, "${UUID.randomUUID()}-stripped.jpg")
        val restoredCopy = File(workDir, "${UUID.randomUUID()}-restored.jpg")

        try {
            copyUriToFile(context, inspection.uri, sourceCopy)
            val strippedResult = rewriteJpegWithoutRemovableMetadata(
                inputFile = sourceCopy,
                outputFile = strippedCopy,
                backupDirectory = null
            )
            if (!strippedResult.validJpeg) {
                throw MetadataOperationException(MetadataStatusMessage(MetadataMessageKey.InvalidJpeg))
            }
            if (strippedResult.coreHash != expectedHash) {
                throw MetadataOperationException(MetadataStatusMessage(MetadataMessageKey.BackupDoesNotMatch))
            }

            val segmentFiles = segmentFilesForBackup(backupDir, manifest)
            if (segmentFiles.isEmpty()) {
                throw MetadataOperationException(MetadataStatusMessage(MetadataMessageKey.BackupMissing))
            }
            insertMetadataSegments(
                cleanedInput = strippedCopy,
                outputFile = restoredCopy,
                segmentFiles = segmentFiles
            )
            replaceUriFromFile(
                context = context,
                uri = inspection.uri,
                replacement = restoredCopy,
                rollback = sourceCopy
            )
            return MetadataStatusMessage(MetadataMessageKey.Restored)
        } catch (exception: MetadataOperationException) {
            throw exception
        } catch (exception: Throwable) {
            Log.w(TAG, "Metadata restore failed", exception)
            throw MetadataOperationException(
                MetadataStatusMessage(MetadataMessageKey.CouldNotWrite, exception.safeDetail()),
                exception
            )
        } finally {
            sourceCopy.delete()
            strippedCopy.delete()
            restoredCopy.delete()
        }
    }

    private fun inspectImage(
        context: Context,
        uri: Uri,
        displayName: String,
        sizeBytes: Long?,
        mimeType: String?,
        canWrite: Boolean
    ): MetadataInspection {
        val extension = extensionFor(displayName)
        val formatLabel = formatLabelFor(extension, mimeType)
        val bounds = readImageBounds(context, uri)
        val canModifyInPlace = canWrite || canRequestMediaWrite(context, uri, MetadataTargetKind.Image)
        if (!isJpegFamily(extension, mimeType)) {
            return MetadataInspection(
                uri = uri,
                displayName = displayName,
                mimeType = mimeType,
                sizeBytes = sizeBytes,
                kind = MetadataTargetKind.Image,
                formatLabel = formatLabel,
                canWrite = canModifyInPlace,
                editable = false,
                unsupportedMessage = MetadataMessageKey.UnsupportedImageFormat,
                width = bounds?.first,
                height = bounds?.second
            )
        }

        val scan = runCatching {
            context.contentResolver.openInputStream(uri)?.use { input ->
                scanJpegMetadata(input)
            }
        }.getOrNull() ?: throw MetadataOperationException(MetadataStatusMessage(MetadataMessageKey.CouldNotRead))

        if (!scan.validJpeg) {
            return MetadataInspection(
                uri = uri,
                displayName = displayName,
                mimeType = mimeType,
                sizeBytes = sizeBytes,
                kind = MetadataTargetKind.Image,
                formatLabel = formatLabel,
                canWrite = canModifyInPlace,
                editable = false,
                unsupportedMessage = MetadataMessageKey.InvalidJpeg,
                width = bounds?.first,
                height = bounds?.second
            )
        }

        val exifSummary = readExifSummary(context, uri)
        val backups = matchingBackups(
            context = context,
            coreHash = scan.coreHash,
            width = bounds?.first,
            height = bounds?.second
        )

        return MetadataInspection(
            uri = uri,
            displayName = displayName,
            mimeType = mimeType,
            sizeBytes = sizeBytes,
            kind = MetadataTargetKind.Image,
            formatLabel = formatLabel,
            canWrite = canModifyInPlace,
            editable = true,
            width = bounds?.first,
            height = bounds?.second,
            hasRemovableMetadata = scan.removedSegmentCount > 0,
            removableSegmentCount = scan.removedSegmentCount,
            removableBytes = scan.removedBytes,
            hasExif = scan.kinds.contains(JpegMetadataSegmentKind.Exif),
            hasXmp = scan.kinds.contains(JpegMetadataSegmentKind.Xmp),
            hasIptc = scan.kinds.contains(JpegMetadataSegmentKind.Iptc),
            hasComment = scan.kinds.contains(JpegMetadataSegmentKind.Comment),
            hasGps = exifSummary.hasGps,
            capturedAt = exifSummary.capturedAt,
            camera = exifSummary.camera,
            software = exifSummary.software,
            orientation = exifSummary.orientation,
            description = exifSummary.description,
            artist = exifSummary.artist,
            copyright = exifSummary.copyright,
            coreHash = scan.coreHash,
            backups = backups
        )
    }

    private fun inspectVideo(
        context: Context,
        uri: Uri,
        displayName: String,
        sizeBytes: Long?,
        mimeType: String?,
        canWrite: Boolean
    ): MetadataInspection {
        val basicInfo = FileBasicInfoReader.read(
            context = context,
            uri = uri,
            displayName = displayName,
            mimeType = mimeType,
            fallbackSizeBytes = sizeBytes
        )
        return MetadataInspection(
            uri = uri,
            displayName = displayName,
            mimeType = mimeType,
            sizeBytes = basicInfo.sizeBytes ?: sizeBytes,
            kind = MetadataTargetKind.Video,
            formatLabel = basicInfo.formatLabel ?: formatLabelFor(extensionFor(displayName), mimeType),
            canWrite = canWrite,
            editable = false,
            width = basicInfo.width,
            height = basicInfo.height,
            durationMs = basicInfo.durationMs,
            bitrateBitsPerSecond = basicInfo.bitrateBitsPerSecond,
            frameRate = basicInfo.frameRate
        )
    }

    private fun scanJpegMetadata(input: InputStream): JpegRewriteResult {
        return rewriteJpegWithoutRemovableMetadata(
            input = input,
            output = null,
            backupDirectory = null
        )
    }

    private fun rewriteJpegWithoutRemovableMetadata(
        inputFile: File,
        outputFile: File?,
        backupDirectory: File?
    ): JpegRewriteResult {
        return FileInputStream(inputFile).use { input ->
            outputFile?.outputStream()?.use { output ->
                rewriteJpegWithoutRemovableMetadata(input, output, backupDirectory)
            } ?: rewriteJpegWithoutRemovableMetadata(input, null, backupDirectory)
        }
    }

    private fun rewriteJpegWithoutRemovableMetadata(
        input: InputStream,
        output: OutputStream?,
        backupDirectory: File?
    ): JpegRewriteResult {
        val bufferedInput = BufferedInputStream(input)
        val bufferedOutput = output?.let { BufferedOutputStream(it) }
        val digest = MessageDigest.getInstance("SHA-256")
        var removedCount = 0
        var removedBytes = 0L
        val kinds = linkedSetOf<JpegMetadataSegmentKind>()
        var backupIndex = 0

        fun keep(bytes: ByteArray) {
            digest.update(bytes)
            bufferedOutput?.write(bytes)
        }

        fun keep(byteValue: Int) {
            digest.update(byteArrayOf(byteValue.toByte()))
            bufferedOutput?.write(byteValue)
        }

        val first = bufferedInput.read()
        val second = bufferedInput.read()
        if (first != JPEG_MARKER_PREFIX || second != JPEG_MARKER_SOI) {
            bufferedOutput?.flush()
            return JpegRewriteResult(validJpeg = false)
        }
        keep(byteArrayOf(first.toByte(), second.toByte()))

        while (true) {
            val prefix = bufferedInput.read()
            if (prefix < 0) break
            if (prefix != JPEG_MARKER_PREFIX) {
                keep(prefix)
                copyRemaining(bufferedInput) { chunk -> keep(chunk) }
                break
            }

            var marker = bufferedInput.read()
            while (marker == JPEG_MARKER_PREFIX) {
                marker = bufferedInput.read()
            }
            if (marker < 0) {
                keep(JPEG_MARKER_PREFIX)
                break
            }

            if (marker == JPEG_MARKER_EOI || isStandaloneMarker(marker)) {
                keep(byteArrayOf(JPEG_MARKER_PREFIX.toByte(), marker.toByte()))
                if (marker == JPEG_MARKER_EOI) break
                continue
            }

            val lengthHigh = bufferedInput.read()
            val lengthLow = bufferedInput.read()
            if (lengthHigh < 0 || lengthLow < 0) {
                keep(byteArrayOf(JPEG_MARKER_PREFIX.toByte(), marker.toByte()))
                break
            }
            val length = (lengthHigh shl 8) or lengthLow
            if (length < 2) {
                bufferedOutput?.flush()
                return JpegRewriteResult(validJpeg = false)
            }

            val payload = bufferedInput.readExactBytesOrNull(length - 2)
                ?: return JpegRewriteResult(validJpeg = false)
            val rawSegment = ByteArray(length + 2)
            rawSegment[0] = JPEG_MARKER_PREFIX.toByte()
            rawSegment[1] = marker.toByte()
            rawSegment[2] = lengthHigh.toByte()
            rawSegment[3] = lengthLow.toByte()
            payload.copyInto(rawSegment, destinationOffset = 4)

            val kind = removableKind(marker, payload)
            if (kind != null) {
                removedCount++
                removedBytes += rawSegment.size.toLong()
                kinds.add(kind)
                backupDirectory?.let { directory ->
                    val segmentFile = File(directory, segmentFileName(backupIndex))
                    segmentFile.outputStream().use { it.write(rawSegment) }
                    backupIndex++
                }
            } else {
                keep(rawSegment)
            }

            if (marker == JPEG_MARKER_SOS) {
                copyRemaining(bufferedInput) { chunk -> keep(chunk) }
                break
            }
        }

        bufferedOutput?.flush()
        return JpegRewriteResult(
            validJpeg = true,
            coreHash = digest.digest().toHexString(),
            removedSegmentCount = removedCount,
            removedBytes = removedBytes,
            kinds = kinds
        )
    }

    private fun insertMetadataSegments(
        cleanedInput: File,
        outputFile: File,
        segmentFiles: List<File>
    ) {
        FileInputStream(cleanedInput).use { rawInput ->
            BufferedInputStream(rawInput).use { input ->
                FileOutputStream(outputFile).use { rawOutput ->
                    BufferedOutputStream(rawOutput).use { output ->
                        val first = input.read()
                        val second = input.read()
                        if (first != JPEG_MARKER_PREFIX || second != JPEG_MARKER_SOI) {
                            throw MetadataOperationException(
                                MetadataStatusMessage(MetadataMessageKey.InvalidJpeg)
                            )
                        }
                        output.write(first)
                        output.write(second)

                        var inserted = false
                        fun insertSegmentsIfNeeded() {
                            if (inserted) return
                            segmentFiles.forEach { file ->
                                file.inputStream().use { segmentInput ->
                                    segmentInput.copyTo(output)
                                }
                            }
                            inserted = true
                        }

                        while (true) {
                            val prefix = input.read()
                            if (prefix < 0) {
                                insertSegmentsIfNeeded()
                                break
                            }
                            if (prefix != JPEG_MARKER_PREFIX) {
                                insertSegmentsIfNeeded()
                                output.write(prefix)
                                input.copyTo(output)
                                break
                            }

                            var marker = input.read()
                            while (marker == JPEG_MARKER_PREFIX) {
                                marker = input.read()
                            }
                            if (marker < 0) {
                                insertSegmentsIfNeeded()
                                output.write(JPEG_MARKER_PREFIX)
                                break
                            }

                            if (marker == JPEG_MARKER_EOI || isStandaloneMarker(marker)) {
                                insertSegmentsIfNeeded()
                                output.write(byteArrayOf(JPEG_MARKER_PREFIX.toByte(), marker.toByte()))
                                if (marker == JPEG_MARKER_EOI) break
                                continue
                            }

                            val lengthHigh = input.read()
                            val lengthLow = input.read()
                            if (lengthHigh < 0 || lengthLow < 0) {
                                insertSegmentsIfNeeded()
                                output.write(byteArrayOf(JPEG_MARKER_PREFIX.toByte(), marker.toByte()))
                                break
                            }
                            val length = (lengthHigh shl 8) or lengthLow
                            val payload = input.readExactBytesOrNull(length - 2)
                                ?: throw MetadataOperationException(
                                    MetadataStatusMessage(MetadataMessageKey.InvalidJpeg)
                                )
                            val rawSegment = ByteArray(length + 2)
                            rawSegment[0] = JPEG_MARKER_PREFIX.toByte()
                            rawSegment[1] = marker.toByte()
                            rawSegment[2] = lengthHigh.toByte()
                            rawSegment[3] = lengthLow.toByte()
                            payload.copyInto(rawSegment, destinationOffset = 4)

                            if (marker != JPEG_MARKER_APP0) {
                                insertSegmentsIfNeeded()
                            }
                            output.write(rawSegment)
                            if (marker == JPEG_MARKER_SOS) {
                                input.copyTo(output)
                                break
                            }
                        }
                    }
                }
            }
        }
    }

    private fun copyUriToFile(context: Context, uri: Uri, file: File) {
        val input = context.contentResolver.openInputStream(uri)
            ?: throw MetadataOperationException(MetadataStatusMessage(MetadataMessageKey.CouldNotRead))
        input.use { source ->
            file.outputStream().use { target ->
                source.copyTo(target)
            }
        }
    }

    private fun replaceUriFromFile(
        context: Context,
        uri: Uri,
        replacement: File,
        rollback: File
    ) {
        try {
            writeFileToUri(context, replacement, uri)
        } catch (writeException: Throwable) {
            val shouldRollback = !isMediaWritePermissionFailure(writeException)
            Log.w(
                TAG,
                if (shouldRollback) {
                    "Metadata write failed for $uri; attempting rollback"
                } else {
                    "Metadata write needs media authorization for $uri; rollback skipped"
                },
                writeException
            )
            if (shouldRollback) {
                runCatching { writeFileToUri(context, rollback, uri) }
                    .onFailure { rollbackException ->
                        Log.w(TAG, "Metadata rollback failed for $uri", rollbackException)
                    }
            }
            throw when (writeException) {
                is MetadataOperationException -> writeException
                else -> MetadataOperationException(
                    MetadataStatusMessage(
                        MetadataMessageKey.CouldNotWrite,
                        writeException.safeDetail()
                    ),
                    writeException
                )
            }
        }
    }

    private fun writeFileToUri(context: Context, file: File, uri: Uri) {
        var firstFailure: Throwable? = null
        writeUriCandidates(context, uri).forEach { writeUri ->
            val failure = writeFileToSingleUri(context, file, writeUri)
            if (failure == null) return
            firstFailure = firstFailure.rememberWriteFailure(failure)
        }

        val failure = firstFailure
        throw MetadataOperationException(
            MetadataStatusMessage(MetadataMessageKey.CouldNotWrite, failure?.safeDetail()),
            failure
        )
    }

    private fun writeUriCandidates(context: Context, uri: Uri): List<Uri> {
        return buildList {
            add(uri)
            mediaStoreItemUriFor(context, uri)
                ?.takeIf { it != uri }
                ?.let { add(it) }
        }
    }

    private fun probeWriteAccess(context: Context, uri: Uri): Throwable? {
        val result = runCatching {
            context.contentResolver.openFileDescriptor(uri, "rw")?.use {
                // Opening rw is enough to make MediaProvider surface the required
                // one-time user authorization without truncating or rewriting data.
            } ?: throw MetadataOperationException(
                MetadataStatusMessage(
                    MetadataMessageKey.WritePermissionNeeded,
                    "openFileDescriptor(rw) returned null"
                )
            )
        }
        if (result.isSuccess) return null

        val failure = result.exceptionOrNull()
        Log.w(TAG, "Metadata write probe failed for $uri", failure)
        return failure
    }

    private fun writeFileToSingleUri(context: Context, file: File, uri: Uri): Throwable? {
        val resolver = context.contentResolver
        var firstFailure: Throwable? = null
        for (mode in SAF_TRUNCATING_WRITE_MODES) {
            val result = runCatching {
                resolver.openOutputStream(uri, mode)?.use { output ->
                    FileInputStream(file).use { input ->
                        input.copyTo(output)
                    }
                    output.flush()
                } ?: throw MetadataOperationException(
                    MetadataStatusMessage(
                        MetadataMessageKey.WritePermissionNeeded,
                        "openOutputStream($mode) returned null"
                    )
                )
            }
            if (result.isSuccess) return null

            val failure = result.exceptionOrNull()
            if (failure != null) {
                firstFailure = firstFailure.rememberWriteFailure(failure)
            }
            Log.w(TAG, "Metadata write mode $mode failed for $uri", failure)
        }

        val descriptorResult = runCatching {
            resolver.openFileDescriptor(uri, "rwt")?.use { descriptor ->
                FileOutputStream(descriptor.fileDescriptor).use { output ->
                    FileInputStream(file).use { input ->
                        input.copyTo(output)
                    }
                    output.flush()
                    runCatching { descriptor.fileDescriptor.sync() }
                }
            } ?: throw MetadataOperationException(
                MetadataStatusMessage(
                    MetadataMessageKey.WritePermissionNeeded,
                    "openFileDescriptor(rwt) returned null"
                )
            )
        }
        if (descriptorResult.isSuccess) return null

        val descriptorFailure = descriptorResult.exceptionOrNull()
        if (descriptorFailure != null) {
            firstFailure = firstFailure.rememberWriteFailure(descriptorFailure)
        }
        Log.w(TAG, "Metadata descriptor write mode rwt failed for $uri", descriptorFailure)

        return firstFailure
    }

    private fun Throwable?.rememberWriteFailure(failure: Throwable): Throwable {
        return if (this == null) {
            failure
        } else {
            if (this !== failure) {
                runCatching { addSuppressed(failure) }
            }
            this
        }
    }

    private fun mediaStoreItemUriFor(context: Context, uri: Uri): Uri? {
        if (uri.authority == MediaStore.AUTHORITY) return uri
        if (uri.authority != MEDIA_DOCUMENTS_AUTHORITY) return null
        if (!DocumentsContract.isDocumentUri(context, uri)) return null

        val documentId = runCatching { DocumentsContract.getDocumentId(uri) }.getOrNull() ?: return null
        val parts = documentId.split(":")
        val type = parts.firstOrNull()?.lowercase(Locale.US) ?: return null
        val id = parts.lastOrNull()?.toLongOrNull() ?: return null
        val baseUri = when (type) {
            "image" -> MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            "video" -> MediaStore.Video.Media.EXTERNAL_CONTENT_URI
            "audio" -> MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
            else -> return null
        }
        return ContentUris.withAppendedId(baseUri, id)
    }

    private fun readImageBounds(context: Context, uri: Uri): Pair<Int, Int>? {
        val options = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        return runCatching {
            context.contentResolver.openInputStream(uri)?.use { input ->
                BitmapFactory.decodeStream(input, null, options)
            }
            val width = options.outWidth.takeIf { it > 0 } ?: return@runCatching null
            val height = options.outHeight.takeIf { it > 0 } ?: return@runCatching null
            width to height
        }.getOrNull()
    }

    private fun readExifSummary(context: Context, uri: Uri): ExifSummary {
        return runCatching {
            context.contentResolver.openInputStream(uri)?.use { input ->
                val exif = ExifInterface(input)
                val make = exif.cleanAttribute(ExifInterface.TAG_MAKE)
                val model = exif.cleanAttribute(ExifInterface.TAG_MODEL)
                ExifSummary(
                    hasGps = exif.cleanAttribute(ExifInterface.TAG_GPS_LATITUDE) != null ||
                        exif.cleanAttribute(ExifInterface.TAG_GPS_LONGITUDE) != null,
                    capturedAt = exif.cleanAttribute(ExifInterface.TAG_DATETIME_ORIGINAL)
                        ?: exif.cleanAttribute(ExifInterface.TAG_DATETIME_DIGITIZED)
                        ?: exif.cleanAttribute(ExifInterface.TAG_DATETIME),
                    camera = listOfNotNull(make, model)
                        .joinToString(" ")
                        .takeIf { it.isNotBlank() },
                    software = exif.cleanAttribute(ExifInterface.TAG_SOFTWARE),
                    orientation = exif.getAttributeInt(
                        ExifInterface.TAG_ORIENTATION,
                        ExifInterface.ORIENTATION_UNDEFINED
                    ).takeIf { it != ExifInterface.ORIENTATION_UNDEFINED },
                    description = exif.cleanAttribute(ExifInterface.TAG_IMAGE_DESCRIPTION),
                    artist = exif.cleanAttribute(ExifInterface.TAG_ARTIST),
                    copyright = exif.cleanAttribute(ExifInterface.TAG_COPYRIGHT)
                )
            }
        }.getOrNull() ?: ExifSummary()
    }

    private fun matchingBackups(
        context: Context,
        coreHash: String,
        width: Int?,
        height: Int?
    ): List<MetadataBackupInfo> {
        return backupRoot(context).listFiles()
            .orEmpty()
            .asSequence()
            .filter { it.isDirectory }
            .mapNotNull { directory ->
                val properties = readBackupManifest(directory)
                if (properties == null) {
                    directory.deleteRecursively()
                    return@mapNotNull null
                }
                if (properties.getProperty(PROP_APPLIED) != "true") {
                    directory.deleteRecursively()
                    return@mapNotNull null
                }
                if (properties.getProperty(PROP_CORE_HASH) != coreHash) return@mapNotNull null
                if (!dimensionMatches(properties.getProperty(PROP_WIDTH), width)) return@mapNotNull null
                if (!dimensionMatches(properties.getProperty(PROP_HEIGHT), height)) return@mapNotNull null
                MetadataBackupInfo(
                    id = directory.name,
                    createdAtMillis = properties.getProperty(PROP_CREATED_AT)?.toLongOrNull() ?: 0L,
                    segmentCount = properties.getProperty(PROP_SEGMENT_COUNT)?.toIntOrNull() ?: 0,
                    segmentBytes = properties.getProperty(PROP_SEGMENT_BYTES)?.toLongOrNull() ?: 0L,
                    originalDisplayName = properties.getProperty(PROP_DISPLAY_NAME).orEmpty()
                )
            }
            .sortedByDescending { it.createdAtMillis }
            .toList()
    }

    private fun writeBackupManifest(
        backupDir: File,
        inspection: MetadataInspection,
        result: JpegRewriteResult
    ) {
        val properties = Properties().apply {
            setProperty(PROP_VERSION, BACKUP_VERSION.toString())
            setProperty(PROP_APPLIED, "true")
            setProperty(PROP_DISPLAY_NAME, inspection.displayName)
            setProperty(PROP_CREATED_AT, System.currentTimeMillis().toString())
            setProperty(PROP_CORE_HASH, result.coreHash)
            setProperty(PROP_WIDTH, inspection.width?.toString().orEmpty())
            setProperty(PROP_HEIGHT, inspection.height?.toString().orEmpty())
            setProperty(PROP_FORMAT, inspection.formatLabel)
            setProperty(PROP_SEGMENT_COUNT, result.removedSegmentCount.toString())
            setProperty(PROP_SEGMENT_BYTES, result.removedBytes.toString())
        }
        File(backupDir, MANIFEST_FILE_NAME).outputStream().use { output ->
            properties.store(output, "ZenConverter metadata backup")
        }
    }

    private fun commitBackupDirectory(
        context: Context,
        inspection: MetadataInspection,
        result: JpegRewriteResult,
        stagingBackupDir: File
    ): File {
        val backupDir = createBackupDirectory(context, inspection)
        try {
            for (index in 0 until result.removedSegmentCount) {
                val source = File(stagingBackupDir, segmentFileName(index))
                if (!source.isFile) {
                    throw IOException("Missing staged metadata segment ${source.name}")
                }
                source.copyTo(File(backupDir, segmentFileName(index)), overwrite = true)
            }
            writeBackupManifest(
                backupDir = backupDir,
                inspection = inspection,
                result = result
            )
            return backupDir
        } catch (exception: Throwable) {
            backupDir.deleteRecursively()
            throw exception
        }
    }

    private fun readBackupManifest(backupDir: File): Properties? {
        val manifest = File(backupDir, MANIFEST_FILE_NAME)
        if (!manifest.isFile) return null
        return runCatching {
            Properties().apply {
                manifest.inputStream().use { load(it) }
            }
        }.getOrNull()
    }

    private fun createBackupDirectory(context: Context, inspection: MetadataInspection): File {
        val base = backupRoot(context).apply { mkdirs() }
        val hashPrefix = inspection.coreHash?.take(12).orEmpty().ifBlank { "unknown" }
        val shortId = UUID.randomUUID().toString().take(8)
        return File(base, "${hashPrefix}-${System.currentTimeMillis()}-$shortId").apply {
            mkdirs()
        }
    }

    private fun backupRoot(context: Context): File {
        return File(context.filesDir, "metadata-backups")
    }

    private fun segmentFilesForBackup(
        backupDir: File,
        manifest: Properties
    ): List<File> {
        val count = manifest.getProperty(PROP_SEGMENT_COUNT)?.toIntOrNull() ?: return emptyList()
        return (0 until count).map { index -> File(backupDir, segmentFileName(index)) }
            .filter { it.isFile }
    }

    private fun segmentFileName(index: Int): String {
        return "segment_${index.toString().padStart(3, '0')}.bin"
    }

    private fun removableKind(marker: Int, payload: ByteArray): JpegMetadataSegmentKind? {
        return when (marker) {
            JPEG_MARKER_APP1 -> when {
                payload.startsWithAscii("Exif\u0000\u0000") -> JpegMetadataSegmentKind.Exif
                payload.startsWithAscii("http://ns.adobe.com/xap/1.0/") -> JpegMetadataSegmentKind.Xmp
                payload.startsWithAscii("http://ns.adobe.com/xmp/extension/") -> JpegMetadataSegmentKind.Xmp
                else -> JpegMetadataSegmentKind.Exif
            }
            JPEG_MARKER_APP13 -> JpegMetadataSegmentKind.Iptc
            JPEG_MARKER_COM -> JpegMetadataSegmentKind.Comment
            else -> null
        }
    }

    private fun isStandaloneMarker(marker: Int): Boolean {
        return marker == 0x01 || marker in 0xD0..0xD7
    }

    private fun extensionFor(displayName: String): String {
        return displayName.substringAfterLast('.', missingDelimiterValue = "")
            .takeIf { it.length in 1..12 }
            ?.lowercase(Locale.US)
            .orEmpty()
    }

    private fun formatLabelFor(extension: String, mimeType: String?): String {
        if (extension.isNotBlank()) return extension.uppercase(Locale.US)
        return when (mimeType.orEmpty().lowercase(Locale.US)) {
            "image/jpeg" -> "JPEG"
            "video/quicktime" -> "MOV"
            "video/x-matroska" -> "MKV"
            else -> mimeType?.substringAfter('/')?.uppercase(Locale.US) ?: "Unknown"
        }
    }

    private fun isJpegFamily(extension: String, mimeType: String?): Boolean {
        return extension in JPEG_EXTENSIONS ||
            mimeType.orEmpty().lowercase(Locale.US) == "image/jpeg"
    }

    private fun dimensionMatches(value: String?, actual: Int?): Boolean {
        val expected = value?.toIntOrNull()
        return expected == null || actual == null || expected == actual
    }

    private fun InputStream.readExactBytesOrNull(count: Int): ByteArray? {
        if (count < 0) return null
        val bytes = ByteArray(count)
        var offset = 0
        while (offset < count) {
            val read = read(bytes, offset, count - offset)
            if (read < 0) return null
            offset += read
        }
        return bytes
    }

    private fun copyRemaining(input: InputStream, keep: (ByteArray) -> Unit) {
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            keep(buffer.copyOf(read))
        }
    }

    private fun ByteArray.startsWithAscii(prefix: String): Boolean {
        val bytes = prefix.toByteArray(Charsets.US_ASCII)
        if (size < bytes.size) return false
        return bytes.indices.all { index -> this[index] == bytes[index] }
    }

    private fun ByteArray.toHexString(): String {
        return joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }
    }

    private fun ExifInterface.cleanAttribute(tag: String): String? {
        return getAttribute(tag)?.trim()?.takeIf { it.isNotBlank() }
    }

    private fun Throwable.safeDetail(): String {
        val type = javaClass.simpleName.takeIf { it.isNotBlank() } ?: "Error"
        val text = localizedMessage
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?.replace(Regex("\\s+"), " ")
            ?.take(160)
        return if (text == null) type else "$type: $text"
    }

    private fun Throwable.anyInChain(predicate: (Throwable) -> Boolean): Boolean {
        val seen = HashSet<Throwable>()
        fun visit(throwable: Throwable?): Boolean {
            if (throwable == null || !seen.add(throwable)) return false
            if (predicate(throwable)) return true
            if (throwable.suppressed.any { visit(it) }) return true
            return visit(throwable.cause)
        }
        return visit(this)
    }

    private data class JpegRewriteResult(
        val validJpeg: Boolean,
        val coreHash: String = "",
        val removedSegmentCount: Int = 0,
        val removedBytes: Long = 0L,
        val kinds: Set<JpegMetadataSegmentKind> = emptySet()
    )

    private data class ExifSummary(
        val hasGps: Boolean = false,
        val capturedAt: String? = null,
        val camera: String? = null,
        val software: String? = null,
        val orientation: Int? = null,
        val description: String? = null,
        val artist: String? = null,
        val copyright: String? = null
    )

    private enum class JpegMetadataSegmentKind {
        Exif,
        Xmp,
        Iptc,
        Comment
    }

    private const val TAG = "MetadataPrivacy"
    private const val MEDIA_DOCUMENTS_AUTHORITY = "com.android.providers.media.documents"
    private const val READ_ONLY_MEDIA_MESSAGE = "Media is read-only"
    private const val MEDIASTORE_ACCESS_DENIED_MESSAGE = "has no access to content://media/"
    private const val BACKUP_VERSION = 1
    private const val MANIFEST_FILE_NAME = "manifest.properties"
    private val SAF_TRUNCATING_WRITE_MODES = arrayOf("rwt", "wt")
    private const val PROP_VERSION = "version"
    private const val PROP_APPLIED = "applied"
    private const val PROP_DISPLAY_NAME = "displayName"
    private const val PROP_CREATED_AT = "createdAtMillis"
    private const val PROP_CORE_HASH = "coreHash"
    private const val PROP_WIDTH = "width"
    private const val PROP_HEIGHT = "height"
    private const val PROP_FORMAT = "format"
    private const val PROP_SEGMENT_COUNT = "segmentCount"
    private const val PROP_SEGMENT_BYTES = "segmentBytes"

    private const val JPEG_MARKER_PREFIX = 0xFF
    private const val JPEG_MARKER_SOI = 0xD8
    private const val JPEG_MARKER_EOI = 0xD9
    private const val JPEG_MARKER_SOS = 0xDA
    private const val JPEG_MARKER_APP0 = 0xE0
    private const val JPEG_MARKER_APP1 = 0xE1
    private const val JPEG_MARKER_APP13 = 0xED
    private const val JPEG_MARKER_COM = 0xFE
    private val JPEG_EXTENSIONS = setOf("jpg", "jpeg", "jfif", "jpe")
}
