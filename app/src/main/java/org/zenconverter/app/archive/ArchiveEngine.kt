package org.zenconverter.app.archive

import org.zenconverter.app.R
import org.zenconverter.app.i18n.LocalizedFailure
import org.zenconverter.app.i18n.localizedText
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Creates and extracts archives locally. ZIP is handled in-process; TAR and 7z
 * are delegated to FFmpeg when the bundled build exposes them. Nothing leaves
 * the device.
 */
object ArchiveEngine {
    enum class Format(val extension: String) {
        Zip("zip"),
        Tar("tar"),
        SevenZip("7z")
    }

    /** Detects a format from a file name, defaulting to ZIP. */
    fun formatFor(fileName: String): Format = when (fileName.substringAfterLast('.', "").lowercase()) {
        "tar" -> Format.Tar
        "7z" -> Format.SevenZip
        else -> Format.Zip
    }

    fun createZip(sources: List<File>, target: File) {
        ZipOutputStream(FileOutputStream(target).buffered()).use { output ->
            sources.forEach { source -> addToZip(output, source, source.name) }
        }
    }

    private fun addToZip(output: ZipOutputStream, source: File, entryName: String) {
        if (source.isDirectory) {
            output.putNextEntry(ZipEntry("$entryName/"))
            output.closeEntry()
            source.listFiles().orEmpty().forEach { child -> addToZip(output, child, "$entryName/${child.name}") }
            return
        }
        output.putNextEntry(ZipEntry(entryName))
        source.inputStream().buffered().use { input -> input.copyTo(output) }
        output.closeEntry()
    }

    /**
     * Extracts a ZIP into [targetDir]. Entries that would escape the folder are
     * rejected, and the total written size and entry count are capped so a
     * malicious archive cannot fill the device.
     */
    fun extractZip(
        archive: File,
        targetDir: File,
        maxTotalBytes: Long = DEFAULT_MAX_TOTAL_BYTES,
        maxEntries: Int = DEFAULT_MAX_ENTRIES
    ) {
        if (!targetDir.exists() && !targetDir.mkdirs()) {
            throw LocalizedFailure(localizedText(R.string.archive_could_not_create_output_folder))
        }
        val canonicalRoot = targetDir.canonicalFile
        var totalBytes = 0L
        var entryCount = 0
        ZipInputStream(FileInputStream(archive).buffered()).use { input ->
            while (true) {
                val entry = input.nextEntry ?: break
                entryCount += 1
                if (entryCount > maxEntries) {
                    throw LocalizedFailure(localizedText(R.string.archive_too_many_entries))
                }
                val destination = File(targetDir, entry.name)
                if (!destination.canonicalPath.startsWith(canonicalRoot.path + File.separator)) {
                    throw LocalizedFailure(localizedText(R.string.archive_entry_outside_folder, entry.name))
                }
                if (entry.isDirectory) {
                    destination.mkdirs()
                } else {
                    destination.parentFile?.mkdirs()
                    FileOutputStream(destination).buffered().use { output ->
                        val buffer = ByteArray(DEFAULT_BUFFER_BYTES)
                        while (true) {
                            val read = input.read(buffer)
                            if (read <= 0) break
                            totalBytes += read
                            if (totalBytes > maxTotalBytes) {
                                throw LocalizedFailure(localizedText(R.string.archive_too_large))
                            }
                            output.write(buffer, 0, read)
                        }
                    }
                }
                input.closeEntry()
            }
        }
    }

    /** Entries of a ZIP archive, without extracting any of them. */
    fun listZipEntries(archive: File): List<Entry> {
        val entries = mutableListOf<Entry>()
        ZipInputStream(FileInputStream(archive).buffered()).use { input ->
            while (true) {
                val entry = input.nextEntry ?: break
                entries.add(Entry(entry.name, entry.size, entry.isDirectory))
                input.closeEntry()
            }
        }
        return entries
    }

    data class Entry(val name: String, val sizeBytes: Long, val isDirectory: Boolean)

    const val DEFAULT_MAX_TOTAL_BYTES = 2L * 1024 * 1024 * 1024
    const val DEFAULT_MAX_ENTRIES = 20_000
    private const val DEFAULT_BUFFER_BYTES = 64 * 1024
}
