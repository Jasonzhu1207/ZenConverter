package org.zenconverter.app.archive

import org.apache.commons.compress.archivers.sevenz.SevenZArchiveEntry
import org.apache.commons.compress.archivers.sevenz.SevenZFile
import org.apache.commons.compress.archivers.sevenz.SevenZOutputFile
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream
import org.zenconverter.app.R
import org.zenconverter.app.i18n.LocalizedFailure
import org.zenconverter.app.i18n.localizedText
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Creates and extracts archives locally: ZIP, TAR and 7z are all handled
 * in-process, so nothing leaves the device. Every extraction path shares the
 * same guard that rejects entries escaping the target folder and caps the total
 * written size and entry count.
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

    /** Creates an archive of [sources] in the requested [format]. */
    fun create(format: Format, sources: List<File>, target: File) {
        when (format) {
            Format.Zip -> createZip(sources, target)
            Format.Tar -> createTar(sources, target)
            Format.SevenZip -> createSevenZip(sources, target)
        }
    }

    /** Extracts [archive] using the format implied by its name. */
    fun extract(
        archive: File,
        targetDir: File,
        maxTotalBytes: Long = DEFAULT_MAX_TOTAL_BYTES,
        maxEntries: Int = DEFAULT_MAX_ENTRIES
    ) = when (formatFor(archive.name)) {
        Format.Zip -> extractZip(archive, targetDir, maxTotalBytes, maxEntries)
        Format.Tar -> extractTar(archive, targetDir, maxTotalBytes, maxEntries)
        Format.SevenZip -> extractSevenZip(archive, targetDir, maxTotalBytes, maxEntries)
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

    fun createTar(sources: List<File>, target: File) {
        TarArchiveOutputStream(FileOutputStream(target).buffered()).use { output ->
            output.setLongFileMode(TarArchiveOutputStream.LONGFILE_POSIX)
            sources.forEach { source -> addToTar(output, source, source.name) }
            output.finish()
        }
    }

    private fun addToTar(output: TarArchiveOutputStream, source: File, entryName: String) {
        if (source.isDirectory) {
            output.putArchiveEntry(TarArchiveEntry(source, "$entryName/"))
            output.closeArchiveEntry()
            source.listFiles().orEmpty().forEach { child -> addToTar(output, child, "$entryName/${child.name}") }
            return
        }
        output.putArchiveEntry(TarArchiveEntry(source, entryName))
        source.inputStream().buffered().use { input -> input.copyTo(output) }
        output.closeArchiveEntry()
    }

    fun createSevenZip(sources: List<File>, target: File) {
        SevenZOutputFile(target).use { output ->
            sources.forEach { source -> addToSevenZip(output, source, source.name) }
        }
    }

    private fun addToSevenZip(output: SevenZOutputFile, source: File, entryName: String) {
        if (source.isDirectory) {
            val entry = SevenZArchiveEntry()
            entry.name = "$entryName/"
            entry.isDirectory = true
            output.putArchiveEntry(entry)
            output.closeArchiveEntry()
            source.listFiles().orEmpty().forEach { child -> addToSevenZip(output, child, "$entryName/${child.name}") }
            return
        }
        val entry = output.createArchiveEntry(source, entryName)
        output.putArchiveEntry(entry)
        source.inputStream().buffered().use { input -> copyToSevenZip(input, output) }
        output.closeArchiveEntry()
    }

    private fun copyToSevenZip(input: java.io.InputStream, output: SevenZOutputFile) {
        val buffer = ByteArray(DEFAULT_BUFFER_BYTES)
        while (true) {
            val read = input.read(buffer)
            if (read <= 0) break
            output.write(buffer, 0, read)
        }
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
        val guard = ExtractionGuard(targetDir, maxTotalBytes, maxEntries)
        ZipInputStream(FileInputStream(archive).buffered()).use { input ->
            while (true) {
                val entry = input.nextEntry ?: break
                val destination = guard.beginEntry(entry.name, entry.isDirectory)
                if (!entry.isDirectory) {
                    FileOutputStream(destination).buffered().use { output ->
                        guard.copy(input, output)
                    }
                }
                input.closeEntry()
            }
        }
    }

    fun extractTar(
        archive: File,
        targetDir: File,
        maxTotalBytes: Long = DEFAULT_MAX_TOTAL_BYTES,
        maxEntries: Int = DEFAULT_MAX_ENTRIES
    ) {
        val guard = ExtractionGuard(targetDir, maxTotalBytes, maxEntries)
        TarArchiveInputStream(FileInputStream(archive).buffered()).use { input ->
            while (true) {
                val entry = input.nextEntry ?: break
                // Skip links and devices: only regular files and directories are
                // written, so a symlink cannot be used to escape the folder.
                if (entry.isSymbolicLink || entry.isLink) continue
                val destination = guard.beginEntry(entry.name, entry.isDirectory)
                if (!entry.isDirectory) {
                    FileOutputStream(destination).buffered().use { output ->
                        guard.copy(input, output)
                    }
                }
            }
        }
    }

    fun extractSevenZip(
        archive: File,
        targetDir: File,
        maxTotalBytes: Long = DEFAULT_MAX_TOTAL_BYTES,
        maxEntries: Int = DEFAULT_MAX_ENTRIES
    ) {
        val guard = ExtractionGuard(targetDir, maxTotalBytes, maxEntries)
        SevenZFile(archive).use { input ->
            while (true) {
                val entry: SevenZArchiveEntry = input.nextEntry ?: break
                val destination = guard.beginEntry(entry.name, entry.isDirectory)
                if (!entry.isDirectory) {
                    FileOutputStream(destination).buffered().use { output ->
                        guard.copySevenZip(input, output)
                    }
                }
            }
        }
    }

    /** Entries of a ZIP archive, without extracting any of them. */
    fun listZipEntries(archive: File): List<Entry> {
        val entries = mutableListOf<Entry>()
        // ZipFile reads the central directory, so entry sizes are real rather
        // than the -1 that a streamed ZipInputStream reports.
        ZipFile(archive).use { zip ->
            val enumeration = zip.entries()
            while (enumeration.hasMoreElements()) {
                val entry = enumeration.nextElement()
                entries.add(Entry(entry.name, entry.size, entry.isDirectory))
            }
        }
        return entries
    }

    data class Entry(val name: String, val sizeBytes: Long, val isDirectory: Boolean)

    const val DEFAULT_MAX_TOTAL_BYTES = 2L * 1024 * 1024 * 1024
    const val DEFAULT_MAX_ENTRIES = 20_000
    private const val DEFAULT_BUFFER_BYTES = 64 * 1024

    /**
     * Shared extraction safety net: resolves each entry inside the target folder,
     * rejects traversal, and enforces the total size and entry-count caps.
     */
    private class ExtractionGuard(
        targetDir: File,
        private val maxTotalBytes: Long,
        private val maxEntries: Int
    ) {
        private val root: String
        private var totalBytes = 0L
        private var entryCount = 0

        init {
            if (!targetDir.exists() && !targetDir.mkdirs()) {
                throw LocalizedFailure(localizedText(R.string.archive_could_not_create_output_folder))
            }
            root = targetDir.canonicalFile.path
        }

        /** Validates the entry and returns the file to write, creating dirs. */
        fun beginEntry(name: String, isDirectory: Boolean): File {
            entryCount += 1
            if (entryCount > maxEntries) {
                throw LocalizedFailure(localizedText(R.string.archive_too_many_entries))
            }
            val target = File(root, name)
            if (target.canonicalPath != root && !target.canonicalPath.startsWith(root + File.separator)) {
                throw LocalizedFailure(localizedText(R.string.archive_entry_outside_folder, name))
            }
            if (isDirectory) {
                target.mkdirs()
            } else {
                target.parentFile?.mkdirs()
            }
            return target
        }

        /** Copies one entry, enforcing the expanded-size cap. */
        fun copy(input: java.io.InputStream, output: java.io.OutputStream) {
            val buffer = ByteArray(DEFAULT_BUFFER_BYTES)
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                account(read)
                output.write(buffer, 0, read)
            }
        }

        /** Same as [copy] but reads from a 7z entry, which is not an InputStream. */
        fun copySevenZip(input: SevenZFile, output: java.io.OutputStream) {
            val buffer = ByteArray(DEFAULT_BUFFER_BYTES)
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                account(read)
                output.write(buffer, 0, read)
            }
        }

        private fun account(read: Int) {
            totalBytes += read
            if (totalBytes > maxTotalBytes) {
                throw LocalizedFailure(localizedText(R.string.archive_too_large))
            }
        }
    }
}
