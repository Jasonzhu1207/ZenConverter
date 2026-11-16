package org.zenconverter.app.archive

import java.io.File
import java.util.zip.ZipFile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ArchiveEngineTest {
    @get:Rule
    val folder = TemporaryFolder()

    private fun sourceFiles(): List<File> {
        val dir = folder.newFolder("input")
        File(dir, "one.txt").writeText("first")
        File(dir, "two.txt").writeText("second")
        return dir.listFiles()!!.sortedBy { it.name }
    }

    @Test
    fun createsAndExtractsZip() {
        val target = File(folder.root, "out.zip")
        ArchiveEngine.create(ArchiveEngine.Format.Zip, sourceFiles(), target)
        assertRoundTrip(target, "out.zip")
    }

    @Test
    fun createsAndExtractsTar() {
        val target = File(folder.root, "out.tar")
        ArchiveEngine.create(ArchiveEngine.Format.Tar, sourceFiles(), target)
        assertRoundTrip(target, "out.tar")
    }

    @Test
    fun createsAndExtractsSevenZip() {
        val target = File(folder.root, "out.7z")
        ArchiveEngine.create(ArchiveEngine.Format.SevenZip, sourceFiles(), target)
        assertRoundTrip(target, "out.7z")
    }

    private fun assertRoundTrip(archive: File, name: String) {
        val output = folder.newFolder("extract-${name.replace('.', '-')}")
        ArchiveEngine.extract(archive, output)
        val names = output.walkTopDown().filter { it.isFile }.map { it.name }.toList()
        assertTrue(names.contains("one.txt"))
        assertTrue(names.contains("two.txt"))
    }

    @Test
    fun formatForDetectsExtension() {
        assertEquals(ArchiveEngine.Format.Tar, ArchiveEngine.formatFor("book.tar"))
        assertEquals(ArchiveEngine.Format.SevenZip, ArchiveEngine.formatFor("book.7z"))
        assertEquals(ArchiveEngine.Format.Zip, ArchiveEngine.formatFor("book.zip"))
        assertEquals(ArchiveEngine.Format.Zip, ArchiveEngine.formatFor("book.unknown"))
    }

    @Test
    fun rejectsTraversalEntries() {
        val target = File(folder.root, "evil.zip")
        java.util.zip.ZipOutputStream(target.outputStream()).use { zip ->
            zip.putNextEntry(java.util.zip.ZipEntry("../escape.txt"))
            zip.write("bad".toByteArray())
            zip.closeEntry()
        }
        val output = folder.newFolder("evil-out")
        val failure = runCatching { ArchiveEngine.extract(target, output) }.exceptionOrNull()
        assertTrue(failure != null)
        assertTrue(!File(output.parentFile, "escape.txt").exists())
    }

    @Test
    fun listZipEntriesReportsNamesAndSizes() {
        val target = File(folder.root, "listed.zip")
        ArchiveEngine.create(ArchiveEngine.Format.Zip, sourceFiles(), target)
        val entries = ArchiveEngine.listZipEntries(target)
        assertTrue(entries.any { it.name.endsWith("one.txt") })
        ZipFile(target).use { zip ->
            val stored = zip.getEntry(entries.first { it.name.endsWith("one.txt") }.name)
            assertEquals(stored.size, entries.first { it.name.endsWith("one.txt") }.sizeBytes)
        }
    }
}
