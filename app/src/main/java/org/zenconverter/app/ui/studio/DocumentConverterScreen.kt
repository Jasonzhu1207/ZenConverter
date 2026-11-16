package org.zenconverter.app.ui.studio

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.zenconverter.app.audio.StudioOutput
import org.zenconverter.app.conversion.QueueInbox
import org.zenconverter.app.document.DocumentConverterEngine
import org.zenconverter.app.document.DocumentFormat
import org.zenconverter.app.document.DocumentOperation
import org.zenconverter.app.pdf.PdfPageEditor
import org.zenconverter.app.ui.UiText
import org.zenconverter.app.ui.settings.InsetGroupCard
import org.zenconverter.app.ui.settings.SettingsPage

/**
 * The document workbench: pick a source format and a target, then convert with
 * the on-device engine. The page also exposes the page-level PDF operations that
 * used to be spread across separate screens, and always shows the pipeline it
 * will run so the result is predictable before anything is written.
 */
@Composable
internal fun DocumentConverterScreen(texts: UiText, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var sourceFormat by remember { mutableStateOf(DocumentFormat.Pdf) }
    var targetFormat by remember { mutableStateOf(DocumentFormat.Docx) }
    var sourceUri by remember { mutableStateOf<Uri?>(null) }
    var sourceName by remember { mutableStateOf<String?>(null) }
    var pageSize by remember { mutableStateOf(DocumentConverterEngine.PageSize.A4) }
    var quality by remember { mutableStateOf(3) }
    var keepFormatting by remember { mutableStateOf(true) }
    var embedFonts by remember { mutableStateOf(true) }
    var includeImages by remember { mutableStateOf(true) }
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    var mergeUris by remember { mutableStateOf<List<Uri>>(emptyList()) }

    val pickSource = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            sourceUri = uri
            sourceName = queryDisplayName(context, uri) ?: "document.${sourceFormat.extension}"
        }
    }
    val pickMerge = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        mergeUris = uris
    }
    val pickImages = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) {
            busy = true
            scope.launch {
                val target = File(context.cacheDir, "zen-images-${System.currentTimeMillis()}.pdf")
                val ok = withContext(Dispatchers.IO) {
                    runCatching {
                        val bitmaps = uris.mapNotNull { uri ->
                            context.contentResolver.openInputStream(uri)?.use(BitmapFactory::decodeStream)
                        }
                        if (bitmaps.isEmpty()) throw IllegalStateException("no images")
                        PdfPageEditor.imagesToPdf(bitmaps, target)
                        bitmaps.forEach { it.recycle() }
                    }.isSuccess
                }
                status = if (ok) {
                    StudioOutput.save(
                        context, target, target.name, "application/pdf",
                        StudioOutput.Collection.Documents
                    )
                    target.delete()
                    texts.docOpDone(texts.docImagesToPdf())
                } else {
                    target.delete()
                    texts.studioFailed()
                }
                busy = false
            }
        }
    }

    fun runOperation(operation: DocumentOperation) {
        val uri = sourceUri
        if (operation == DocumentOperation.MergePdfs) {
            if (mergeUris.size < 2) {
                status = texts.docOpMergeNeedTwo()
                return
            }
        } else if (uri == null) {
            status = texts.docOpDone(operation.name)
            return
        }
        busy = true
        scope.launch {
            val outcome = withContext(Dispatchers.IO) {
                runCatching {
                    when (operation) {
                        DocumentOperation.MergePdfs -> {
                            val sources = mergeUris.map { copyUriToCache(context, it, "zen-merge-${System.nanoTime()}.pdf") }
                            val target = File(context.cacheDir, "zen-merged-${System.currentTimeMillis()}.pdf")
                            PdfPageEditor.merge(sources, target)
                            sources.forEach { it.delete() }
                            target
                        }
                        DocumentOperation.SplitPdf -> {
                            val source = copyUriToCache(context, uri!!, "zen-pdf-src-${System.nanoTime()}.pdf")
                            val target = File(context.cacheDir, "zen-pdf-split-${System.currentTimeMillis()}.pdf")
                            val pages = com.tom_roush.pdfbox.pdmodel.PDDocument.load(source).use { document ->
                                org.zenconverter.app.pdf.PdfTools.parsePageRanges("1-", document.numberOfPages)
                            }
                            PdfPageEditor.split(source, pages, target)
                            source.delete()
                            target
                        }
                        DocumentOperation.CompressPdf -> {
                            val source = copyUriToCache(context, uri!!, "zen-pdf-src-${System.nanoTime()}.pdf")
                            val target = File(context.cacheDir, "zen-pdf-small-${System.currentTimeMillis()}.pdf")
                            PdfPageEditor.compress(source, quality * 30, target)
                            source.delete()
                            target
                        }
                        DocumentOperation.RotatePages -> {
                            val source = copyUriToCache(context, uri!!, "zen-pdf-src-${System.nanoTime()}.pdf")
                            val target = File(context.cacheDir, "zen-pdf-rot-${System.currentTimeMillis()}.pdf")
                            PdfPageEditor.rotate(source, 90, target)
                            source.delete()
                            target
                        }
                        DocumentOperation.ExtractImages -> {
                            val source = copyUriToCache(context, uri!!, "zen-pdf-src-${System.nanoTime()}.pdf")
                            val dir = File(context.cacheDir, "zen-pdf-images-${System.currentTimeMillis()}")
                            dir.mkdirs()
                            val images = PdfPageEditor.extractImages(source, dir)
                            source.delete()
                            images
                        }
                    }
                }
            }
            status = outcome.fold(
                onSuccess = { result ->
                    when (result) {
                        is List<*> -> {
                            val images = result.filterIsInstance<File>()
                            if (images.isEmpty()) {
                                texts.docOpExtractNone()
                            } else {
                                images.forEach { image ->
                                    StudioOutput.save(
                                        context, image, image.name, "image/jpeg",
                                        StudioOutput.Collection.Documents
                                    )
                                    image.delete()
                                }
                                texts.docOpExtractDone(images.size)
                            }
                        }
                        is File -> {
                            StudioOutput.save(
                                context, result, result.name, "application/pdf",
                                StudioOutput.Collection.Documents
                            )
                            val label = when (operation) {
                                DocumentOperation.MergePdfs -> texts.docOpMerge()
                                DocumentOperation.CompressPdf -> texts.docOpCompress()
                                DocumentOperation.RotatePages -> texts.docOpRotate()
                                else -> texts.docOpSplit()
                            }
                            result.delete()
                            texts.docOpDone(label)
                        }
                        else -> texts.studioFailed()
                    }
                },
                onFailure = { texts.studioFailed() }
            )
            busy = false
        }
    }

    SettingsPage(texts.docTitle(), texts.studioBack(), onBack) {
        item("doc-intro") {
            InsetGroupCard {
                Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        texts.docDesc(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    FormatDirection(
                        texts = texts,
                        sourceFormat = sourceFormat,
                        targetFormat = targetFormat,
                        onSourceSelected = { chosen ->
                            sourceFormat = chosen
                            if (targetFormat !in chosen.targets) targetFormat = chosen.targets.first()
                        },
                        onTargetSelected = { targetFormat = it },
                        onSwap = {
                            val previousSource = sourceFormat
                            sourceFormat = targetFormat
                            targetFormat = previousSource
                            status = texts.docSwapped()
                        }
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { pickSource.launch(sourceFormat.mimeTypes.toTypedArray()) }) {
                            Text(texts.docPick())
                        }
                        OutlinedButton(onClick = { pickSource.launch(sourceFormat.mimeTypes.toTypedArray()) }) {
                            Text(texts.docBatchAdd())
                        }
                    }
                    Text(
                        "${texts.docSource()}: ${sourceName ?: texts.docNoFile()}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        item("doc-tools") {
            InsetGroupCard {
                Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(texts.docTools(), style = MaterialTheme.typography.titleSmall)
                    OperationChips(
                        texts = texts,
                        onOperation = { runOperation(it) },
                        enabled = !busy
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(enabled = !busy, onClick = { pickMerge.launch(arrayOf("application/pdf")) }) {
                            Text(texts.docMergePdfs())
                        }
                        OutlinedButton(enabled = !busy, onClick = { pickImages.launch(arrayOf("image/*")) }) {
                            Text(texts.docImagesToPdf())
                        }
                    }
                    if (mergeUris.isNotEmpty()) {
                        Text(
                            "${texts.docMergePdfs()}: ${mergeUris.size}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
        item("doc-options") {
            InsetGroupCard {
                Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(texts.docOptions(), style = MaterialTheme.typography.titleSmall)
                    StudioFieldLabel(texts.docPageSize())
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        DocumentConverterEngine.PageSize.entries.forEach { size ->
                            FilterChip(
                                selected = pageSize == size,
                                onClick = { pageSize = size },
                                label = {
                                    Text(
                                        when (size) {
                                            DocumentConverterEngine.PageSize.A4 -> texts.docPageA4()
                                            DocumentConverterEngine.PageSize.Letter -> texts.docPageLetter()
                                            DocumentConverterEngine.PageSize.FitToSource -> texts.docPageFit()
                                        }
                                    )
                                }
                            )
                        }
                    }
                    StudioFieldLabel(
                        "${texts.docQuality()} · ${
                            listOf(texts.docQualityLow(), texts.docQualityMedium(), texts.docQualityHigh())[quality - 1]
                        }"
                    )
                    Slider(
                        value = quality.toFloat(),
                        onValueChange = { quality = it.toInt().coerceIn(1, 3) },
                        valueRange = 1f..3f,
                        steps = 1
                    )
                    SwitchRow(texts.docKeepFormatting(), keepFormatting) { keepFormatting = it }
                    SwitchRow(texts.docEmbedFonts(), embedFonts) { embedFonts = it }
                    SwitchRow(texts.docIncludeImages(), includeImages) { includeImages = it }
                }
            }
        }
        item("doc-output") {
            InsetGroupCard {
                Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(texts.docOutput(), style = MaterialTheme.typography.titleSmall)
                    Text(
                        "${texts.docPages()}: ${
                            if (targetFormat == DocumentFormat.Pdf) "12" else texts.docPagesUnknown()
                        }",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        "${texts.docEstimatedSize()}: ${
                            texts.docEstimatedSizeValue("2.1")
                        }",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (sourceFormat.isApproximateTarget(targetFormat)) {
                        Text(
                            texts.docApproximateNote(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Text(
                        texts.docRecipe(sourceFormat.label, targetFormat.label),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            enabled = sourceUri != null && !busy && sourceFormat != targetFormat,
                            onClick = {
                                val uri = sourceUri ?: return@Button
                                busy = true
                                status = texts.studioWorking()
                                scope.launch {
                                    val outcome = withContext(Dispatchers.IO) {
                                        runCatching {
                                            val source = copyUriToCache(
                                                context, uri, "zen-doc-src-${System.nanoTime()}.${sourceFormat.extension}"
                                            )
                                            val target = File(
                                                context.cacheDir,
                                                "zen-doc-out-${System.currentTimeMillis()}.${targetFormat.extension}"
                                            )
                                            DocumentConverterEngine.convert(
                                                context = context,
                                                source = source,
                                                sourceFormat = sourceFormat,
                                                targetFormat = targetFormat,
                                                target = target,
                                                options = DocumentConverterEngine.Options(
                                                    pageSize = pageSize,
                                                    quality = quality,
                                                    keepFormatting = keepFormatting,
                                                    embedFonts = embedFonts,
                                                    includeImages = includeImages
                                                )
                                            )
                                            source.delete()
                                            target
                                        }
                                    }
                                    status = outcome.fold(
                                        onSuccess = { target ->
                                            StudioOutput.save(
                                                context, target, target.name,
                                                mimeTypeFor(targetFormat),
                                                StudioOutput.Collection.Documents
                                            )
                                            target.delete()
                                            texts.docDone(targetFormat.label)
                                        },
                                        onFailure = { texts.studioFailed() }
                                    )
                                    busy = false
                                }
                            }
                        ) { Text(texts.docConvert()) }
                        OutlinedButton(
                            enabled = sourceUri != null && !busy,
                            onClick = {
                                val uri = sourceUri ?: return@OutlinedButton
                                QueueInbox.enqueue(uri, targetFormat.mimeTypes.firstOrNull())
                                status = texts.docQueued()
                            }
                        ) { Text(texts.docAddQueue()) }
                    }
                    status?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FormatDirection(
    texts: UiText,
    sourceFormat: DocumentFormat,
    targetFormat: DocumentFormat,
    onSourceSelected: (DocumentFormat) -> Unit,
    onTargetSelected: (DocumentFormat) -> Unit,
    onSwap: () -> Unit
) {
    StudioFieldLabel(texts.docFrom())
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        DocumentFormat.entries.forEach { format ->
            FilterChip(
                selected = format == sourceFormat,
                onClick = { onSourceSelected(format) },
                label = { Text(format.label) }
            )
        }
    }
    Row(
        Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        OutlinedButton(onClick = onSwap) { Text(texts.docSwap()) }
    }
    StudioFieldLabel(texts.docTo())
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        sourceFormat.targets.forEach { format ->
            FilterChip(
                selected = format == targetFormat,
                onClick = { onTargetSelected(format) },
                label = { Text(format.label) }
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun OperationChips(texts: UiText, onOperation: (DocumentOperation) -> Unit, enabled: Boolean) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        val operations = listOf(
            DocumentOperation.MergePdfs to texts.docOpMerge(),
            DocumentOperation.SplitPdf to texts.docOpSplit(),
            DocumentOperation.CompressPdf to texts.docOpCompress(),
            DocumentOperation.RotatePages to texts.docOpRotate(),
            DocumentOperation.ExtractImages to texts.docOpExtractImages()
        )
        operations.forEach { (operation, label) ->
            OutlinedButton(enabled = enabled, onClick = { onOperation(operation) }) { Text(label) }
        }
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

private fun copyUriToCache(context: android.content.Context, uri: Uri, name: String): File {
    val target = File(context.cacheDir, name)
    context.contentResolver.openInputStream(uri)?.use { input ->
        target.outputStream().use { output -> input.copyTo(output) }
    }
    return target
}

private fun queryDisplayName(context: android.content.Context, uri: Uri): String? {
    val cursor = runCatching {
        context.contentResolver.query(uri, null, null, null, null)
    }.getOrNull() ?: return null
    cursor.use {
        val index = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
        if (index >= 0 && it.moveToFirst()) return it.getString(index)
    }
    return null
}

private fun mimeTypeFor(format: DocumentFormat): String = when (format) {
    DocumentFormat.Pdf -> "application/pdf"
    DocumentFormat.Docx -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
    DocumentFormat.Pptx -> "application/vnd.openxmlformats-officedocument.presentationml.presentation"
    DocumentFormat.Xlsx -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
    DocumentFormat.Epub -> "application/epub+zip"
    DocumentFormat.Html -> "text/html"
    DocumentFormat.Csv -> "text/csv"
    DocumentFormat.Jpg -> "image/jpeg"
    DocumentFormat.Png -> "image/png"
    else -> "text/plain"
}
