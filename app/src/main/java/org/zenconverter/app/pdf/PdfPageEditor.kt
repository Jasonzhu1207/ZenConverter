package org.zenconverter.app.pdf

import android.graphics.Bitmap
import com.tom_roush.pdfbox.cos.COSName
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.graphics.image.JPEGFactory
import com.tom_roush.pdfbox.pdmodel.graphics.image.PDImageXObject
import java.io.File

/**
 * Page-level PDF edits backed by PDFBox. Every operation writes a fresh
 * document, so the source is never modified.
 */
object PdfPageEditor {
    /** Writes the zero-based [pages] of [source] to [target] as a new PDF. */
    fun split(source: File, pages: List<Int>, target: File) {
        PDDocument.load(source).use { document ->
            PDDocument().use { output ->
                pages.filter { it in 0 until document.numberOfPages }.forEach { index ->
                    output.importPage(document.getPage(index))
                }
                output.save(target)
            }
        }
    }

    /** Concatenates [sources] in order into a single PDF at [target]. */
    fun merge(sources: List<File>, target: File) {
        PDDocument().use { output ->
            sources.forEach { source ->
                PDDocument.load(source).use { document ->
                    for (index in 0 until document.numberOfPages) {
                        output.importPage(document.getPage(index))
                    }
                }
            }
            output.save(target)
        }
    }

    /** Rotates every page of [source] clockwise by [degrees] (a multiple of 90). */
    fun rotate(source: File, degrees: Int, target: File) {
        val normalized = ((degrees % 360) + 360) % 360
        PDDocument.load(source).use { document ->
            for (index in 0 until document.numberOfPages) {
                val page = document.getPage(index)
                page.rotation = (page.rotation + normalized) % 360
            }
            document.save(target)
        }
    }

    /**
     * Writes [bitmaps] onto fresh pages of [target], one page per image. Used by
     * the images-to-PDF direction of the Document converter.
     */
    fun imagesToPdf(bitmaps: List<Bitmap>, target: File) {
        PDDocument().use { document ->
            bitmaps.forEach { bitmap ->
                val image = JPEGFactory.createFromImage(document, bitmap, 0.92f)
                val page = PDPage(PDRectangle(image.width.toFloat(), image.height.toFloat()))
                document.addPage(page)
                PDPageContentStream(document, page).use { stream ->
                    stream.drawImage(image, 0f, 0f, image.width.toFloat(), image.height.toFloat())
                }
            }
            document.save(target)
        }
    }

    /**
     * Re-encodes every embedded bitmap at [quality] (1-100) and writes the result
     * to [target]. Vector text is untouched, so the win is largest on scanned
     * documents where images dominate the file size.
     */
    fun compress(source: File, quality: Int, target: File) {
        val clamped = quality.coerceIn(1, 100)
        PDDocument.load(source).use { document ->
            document.documentCatalog.pages.forEach { page ->
                val resources = page.resources ?: return@forEach
                resources.xObjectNames.toList().forEach { name ->
                    val xObject = runCatching { resources.getXObject(name) }.getOrNull()
                    if (xObject is PDImageXObject) {
                        val bitmap = runCatching { xObject.image }.getOrNull() ?: return@forEach
                        val recompressed = runCatching {
                            JPEGFactory.createFromImage(document, bitmap, clamped / 100f)
                        }.getOrNull() ?: return@forEach
                        resources.put(name, recompressed)
                    }
                }
            }
            document.save(target)
        }
    }

    /** Writes every embedded image in [source] into [outputDir] as a JPEG file. */
    fun extractImages(source: File, outputDir: File): List<File> {
        val written = mutableListOf<File>()
        PDDocument.load(source).use { document ->
            var counter = 0
            document.documentCatalog.pages.forEach { page ->
                val resources = page.resources ?: return@forEach
                resources.xObjectNames.forEach { name: COSName ->
                    val xObject = runCatching { resources.getXObject(name) }.getOrNull()
                    if (xObject is PDImageXObject) {
                        val bitmap = runCatching { xObject.image }.getOrNull() ?: return@forEach
                        counter += 1
                        val output = File(outputDir, "image-$counter.jpg")
                        output.outputStream().use { stream ->
                            bitmap.compress(Bitmap.CompressFormat.JPEG, 92, stream)
                        }
                        bitmap.recycle()
                        written += output
                    }
                }
            }
        }
        return written
    }
}
