package org.zenconverter.app.markdown

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Build
import android.text.Layout
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.StaticLayout
import android.text.TextPaint
import android.text.style.AbsoluteSizeSpan
import android.text.style.BackgroundColorSpan
import android.text.style.ForegroundColorSpan
import android.text.style.MetricAffectingSpan
import android.text.style.ReplacementSpan
import android.text.style.StrikethroughSpan
import android.text.style.StyleSpan
import android.text.style.SubscriptSpan
import android.text.style.SuperscriptSpan
import android.text.style.TypefaceSpan
import android.text.style.UnderlineSpan
import com.tom_roush.pdfbox.cos.COSArray
import com.tom_roush.pdfbox.cos.COSInteger
import com.tom_roush.pdfbox.cos.COSName
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.interactive.action.PDActionURI
import com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAnnotationLink
import com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDBorderStyleDictionary
import com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.destination.PDPageXYZDestination
import com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.outline.PDDocumentOutline
import com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.outline.PDOutlineItem
import org.zenconverter.app.conversion.MarkdownExportOptions
import java.io.File
import java.io.FileOutputStream
import java.util.Locale
import kotlin.math.roundToInt

internal object MarkdownPdfRenderer {
    private const val LAYOUT_SCALE = 2f

    private const val MARGIN_X_PT = 44f
    private const val MARGIN_TOP_PT = 46f
    private const val MARGIN_BOTTOM_PT = 52f
    private const val FOOTER_Y_OFFSET_PT = 22f

    private const val COLOR_TEXT_PRIMARY = 0xFF1F2328.toInt()
    private const val COLOR_TEXT_MUTED = 0xFF57606A.toInt()
    private const val COLOR_LINK = 0xFF0969DA.toInt()
    private const val COLOR_CODE_TEXT = 0xFF24292F.toInt()
    private const val COLOR_CODE_PILL_BG = 0xFFEFF1F3.toInt()
    private const val COLOR_CODE_BLOCK_BG = 0xFFF6F8FA.toInt()
    private const val COLOR_BORDER = 0xFFD0D7DE.toInt()
    private const val COLOR_RULE = 0xFFD8DEE4.toInt()
    private const val COLOR_MARK_BG = 0xFFFFF3B0.toInt()
    private const val COLOR_KBD_BG = 0xFFF6F8FA.toInt()
    private const val COLOR_QUOTE_BAR = 0xFFD0D7DE.toInt()
    private const val COLOR_TABLE_HEADER_BG = 0xFFF6F8FA.toInt()
    private const val COLOR_TABLE_STRIPE_BG = 0xFFFAFBFC.toInt()
    private const val COLOR_CHECKBOX_CHECKED = 0xFF0969DA.toInt()
    private const val COLOR_VIDEO_BG = 0xFF1B1F24.toInt()
    private const val COLOR_VIDEO_PLAY_BG = 0xB3000000.toInt()
    private const val COLOR_BADGE_BG = 0xFFEFF3F6.toInt()

    fun render(
        context: Context,
        document: ParsedMarkdownDocument,
        options: MarkdownExportOptions,
        outputFile: File,
        workDir: File,
        isCancelled: () -> Boolean,
        onProgress: (Float) -> Unit,
        ffmpegExtractFrame: (videoUri: Uri?, localPath: String?, outputJpg: File) -> Boolean
    ) {
        workDir.mkdirs()
        val mediaCacheDir = File(workDir, "media")
        val rawPdfFile = File(workDir, "raw_vector.pdf")

        try {
            val resolver = MarkdownMediaResolver(
                context = context,
                options = options,
                mediaCacheDir = mediaCacheDir,
                isCancelled = isCancelled,
                ffmpegExtractFrame = ffmpegExtractFrame
            )
            onProgress(0.05f)
            val mediaCollection = resolver.resolveAll(document, onProgress)
            if (isCancelled()) return

            val pageWidthPt = options.pageSize.widthPt.toFloat()
            val pageHeightPt = options.pageSize.heightPt.toFloat()
            val bodyPt = options.fontScale.bodyPt

            val engine = LayoutEngine(
                pageWidthPt = pageWidthPt,
                pageHeightPt = pageHeightPt,
                bodyPt = bodyPt,
                media = mediaCollection
            )
            val plan = engine.buildPlan(document, isCancelled)
            if (isCancelled()) return
            onProgress(0.42f)

            writeVectorPdf(
                plan = plan,
                pageWidthPt = pageWidthPt.roundToInt(),
                pageHeightPt = pageHeightPt.roundToInt(),
                bodyPt = bodyPt,
                rawPdfFile = rawPdfFile,
                isCancelled = isCancelled,
                onProgress = onProgress
            )
            if (isCancelled()) return
            onProgress(0.84f)

            postProcessWithPdfBox(
                rawPdfFile = rawPdfFile,
                outputFile = outputFile,
                pageHeightPt = pageHeightPt,
                links = plan.links,
                outlines = plan.outlines,
                title = document.title,
                author = document.author,
                isCancelled = isCancelled
            )
            onProgress(0.95f)
        } finally {
            rawPdfFile.delete()
            mediaCacheDir.deleteRecursively()
        }
    }

    private data class PdfLinkAnnotation(
        val pageIndex: Int,
        val leftPt: Float,
        val topPt: Float,
        val rightPt: Float,
        val bottomPt: Float,
        val url: String
    )

    private data class PdfOutlineEntry(
        val level: Int,
        val title: String,
        val pageIndex: Int,
        val topPt: Float
    )

    private sealed interface PageCommand {
        data class DrawRect(
            val rectPt: RectF,
            val fillColor: Int,
            val strokeColor: Int? = null,
            val strokeWidthPt: Float = 0.75f,
            val cornerRadiusPt: Float = 0f
        ) : PageCommand

        data class DrawLine(
            val x1Pt: Float,
            val y1Pt: Float,
            val x2Pt: Float,
            val y2Pt: Float,
            val color: Int,
            val strokeWidthPt: Float = 0.75f
        ) : PageCommand

        data class DrawCheckbox(
            val xPt: Float,
            val yPt: Float,
            val sizePt: Float,
            val checked: Boolean
        ) : PageCommand

        data class DrawTextSlice(
            val layout: StaticLayout,
            val xPt: Float,
            val yPt: Float,
            val startLine: Int,
            val endLine: Int
        ) : PageCommand

        data class DrawImageCard(
            val file: File,
            val rectPt: RectF,
            val cornerRadiusPt: Float = 4f
        ) : PageCommand

        data class DrawVideoCard(
            val frameFile: File?,
            val rectPt: RectF,
            val title: String?,
            val durationLabel: String?,
            val urlHint: String?
        ) : PageCommand

        data class DrawMediaBadgeOrFallback(
            val rectPt: RectF,
            val label: String,
            val sublabel: String?,
            val isLink: Boolean
        ) : PageCommand
    }

    private class PagePlan {
        val commands = mutableListOf<PageCommand>()
    }

    private data class LayoutPlan(
        val pages: List<PagePlan>,
        val links: List<PdfLinkAnnotation>,
        val outlines: List<PdfOutlineEntry>
    )

    private class LinkTagSpan(val url: String) : TextPaintSpan() {
        override fun updateDrawState(tp: TextPaint) {
            tp.color = COLOR_LINK
            tp.isUnderlineText = true
        }
    }

    private abstract class TextPaintSpan : MetricAffectingSpan() {
        override fun updateMeasureState(textPaint: TextPaint) = Unit
    }

    private class WeightTypefaceSpan(
        private val family: MdFontFamily?,
        private val weight: Int,
        private val italic: Boolean
    ) : MetricAffectingSpan() {
        override fun updateMeasureState(textPaint: TextPaint) = apply(textPaint)
        override fun updateDrawState(tp: TextPaint) = apply(tp)

        private fun apply(paint: TextPaint) {
            val base = when (family) {
                MdFontFamily.Monospace -> Typeface.MONOSPACE
                MdFontFamily.Serif -> Typeface.SERIF
                MdFontFamily.Sans, null -> Typeface.SANS_SERIF
            }
            paint.typeface = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                Typeface.create(base, weight.coerceIn(100, 900), italic)
            } else {
                val style = when {
                    weight >= 600 && italic -> Typeface.BOLD_ITALIC
                    weight >= 600 -> Typeface.BOLD
                    italic -> Typeface.ITALIC
                    else -> Typeface.NORMAL
                }
                Typeface.create(base, style)
            }
        }
    }

    private class InlinePillSpan(
        private val bgColor: Int,
        private val borderColor: Int?,
        private val textColor: Int,
        private val scaleFactor: Float,
        private val monospace: Boolean
    ) : ReplacementSpan() {
        private val padX = 5.5f * LAYOUT_SCALE
        private val padY = 1.5f * LAYOUT_SCALE

        override fun getSize(
            paint: Paint,
            text: CharSequence,
            start: Int,
            end: Int,
            fm: Paint.FontMetricsInt?
        ): Int {
            val workPaint = configurePaint(paint)
            val textWidth = workPaint.measureText(text, start, end)
            if (fm != null) {
                val pfm = workPaint.fontMetricsInt
                fm.ascent = pfm.ascent - padY.roundToInt()
                fm.descent = pfm.descent + padY.roundToInt()
                fm.top = fm.ascent
                fm.bottom = fm.descent
            }
            return (textWidth + padX * 2f).roundToInt()
        }

        override fun draw(
            canvas: Canvas,
            text: CharSequence,
            start: Int,
            end: Int,
            x: Float,
            top: Int,
            y: Int,
            bottom: Int,
            paint: Paint
        ) {
            val workPaint = configurePaint(paint)
            val textWidth = workPaint.measureText(text, start, end)
            val fontMetrics = workPaint.fontMetrics
            val boxTop = y + fontMetrics.ascent - padY * 0.6f
            val boxBottom = y + fontMetrics.descent + padY * 0.6f
            val rect = RectF(x, boxTop, x + textWidth + padX * 2f, boxBottom)
            val radius = 3.5f * LAYOUT_SCALE

            val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = bgColor
                style = Paint.Style.FILL
            }
            canvas.drawRoundRect(rect, radius, radius, bgPaint)

            if (borderColor != null) {
                val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = borderColor
                    style = Paint.Style.STROKE
                    strokeWidth = 0.8f * LAYOUT_SCALE
                }
                canvas.drawRoundRect(rect, radius, radius, borderPaint)
            }

            canvas.drawText(text, start, end, x + padX, y.toFloat(), workPaint)
        }

        private fun configurePaint(base: Paint): Paint {
            return Paint(base).apply {
                color = textColor
                textSize = base.textSize * scaleFactor
                if (monospace) {
                    typeface = Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL)
                }
            }
        }
    }

    private class InlineBitmapSpan(
        private val file: File?,
        private val widthPt: Float,
        private val heightPt: Float,
        private val fallbackLabel: String?,
        private val bitmapProvider: (File) -> Bitmap?
    ) : ReplacementSpan() {
        private val width2x = widthPt * LAYOUT_SCALE
        private val height2x = heightPt * LAYOUT_SCALE

        override fun getSize(
            paint: Paint,
            text: CharSequence,
            start: Int,
            end: Int,
            fm: Paint.FontMetricsInt?
        ): Int {
            if (fm != null) {
                val hInt = height2x.roundToInt()
                val currentAscent = -hInt + (paint.fontMetricsInt.descent / 2)
                if (currentAscent < fm.ascent) fm.ascent = currentAscent
                if (currentAscent < fm.top) fm.top = currentAscent
                if (paint.fontMetricsInt.descent > fm.descent) fm.descent = paint.fontMetricsInt.descent
                if (paint.fontMetricsInt.bottom > fm.bottom) fm.bottom = paint.fontMetricsInt.bottom
            }
            return (width2x + 4f).roundToInt()
        }

        override fun draw(
            canvas: Canvas,
            text: CharSequence,
            start: Int,
            end: Int,
            x: Float,
            top: Int,
            y: Int,
            bottom: Int,
            paint: Paint
        ) {
            val drawTop = y - height2x + paint.fontMetrics.descent * 0.5f
            val rect = RectF(x, drawTop, x + width2x, drawTop + height2x)
            val bmp = file?.let { bitmapProvider(it) }
            if (bmp != null) {
                val bmpPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
                canvas.drawBitmap(bmp, null, rect, bmpPaint)
            } else {
                val pillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = COLOR_BADGE_BG
                    style = Paint.Style.FILL
                }
                val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = COLOR_BORDER
                    style = Paint.Style.STROKE
                    strokeWidth = 0.7f * LAYOUT_SCALE
                }
                val radius = 3.5f * LAYOUT_SCALE
                canvas.drawRoundRect(rect, radius, radius, pillPaint)
                canvas.drawRoundRect(rect, radius, radius, borderPaint)
                if (!fallbackLabel.isNullOrBlank()) {
                    val labelPaint = TextPaint(paint).apply {
                        color = COLOR_TEXT_PRIMARY
                        textSize = 8.5f * LAYOUT_SCALE
                        typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
                    }
                    val textW = labelPaint.measureText(fallbackLabel)
                    val tx = rect.left + (rect.width() - textW).coerceAtLeast(4f) / 2f
                    val ty = rect.centerY() - (labelPaint.ascent() + labelPaint.descent()) / 2f
                    canvas.save()
                    canvas.clipRect(rect)
                    canvas.drawText(fallbackLabel, tx, ty, labelPaint)
                    canvas.restore()
                }
            }
        }
    }

    private class LayoutEngine(
        private val pageWidthPt: Float,
        private val pageHeightPt: Float,
        private val bodyPt: Float,
        private val media: MarkdownMediaCollection
    ) {
        private val contentWidthPt = pageWidthPt - MARGIN_X_PT * 2f
        private val contentTopPt = MARGIN_TOP_PT
        private val contentBottomPt = pageHeightPt - MARGIN_BOTTOM_PT

        private val pages = mutableListOf(PagePlan())
        private val links = mutableListOf<PdfLinkAnnotation>()
        private val outlines = mutableListOf<PdfOutlineEntry>()

        private var pageIndex = 0
        private var cursorY = contentTopPt

        fun buildPlan(document: ParsedMarkdownDocument, isCancelled: () -> Boolean): LayoutPlan {
            val blocks = document.blocks
            for (i in blocks.indices) {
                if (isCancelled()) break
                val block = blocks[i]
                val nextBlock = blocks.getOrNull(i + 1)
                layoutBlock(
                    block = block,
                    nextBlock = nextBlock,
                    leftPt = MARGIN_X_PT,
                    widthPt = contentWidthPt,
                    isCancelled = isCancelled
                )
            }
            if (pages.size > 1 && pages.last().commands.isEmpty()) {
                pages.removeAt(pages.lastIndex)
            }
            return LayoutPlan(pages = pages, links = links, outlines = outlines)
        }

        private fun currentPage(): PagePlan = pages[pageIndex]

        private fun remainingHeightPt(): Float = (contentBottomPt - cursorY).coerceAtLeast(0f)

        private fun newPage() {
            pages.add(PagePlan())
            pageIndex = pages.lastIndex
            cursorY = contentTopPt
        }

        private fun ensureSpaceOrNewPage(neededPt: Float) {
            if (neededPt > remainingHeightPt() && cursorY > contentTopPt + 1f) {
                newPage()
            }
        }

        private fun layoutBlock(
            block: MdBlock,
            nextBlock: MdBlock?,
            leftPt: Float,
            widthPt: Float,
            isCancelled: () -> Boolean
        ) {
            if (isCancelled()) return
            when (block) {
                is MdBlock.FrontMatterBlock -> layoutFrontMatter(block, leftPt, widthPt)
                is MdBlock.HeadingBlock -> layoutHeading(block, nextBlock, leftPt, widthPt)
                is MdBlock.ParagraphBlock -> layoutParagraph(block, leftPt, widthPt, basePt = bodyPt, spacingAfterPt = bodyPt * 0.72f, nextBlock = nextBlock)
                is MdBlock.CodeBlock -> layoutCodeBlock(block, leftPt, widthPt)
                is MdBlock.BlockQuoteBlock -> layoutBlockQuote(block, leftPt, widthPt, isCancelled)
                is MdBlock.ListBlock -> layoutList(block, leftPt, widthPt, isCancelled, nextBlock = nextBlock)
                is MdBlock.TableBlock -> layoutTable(block, leftPt, widthPt, isCancelled)
                is MdBlock.ThematicBreakBlock -> layoutThematicBreak(leftPt, widthPt)
                is MdBlock.ImageBlock -> layoutImageBlock(block, leftPt, widthPt)
                is MdBlock.VideoBlock -> layoutVideoBlock(block, leftPt, widthPt)
                is MdBlock.DetailsBlock -> layoutDetailsBlock(block, leftPt, widthPt, isCancelled)
                is MdBlock.MathBlock -> layoutMathBlock(block, leftPt, widthPt)
                is MdBlock.FootnotesSectionBlock -> layoutFootnotes(block, leftPt, widthPt)
            }
        }

        private fun layoutFrontMatter(block: MdBlock.FrontMatterBlock, leftPt: Float, widthPt: Float) {
            val visibleEntries = block.entries.take(8)
            if (visibleEntries.isEmpty()) return
            val padding = 10f
            val rowLayouts = visibleEntries.map { (k, v) ->
                val ssb = SpannableStringBuilder()
                val keyStart = 0
                ssb.append("$k: ")
                ssb.setSpan(StyleSpan(Typeface.BOLD), keyStart, ssb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                ssb.setSpan(ForegroundColorSpan(COLOR_TEXT_MUTED), keyStart, ssb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                val valStart = ssb.length
                ssb.append(v)
                ssb.setSpan(ForegroundColorSpan(COLOR_TEXT_PRIMARY), valStart, ssb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                buildStaticLayout(
                    text = ssb,
                    widthPt = widthPt - padding * 2f,
                    baseSizePt = bodyPt * 0.86f,
                    baseColor = COLOR_TEXT_PRIMARY,
                    lineSpacingMult = 1.25f
                )
            }
            val totalHeightPt = padding * 2f + rowLayouts.sumOf { (it.height / LAYOUT_SCALE).toDouble() }.toFloat() +
                (rowLayouts.size - 1).coerceAtLeast(0) * 3f
            ensureSpaceOrNewPage(totalHeightPt)
            val boxRect = RectF(leftPt, cursorY, leftPt + widthPt, cursorY + totalHeightPt)
            currentPage().commands.add(
                PageCommand.DrawRect(
                    rectPt = boxRect,
                    fillColor = COLOR_CODE_BLOCK_BG,
                    strokeColor = COLOR_BORDER,
                    strokeWidthPt = 0.6f,
                    cornerRadiusPt = 5f
                )
            )
            var y = cursorY + padding
            for (layout in rowLayouts) {
                currentPage().commands.add(PageCommand.DrawTextSlice(layout, leftPt + padding, y, 0, layout.lineCount - 1))
                y += layout.height / LAYOUT_SCALE + 3f
            }
            cursorY += totalHeightPt + bodyPt * 0.9f
        }

        private fun layoutHeading(
            block: MdBlock.HeadingBlock,
            nextBlock: MdBlock?,
            leftPt: Float,
            widthPt: Float
        ) {
            val scale = when (block.level) {
                1 -> 1.95f
                2 -> 1.52f
                3 -> 1.26f
                4 -> 1.10f
                5 -> 1.00f
                else -> 0.92f
            }
            val headingPt = bodyPt * scale
            val topSpacePt = if (cursorY > contentTopPt + 1f) {
                when (block.level) {
                    1 -> bodyPt * 1.25f
                    2 -> bodyPt * 1.10f
                    else -> bodyPt * 0.85f
                }
            } else {
                0f
            }
            val textColor = if (block.level == 6) COLOR_TEXT_MUTED else COLOR_TEXT_PRIMARY
            val spannable = buildSpannable(block.spans, basePt = headingPt, defaultColor = textColor, forceBold = true)
            val layout = buildStaticLayout(
                text = spannable,
                widthPt = widthPt,
                baseSizePt = headingPt,
                baseColor = textColor,
                alignment = block.alignment,
                lineSpacingMult = 1.22f,
                defaultBold = true
            )
            val headingHeightPt = layout.height / LAYOUT_SCALE
            val afterHeadingPt = if (block.level <= 2) 4f + bodyPt * 0.72f else bodyPt * 0.55f
            val minFollowPt = estimateMinimumFollowingHeightPt(nextBlock)

            if (topSpacePt + headingHeightPt + afterHeadingPt + minFollowPt > remainingHeightPt() && cursorY > contentTopPt + 1f) {
                newPage()
            } else {
                cursorY += topSpacePt
            }

            if (block.level <= 3 && block.plainText.isNotBlank()) {
                outlines.add(
                    PdfOutlineEntry(
                        level = block.level,
                        title = block.plainText.take(120),
                        pageIndex = pageIndex,
                        topPt = cursorY
                    )
                )
            }

            drawLayoutWithPagination(
                layout = layout,
                leftPt = leftPt,
                bottomSpacingPt = if (block.level <= 2) 4f else bodyPt * 0.55f
            )

            if (block.level <= 2) {
                val lineY = cursorY
                currentPage().commands.add(
                    PageCommand.DrawLine(
                        x1Pt = leftPt,
                        y1Pt = lineY,
                        x2Pt = leftPt + widthPt,
                        y2Pt = lineY,
                        color = COLOR_RULE,
                        strokeWidthPt = if (block.level == 1) 1.0f else 0.6f
                    )
                )
                cursorY += bodyPt * 0.72f
            }
        }

        private fun estimateMinimumFollowingHeightPt(nextBlock: MdBlock?): Float {
            val raw = when (nextBlock) {
                null -> 0f
                is MdBlock.TableBlock -> bodyPt * 7.0f
                is MdBlock.VideoBlock -> 150f
                is MdBlock.ImageBlock -> 100f
                is MdBlock.CodeBlock -> bodyPt * 6.0f
                is MdBlock.DetailsBlock,
                is MdBlock.BlockQuoteBlock,
                is MdBlock.MathBlock -> bodyPt * 5.0f
                is MdBlock.HeadingBlock -> bodyPt * 4.5f
                else -> bodyPt * 3.0f
            }
            return raw.coerceAtMost((contentBottomPt - contentTopPt) * 0.45f)
        }

        private fun layoutParagraph(
            block: MdBlock.ParagraphBlock,
            leftPt: Float,
            widthPt: Float,
            basePt: Float,
            spacingAfterPt: Float,
            nextBlock: MdBlock? = null
        ) {
            val spannable = buildSpannable(block.spans, basePt = basePt, defaultColor = COLOR_TEXT_PRIMARY)
            if (spannable.isEmpty()) return
            val layout = buildStaticLayout(
                text = spannable,
                widthPt = widthPt,
                baseSizePt = basePt,
                baseColor = COLOR_TEXT_PRIMARY,
                alignment = block.alignment,
                lineSpacingMult = 1.42f
            )
            if (
                (nextBlock is MdBlock.VideoBlock || nextBlock is MdBlock.ImageBlock || nextBlock is MdBlock.TableBlock) &&
                layout.lineCount <= 2
            ) {
                val trimmed = spannable.toString().trimEnd()
                if (trimmed.endsWith(':') || trimmed.endsWith('：')) {
                    ensureSpaceOrNewPage(
                        layout.height / LAYOUT_SCALE + spacingAfterPt + estimateMinimumFollowingHeightPt(nextBlock)
                    )
                }
            }
            drawLayoutWithPagination(layout, leftPt, spacingAfterPt)
        }

        private fun layoutCodeBlock(block: MdBlock.CodeBlock, leftPt: Float, widthPt: Float) {
            val codePt = (bodyPt * 0.86f).coerceIn(7.5f, 10.5f)
            val padX = 10f
            val padY = 9f
            val innerWidthPt = (widthPt - padX * 2f).coerceAtLeast(40f)

            val highlighted = MarkdownCodeHighlighter.highlightLines(block.code, block.language)
            val ssb = SpannableStringBuilder()
            highlighted.forEachIndexed { lineIdx, tokens ->
                if (lineIdx > 0) ssb.append('\n')
                if (tokens.isEmpty() || (tokens.size == 1 && tokens[0].text.isEmpty())) {
                    ssb.append(" ")
                } else {
                    for (token in tokens) {
                        val start = ssb.length
                        ssb.append(token.text)
                        ssb.setSpan(
                            ForegroundColorSpan(token.color),
                            start,
                            ssb.length,
                            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                        )
                    }
                }
            }
            if (ssb.isEmpty()) ssb.append(" ")

            val codeLayout = buildStaticLayout(
                text = ssb,
                widthPt = innerWidthPt,
                baseSizePt = codePt,
                baseColor = COLOR_CODE_TEXT,
                lineSpacingMult = 1.32f,
                monospace = true
            )

            val headerHeightPt = if (!block.language.isNullOrBlank()) 17f else 0f
            val firstLineHeightPt = codeLayout.getLineBottom(0) / LAYOUT_SCALE
            ensureSpaceOrNewPage(padY * 2f + headerHeightPt + firstLineHeightPt)

            var lineIdx = 0
            var isFirstSlice = true
            while (lineIdx < codeLayout.lineCount) {
                val extraHeader = if (isFirstSlice) headerHeightPt else 0f
                val minSliceNeeded = padY * 2f + extraHeader + (codeLayout.getLineBottom(lineIdx) - codeLayout.getLineTop(lineIdx)) / LAYOUT_SCALE
                if (minSliceNeeded > remainingHeightPt() && cursorY > contentTopPt + 1f) {
                    newPage()
                    continue
                }

                val availableForLinesPt = (remainingHeightPt() - padY * 2f - extraHeader).coerceAtLeast(10f)
                val startTop2x = codeLayout.getLineTop(lineIdx)
                var endLine = lineIdx
                while (endLine + 1 < codeLayout.lineCount) {
                    val nextH = (codeLayout.getLineBottom(endLine + 1) - startTop2x) / LAYOUT_SCALE
                    if (nextH <= availableForLinesPt) {
                        endLine++
                    } else {
                        break
                    }
                }

                val sliceTextHeightPt = (codeLayout.getLineBottom(endLine) - startTop2x) / LAYOUT_SCALE
                val boxHeightPt = padY * 2f + extraHeader + sliceTextHeightPt
                val boxRect = RectF(leftPt, cursorY, leftPt + widthPt, cursorY + boxHeightPt)

                currentPage().commands.add(
                    PageCommand.DrawRect(
                        rectPt = boxRect,
                        fillColor = COLOR_CODE_BLOCK_BG,
                        strokeColor = COLOR_BORDER,
                        strokeWidthPt = 0.6f,
                        cornerRadiusPt = 5f
                    )
                )

                var contentStartY = cursorY + padY
                if (isFirstSlice && !block.language.isNullOrBlank()) {
                    val langLabel = SpannableStringBuilder(block.language.uppercase(Locale.US))
                    val langLayout = buildStaticLayout(
                        text = langLabel,
                        widthPt = innerWidthPt,
                        baseSizePt = 7.5f,
                        baseColor = COLOR_TEXT_MUTED,
                        lineSpacingMult = 1.0f,
                        defaultBold = true
                    )
                    currentPage().commands.add(
                        PageCommand.DrawTextSlice(langLayout, leftPt + padX, cursorY + 6f, 0, langLayout.lineCount - 1)
                    )
                    val dividerY = cursorY + headerHeightPt + 2f
                    currentPage().commands.add(
                        PageCommand.DrawLine(
                            x1Pt = leftPt,
                            y1Pt = dividerY,
                            x2Pt = leftPt + widthPt,
                            y2Pt = dividerY,
                            color = COLOR_BORDER,
                            strokeWidthPt = 0.5f
                        )
                    )
                    contentStartY = dividerY + 6f
                }

                currentPage().commands.add(
                    PageCommand.DrawTextSlice(
                        layout = codeLayout,
                        xPt = leftPt + padX,
                        yPt = contentStartY,
                        startLine = lineIdx,
                        endLine = endLine
                    )
                )

                cursorY += boxHeightPt
                lineIdx = endLine + 1
                isFirstSlice = false
                if (lineIdx < codeLayout.lineCount) {
                    newPage()
                }
            }
            cursorY += bodyPt * 0.78f
        }

        private fun layoutBlockQuote(
            block: MdBlock.BlockQuoteBlock,
            leftPt: Float,
            widthPt: Float,
            isCancelled: () -> Boolean
        ) {
            val callout = block.calloutType
            val barColor: Int
            val bgColor: Int?
            val titleLabel: String?
            when (callout) {
                MdCalloutType.Note -> {
                    barColor = 0xFF0969DA.toInt(); bgColor = 0xFFF0F6FC.toInt(); titleLabel = "NOTE"
                }
                MdCalloutType.Tip -> {
                    barColor = 0xFF1A7F37.toInt(); bgColor = 0xFFF0F9F4.toInt(); titleLabel = "TIP"
                }
                MdCalloutType.Important -> {
                    barColor = 0xFF8250DF.toInt(); bgColor = 0xFFF6F0FD.toInt(); titleLabel = "IMPORTANT"
                }
                MdCalloutType.Warning -> {
                    barColor = 0xFF9A6700.toInt(); bgColor = 0xFFFFF8E5.toInt(); titleLabel = "WARNING"
                }
                MdCalloutType.Caution -> {
                    barColor = 0xFFCF222E.toInt(); bgColor = 0xFFFFF0F0.toInt(); titleLabel = "CAUTION"
                }
                null -> {
                    barColor = COLOR_QUOTE_BAR; bgColor = null; titleLabel = null
                }
            }

            ensureSpaceOrNewPage(bodyPt * 3f)
            val padLeft = if (callout != null) 14f else 12f
            val padRight = if (callout != null) 12f else 4f
            val padV = if (callout != null) 9f else 2f

            val startPageIdx = pageIndex
            val startY = cursorY + padV
            cursorY = startY

            // Reserve space index for background rect on starting page (if single-page callout we fill nicely; across pages we draw bar per page)
            val bgPlaceholderCmdIdx = if (bgColor != null) {
                currentPage().commands.add(PageCommand.DrawRect(RectF(), bgColor, cornerRadiusPt = 4f))
                currentPage().commands.lastIndex
            } else {
                -1
            }

            if (titleLabel != null) {
                val ssb = SpannableStringBuilder(titleLabel)
                val layout = buildStaticLayout(
                    text = ssb,
                    widthPt = widthPt - padLeft - padRight,
                    baseSizePt = bodyPt * 0.88f,
                    baseColor = barColor,
                    defaultBold = true
                )
                drawLayoutWithPagination(layout, leftPt + padLeft, bodyPt * 0.35f)
            }

            val innerWidth = (widthPt - padLeft - padRight).coerceAtLeast(60f)
            for (i in block.children.indices) {
                if (isCancelled()) break
                layoutBlock(
                    block = block.children[i],
                    nextBlock = block.children.getOrNull(i + 1),
                    leftPt = leftPt + padLeft,
                    widthPt = innerWidth,
                    isCancelled = isCancelled
                )
            }

            val endPageIdx = pageIndex
            val endY = (cursorY - bodyPt * 0.3f).coerceAtLeast(startY + bodyPt)

            // Draw left accent bar on each spanned page
            for (pIdx in startPageIdx..endPageIdx) {
                val top = if (pIdx == startPageIdx) startY - padV else contentTopPt
                val bottom = if (pIdx == endPageIdx) endY + padV else contentBottomPt
                if (bottom > top) {
                    if (pIdx == startPageIdx && bgPlaceholderCmdIdx >= 0 && startPageIdx == endPageIdx) {
                        pages[pIdx].commands[bgPlaceholderCmdIdx] = PageCommand.DrawRect(
                            rectPt = RectF(leftPt, top, leftPt + widthPt, bottom),
                            fillColor = bgColor!!,
                            cornerRadiusPt = 4f
                        )
                    } else if (bgColor != null && pIdx > startPageIdx) {
                        pages[pIdx].commands.add(
                            0,
                            PageCommand.DrawRect(
                                rectPt = RectF(leftPt, top, leftPt + widthPt, bottom),
                                fillColor = bgColor,
                                cornerRadiusPt = 4f
                            )
                        )
                    } else if (pIdx == startPageIdx && bgPlaceholderCmdIdx >= 0) {
                        pages[pIdx].commands[bgPlaceholderCmdIdx] = PageCommand.DrawRect(
                            rectPt = RectF(leftPt, top, leftPt + widthPt, bottom),
                            fillColor = bgColor!!,
                            cornerRadiusPt = 4f
                        )
                    }
                    pages[pIdx].commands.add(
                        PageCommand.DrawRect(
                            rectPt = RectF(leftPt, top, leftPt + 3f, bottom),
                            fillColor = barColor,
                            cornerRadiusPt = 1.5f
                        )
                    )
                }
            }
            cursorY = endY + padV + bodyPt * 0.65f
        }

        private fun layoutDetailsBlock(
            block: MdBlock.DetailsBlock,
            leftPt: Float,
            widthPt: Float,
            isCancelled: () -> Boolean
        ) {
            ensureSpaceOrNewPage(bodyPt * 3.5f)
            val summarySpans = listOf(MdSpan.TextSpan("▸ ", MdInlineStyle(bold = true))) + block.summary
            val summarySsb = buildSpannable(summarySpans, basePt = bodyPt, defaultColor = COLOR_TEXT_PRIMARY, forceBold = true)
            val summaryLayout = buildStaticLayout(
                text = summarySsb,
                widthPt = widthPt - 20f,
                baseSizePt = bodyPt,
                baseColor = COLOR_TEXT_PRIMARY,
                defaultBold = true
            )
            val headerH = summaryLayout.height / LAYOUT_SCALE + 10f
            currentPage().commands.add(
                PageCommand.DrawRect(
                    rectPt = RectF(leftPt, cursorY, leftPt + widthPt, cursorY + headerH),
                    fillColor = COLOR_CODE_BLOCK_BG,
                    strokeColor = COLOR_BORDER,
                    strokeWidthPt = 0.5f,
                    cornerRadiusPt = 4f
                )
            )
            currentPage().commands.add(
                PageCommand.DrawTextSlice(summaryLayout, leftPt + 10f, cursorY + 5f, 0, summaryLayout.lineCount - 1)
            )
            cursorY += headerH + 6f

            val startPageIdx = pageIndex
            val startY = cursorY
            for (i in block.children.indices) {
                if (isCancelled()) break
                layoutBlock(
                    block = block.children[i],
                    nextBlock = block.children.getOrNull(i + 1),
                    leftPt = leftPt + 12f,
                    widthPt = (widthPt - 16f).coerceAtLeast(60f),
                    isCancelled = isCancelled
                )
            }
            val endPageIdx = pageIndex
            val endY = cursorY
            for (pIdx in startPageIdx..endPageIdx) {
                val top = if (pIdx == startPageIdx) startY else contentTopPt
                val bottom = if (pIdx == endPageIdx) endY else contentBottomPt
                if (bottom > top) {
                    pages[pIdx].commands.add(
                        PageCommand.DrawLine(leftPt + 4f, top, leftPt + 4f, bottom, COLOR_BORDER, 1.5f)
                    )
                }
            }
            cursorY += bodyPt * 0.4f
        }

        private fun layoutList(
            block: MdBlock.ListBlock,
            leftPt: Float,
            widthPt: Float,
            isCancelled: () -> Boolean,
            nextBlock: MdBlock? = null
        ) {
            val markerWidthPt = if (block.ordered) 22f else 16f
            val childLeftPt = leftPt + markerWidthPt
            val childWidthPt = (widthPt - markerWidthPt).coerceAtLeast(60f)

            block.items.forEachIndexed { index, item ->
                if (isCancelled()) return
                val followBlock = if (index == block.items.lastIndex) nextBlock else null
                val itemMediaTarget = when {
                    item.blocks.size >= 2 &&
                        item.blocks[0] is MdBlock.ParagraphBlock &&
                        (item.blocks[1] is MdBlock.VideoBlock || item.blocks[1] is MdBlock.ImageBlock || item.blocks[1] is MdBlock.TableBlock) ->
                        item.blocks[1]
                    item.blocks.size == 1 &&
                        item.blocks[0] is MdBlock.ParagraphBlock &&
                        (followBlock is MdBlock.VideoBlock || followBlock is MdBlock.ImageBlock || followBlock is MdBlock.TableBlock) ->
                        followBlock
                    else -> null
                }
                val neededItemPt = if (itemMediaTarget != null) {
                    val leadText = (item.blocks[0] as MdBlock.ParagraphBlock).spans
                        .filterIsInstance<MdSpan.TextSpan>()
                        .joinToString("") { it.text }
                        .trimEnd()
                    if (leadText.length <= 120 && (leadText.endsWith(':') || leadText.endsWith('：'))) {
                        bodyPt * 2.4f + estimateMinimumFollowingHeightPt(itemMediaTarget)
                    } else {
                        bodyPt * 1.6f
                    }
                } else {
                    bodyPt * 1.6f
                }
                ensureSpaceOrNewPage(neededItemPt)
                val markerY = cursorY

                if (item.checked != null) {
                    val boxSize = (bodyPt * 0.92f).coerceIn(8.5f, 12f)
                    currentPage().commands.add(
                        PageCommand.DrawCheckbox(
                            xPt = leftPt + 2f,
                            yPt = markerY + 2.5f,
                            sizePt = boxSize,
                            checked = item.checked
                        )
                    )
                } else {
                    val markerText = if (block.ordered) "${block.startNumber + index}." else "•"
                    val markerLayout = buildStaticLayout(
                        text = SpannableStringBuilder(markerText),
                        widthPt = markerWidthPt - 3f,
                        baseSizePt = bodyPt,
                        baseColor = COLOR_TEXT_MUTED,
                        alignment = if (block.ordered) MdAlignment.Right else MdAlignment.Center,
                        defaultBold = !block.ordered
                    )
                    currentPage().commands.add(
                        PageCommand.DrawTextSlice(markerLayout, leftPt, markerY, 0, markerLayout.lineCount - 1)
                    )
                }

                if (item.blocks.isEmpty()) {
                    cursorY += bodyPt * 1.4f
                } else {
                    item.blocks.forEachIndexed { bIdx, childBlock ->
                        val isLastInItem = bIdx == item.blocks.lastIndex
                        if (childBlock is MdBlock.ParagraphBlock) {
                            layoutParagraph(
                                block = childBlock,
                                leftPt = childLeftPt,
                                widthPt = childWidthPt,
                                basePt = bodyPt,
                                spacingAfterPt = if (isLastInItem) bodyPt * 0.35f else bodyPt * 0.45f
                            )
                        } else {
                            layoutBlock(
                                block = childBlock,
                                nextBlock = item.blocks.getOrNull(bIdx + 1),
                                leftPt = childLeftPt,
                                widthPt = childWidthPt,
                                isCancelled = isCancelled
                            )
                        }
                    }
                }
            }
            cursorY += bodyPt * 0.38f
        }

        private fun layoutTable(
            block: MdBlock.TableBlock,
            leftPt: Float,
            widthPt: Float,
            isCancelled: () -> Boolean
        ) {
            val allRows = block.headerRows + block.bodyRows
            if (allRows.isEmpty()) return
            val colCount = maxOf(
                block.columnAlignments.size,
                allRows.maxOfOrNull { row -> row.sumOf { it.colSpan } } ?: 1
            ).coerceAtLeast(1)

            val colWidths = computeTableColumnWidths(allRows, colCount, widthPt)
            val cellPadX = 6.5f
            val cellPadY = 5.5f
            val cellFontPt = (bodyPt * 0.90f).coerceAtLeast(8f)

            data class MeasuredCell(
                val colStart: Int,
                val colSpan: Int,
                val widthPt: Float,
                val layout: StaticLayout,
                val leftOffsetPt: Float
            )

            data class MeasuredRow(
                val isHeader: Boolean,
                val bodyIndex: Int,
                val heightPt: Float,
                val cells: List<MeasuredCell>
            )

            fun measureRow(row: List<MdTableCell>, isHeader: Boolean, bodyIndex: Int): MeasuredRow {
                val measuredCells = mutableListOf<MeasuredCell>()
                var colCursor = 0
                var xOffset = 0f
                var maxLayoutHeightPt = cellFontPt * 1.3f

                for (cell in row) {
                    val span = cell.colSpan.coerceIn(1, (colCount - colCursor).coerceAtLeast(1))
                    var spanWidth = 0f
                    for (k in 0 until span) {
                        spanWidth += colWidths.getOrElse(colCursor + k) { colWidths.last() }
                    }
                    val textWidth = (spanWidth - cellPadX * 2f).coerceAtLeast(16f)
                    val ssb = buildSpannable(
                        spans = cell.spans,
                        basePt = cellFontPt,
                        defaultColor = COLOR_TEXT_PRIMARY,
                        forceBold = isHeader || cell.isHeader
                    )
                    val layout = buildStaticLayout(
                        text = ssb,
                        widthPt = textWidth,
                        baseSizePt = cellFontPt,
                        baseColor = COLOR_TEXT_PRIMARY,
                        alignment = cell.alignment,
                        lineSpacingMult = 1.25f,
                        defaultBold = isHeader || cell.isHeader
                    )
                    val hPt = layout.height / LAYOUT_SCALE
                    if (hPt > maxLayoutHeightPt) maxLayoutHeightPt = hPt
                    measuredCells.add(
                        MeasuredCell(
                            colStart = colCursor,
                            colSpan = span,
                            widthPt = spanWidth,
                            layout = layout,
                            leftOffsetPt = xOffset
                        )
                    )
                    colCursor += span
                    xOffset += spanWidth
                }
                return MeasuredRow(
                    isHeader = isHeader,
                    bodyIndex = bodyIndex,
                    heightPt = maxLayoutHeightPt + cellPadY * 2f,
                    cells = measuredCells
                )
            }

            val measuredHeaders = block.headerRows.mapIndexed { idx, row -> measureRow(row, true, idx) }
            val measuredBodies = block.bodyRows.mapIndexed { idx, row -> measureRow(row, false, idx) }
            val headerHeightSum = measuredHeaders.sumOf { it.heightPt.toDouble() }.toFloat()

            fun drawMeasuredRow(row: MeasuredRow) {
                val bg = when {
                    row.isHeader -> COLOR_TABLE_HEADER_BG
                    row.bodyIndex % 2 == 1 -> COLOR_TABLE_STRIPE_BG
                    else -> null
                }
                for (cell in row.cells) {
                    val cellLeft = leftPt + cell.leftOffsetPt
                    val rect = RectF(cellLeft, cursorY, cellLeft + cell.widthPt, cursorY + row.heightPt)
                    currentPage().commands.add(
                        PageCommand.DrawRect(
                            rectPt = rect,
                            fillColor = bg ?: 0xFFFFFFFF.toInt(),
                            strokeColor = COLOR_BORDER,
                            strokeWidthPt = 0.5f
                        )
                    )
                    val contentH = cell.layout.height / LAYOUT_SCALE
                    val textY = cursorY + ((row.heightPt - contentH) / 2f).coerceAtLeast(cellPadY)
                    currentPage().commands.add(
                        PageCommand.DrawTextSlice(
                            layout = cell.layout,
                            xPt = cellLeft + cellPadX,
                            yPt = textY,
                            startLine = 0,
                            endLine = cell.layout.lineCount - 1
                        )
                    )
                    collectLinksForLayout(cell.layout, cellLeft + cellPadX, textY, 0, cell.layout.lineCount - 1)
                }
                cursorY += row.heightPt
            }

            val firstBodyHeight = measuredBodies.firstOrNull()?.heightPt ?: 0f
            ensureSpaceOrNewPage(headerHeightSum + firstBodyHeight)

            measuredHeaders.forEach { drawMeasuredRow(it) }
            for (bodyRow in measuredBodies) {
                if (isCancelled()) break
                if (bodyRow.heightPt > remainingHeightPt() && cursorY > contentTopPt + 1f) {
                    newPage()
                    measuredHeaders.forEach { drawMeasuredRow(it) }
                }
                drawMeasuredRow(bodyRow)
            }
            cursorY += bodyPt * 0.78f
        }

        private fun computeTableColumnWidths(
            rows: List<List<MdTableCell>>,
            colCount: Int,
            totalWidthPt: Float
        ): List<Float> {
            val preferred = FloatArray(colCount) { 40f }
            val cellFontPt = (bodyPt * 0.90f).coerceAtLeast(8f)
            val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                textSize = cellFontPt * LAYOUT_SCALE
                typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            }
            for (row in rows) {
                var col = 0
                for (cell in row) {
                    if (cell.colSpan == 1 && col < colCount) {
                        val plain = buildSpannable(cell.spans, cellFontPt, COLOR_TEXT_PRIMARY)
                        val desired = (Layout.getDesiredWidth(plain, paint) / LAYOUT_SCALE + 16f)
                            .coerceIn(32f, totalWidthPt * 0.65f)
                        if (desired > preferred[col]) preferred[col] = desired
                    }
                    col += cell.colSpan
                }
            }
            val minWidth = minOf(38f, totalWidthPt / colCount)
            val sumPref = preferred.sum().coerceAtLeast(1f)
            return if (sumPref <= totalWidthPt) {
                val extra = totalWidthPt - sumPref
                preferred.map { it + extra * (it / sumPref) }
            } else {
                val baseTotal = minWidth * colCount
                val allocatable = (totalWidthPt - baseTotal).coerceAtLeast(0f)
                val excessSum = preferred.sumOf { (it - minWidth).coerceAtLeast(0f).toDouble() }.toFloat().coerceAtLeast(1f)
                preferred.map { p ->
                    minWidth + allocatable * ((p - minWidth).coerceAtLeast(0f) / excessSum)
                }
            }
        }

        private fun layoutThematicBreak(leftPt: Float, widthPt: Float) {
            ensureSpaceOrNewPage(14f)
            val y = cursorY + 6f
            currentPage().commands.add(
                PageCommand.DrawLine(leftPt, y, leftPt + widthPt, y, COLOR_RULE, 1.0f)
            )
            cursorY += 14f
        }

        private fun layoutMathBlock(block: MdBlock.MathBlock, leftPt: Float, widthPt: Float) {
            val ssb = SpannableStringBuilder(block.formatted)
            val layout = buildStaticLayout(
                text = ssb,
                widthPt = widthPt - 24f,
                baseSizePt = bodyPt * 1.05f,
                baseColor = COLOR_TEXT_PRIMARY,
                alignment = MdAlignment.Center,
                lineSpacingMult = 1.3f,
                italic = true
            )
            val boxH = layout.height / LAYOUT_SCALE + 16f
            ensureSpaceOrNewPage(boxH)
            currentPage().commands.add(
                PageCommand.DrawRect(
                    rectPt = RectF(leftPt, cursorY, leftPt + widthPt, cursorY + boxH),
                    fillColor = COLOR_CODE_BLOCK_BG,
                    strokeColor = COLOR_BORDER,
                    strokeWidthPt = 0.4f,
                    cornerRadiusPt = 4f
                )
            )
            currentPage().commands.add(
                PageCommand.DrawTextSlice(layout, leftPt + 12f, cursorY + 8f, 0, layout.lineCount - 1)
            )
            cursorY += boxH + bodyPt * 0.72f
        }

        private fun layoutImageBlock(block: MdBlock.ImageBlock, leftPt: Float, widthPt: Float) {
            val resolved = media.images[block.source]
            val imageFile = resolved?.imageFile
            if (imageFile == null || resolved.widthPx <= 0 || resolved.heightPx <= 0) {
                val badge = resolved?.badgeLabel
                val label = badge ?: block.alt.ifBlank { block.title?.ifBlank { null } ?: block.source.substringAfterLast('/') }
                val sub = if (badge != null) null else block.source.takeIf { it != label }
                val cardH = if (sub != null) 34f else 24f
                ensureSpaceOrNewPage(cardH)
                val rect = RectF(leftPt, cursorY, leftPt + widthPt, cursorY + cardH)
                currentPage().commands.add(
                    PageCommand.DrawMediaBadgeOrFallback(
                        rectPt = rect,
                        label = label,
                        sublabel = sub,
                        isLink = !block.linkUrl.isNullOrBlank()
                    )
                )
                block.linkUrl?.takeIf { it.isNotBlank() }?.let { url ->
                    links.add(PdfLinkAnnotation(pageIndex, rect.left, rect.top, rect.right, rect.bottom, url))
                }
                cursorY += cardH + bodyPt * 0.7f
                return
            }

            val aspect = resolved.widthPx.toFloat() / resolved.heightPx.toFloat()
            val maxCardH = (contentBottomPt - contentTopPt) * 0.76f
            val naturalPtW = resolved.widthPx * 0.75f
            var drawW = when {
                block.widthHint != null && block.widthHint.isPercent -> widthPt * (block.widthHint.value / 100f)
                block.widthHint != null -> minOf(block.widthHint.value, widthPt)
                else -> minOf(naturalPtW, widthPt)
            }.coerceIn(48f, widthPt)
            var drawH = drawW / aspect
            if (block.heightHint != null && !block.heightHint.isPercent) {
                val targetH = block.heightHint.value.coerceIn(24f, maxCardH)
                drawH = targetH
                if (block.widthHint == null) {
                    drawW = (drawH * aspect).coerceAtMost(widthPt)
                    drawH = drawW / aspect
                }
            }
            if (drawH > maxCardH) {
                drawH = maxCardH
                drawW = (drawH * aspect).coerceAtMost(widthPt)
            }

            val caption = block.title?.takeIf { it.isNotBlank() }
                ?: block.alt.takeIf { it.isNotBlank() && !it.equals(block.source.substringAfterLast('/'), ignoreCase = true) }
            val captionLayout = caption?.let {
                buildStaticLayout(
                    text = SpannableStringBuilder(it),
                    widthPt = widthPt,
                    baseSizePt = bodyPt * 0.85f,
                    baseColor = COLOR_TEXT_MUTED,
                    alignment = block.alignment
                )
            }
            val captionH = captionLayout?.let { it.height / LAYOUT_SCALE + 4f } ?: 0f

            ensureSpaceOrNewPage(drawH + captionH)
            val drawX = when (block.alignment) {
                MdAlignment.Left -> leftPt
                MdAlignment.Center -> leftPt + (widthPt - drawW) / 2f
                MdAlignment.Right -> leftPt + widthPt - drawW
            }
            val rect = RectF(drawX, cursorY, drawX + drawW, cursorY + drawH)
            currentPage().commands.add(PageCommand.DrawImageCard(imageFile, rect))
            block.linkUrl?.takeIf { it.isNotBlank() }?.let { url ->
                links.add(PdfLinkAnnotation(pageIndex, rect.left, rect.top, rect.right, rect.bottom, url))
            }
            cursorY += drawH
            if (captionLayout != null) {
                cursorY += 4f
                currentPage().commands.add(
                    PageCommand.DrawTextSlice(captionLayout, leftPt, cursorY, 0, captionLayout.lineCount - 1)
                )
                cursorY += captionLayout.height / LAYOUT_SCALE
            }
            cursorY += bodyPt * 0.78f
        }

        private fun layoutVideoBlock(block: MdBlock.VideoBlock, leftPt: Float, widthPt: Float) {
            val resolved = media.videos[block.source]
            val frameFile = resolved?.imageFile
            val durationLabel = resolved?.durationMs?.let { formatDuration(it) }
            val aspect = if (resolved != null && resolved.widthPx > 0 && resolved.heightPx > 0) {
                resolved.widthPx.toFloat() / resolved.heightPx.toFloat()
            } else {
                16f / 9f
            }
            val maxCardH = (contentBottomPt - contentTopPt) * 0.65f
            var drawW = when {
                block.widthHint != null && block.widthHint.isPercent -> widthPt * (block.widthHint.value / 100f)
                block.widthHint != null -> minOf(block.widthHint.value, widthPt)
                else -> minOf(widthPt, 420f)
            }.coerceIn(120f, widthPt)
            var drawH = drawW / aspect
            if (drawH > maxCardH) {
                drawH = maxCardH
                drawW = (drawH * aspect).coerceAtMost(widthPt)
            }
            if (frameFile == null) {
                drawH = drawH.coerceIn(72f, 120f)
            }

            ensureSpaceOrNewPage(drawH)
            val drawX = when (block.alignment) {
                MdAlignment.Left -> leftPt
                MdAlignment.Center -> leftPt + (widthPt - drawW) / 2f
                MdAlignment.Right -> leftPt + widthPt - drawW
            }
            val rect = RectF(drawX, cursorY, drawX + drawW, cursorY + drawH)
            val title = block.title?.takeIf { it.isNotBlank() } ?: block.source.substringAfterLast('/').substringBefore('?')
            val targetUrl = block.linkUrl ?: block.source.takeIf { it.startsWith("http://", true) || it.startsWith("https://", true) }
            currentPage().commands.add(
                PageCommand.DrawVideoCard(
                    frameFile = frameFile,
                    rectPt = rect,
                    title = title,
                    durationLabel = durationLabel,
                    urlHint = targetUrl
                )
            )
            if (!targetUrl.isNullOrBlank()) {
                links.add(PdfLinkAnnotation(pageIndex, rect.left, rect.top, rect.right, rect.bottom, targetUrl))
            }
            cursorY += drawH + bodyPt * 0.82f
        }

        private fun layoutFootnotes(block: MdBlock.FootnotesSectionBlock, leftPt: Float, widthPt: Float) {
            if (block.entries.isEmpty()) return
            ensureSpaceOrNewPage(bodyPt * 3f)
            cursorY += bodyPt * 0.4f
            currentPage().commands.add(
                PageCommand.DrawLine(leftPt, cursorY, leftPt + widthPt * 0.35f, cursorY, COLOR_RULE, 0.8f)
            )
            cursorY += 8f
            val fnPt = (bodyPt * 0.86f).coerceAtLeast(8f)
            for (entry in block.entries) {
                val prefix = MdSpan.TextSpan("[${entry.index}] ", MdInlineStyle(bold = true, textColor = COLOR_TEXT_MUTED))
                layoutParagraph(
                    block = MdBlock.ParagraphBlock(listOf(prefix) + entry.spans),
                    leftPt = leftPt,
                    widthPt = widthPt,
                    basePt = fnPt,
                    spacingAfterPt = fnPt * 0.45f
                )
            }
        }

        private fun drawLayoutWithPagination(
            layout: StaticLayout,
            leftPt: Float,
            bottomSpacingPt: Float
        ) {
            var lineIdx = 0
            while (lineIdx < layout.lineCount) {
                val firstLineH = (layout.getLineBottom(lineIdx) - layout.getLineTop(lineIdx)) / LAYOUT_SCALE
                if (firstLineH > remainingHeightPt() && cursorY > contentTopPt + 1f) {
                    newPage()
                    continue
                }
                val availPt = remainingHeightPt().coerceAtLeast(firstLineH)
                val sliceTop2x = layout.getLineTop(lineIdx)
                var endLine = lineIdx
                while (endLine + 1 < layout.lineCount) {
                    val nextH = (layout.getLineBottom(endLine + 1) - sliceTop2x) / LAYOUT_SCALE
                    if (nextH <= availPt) {
                        endLine++
                    } else {
                        break
                    }
                }
                val sliceHeightPt = (layout.getLineBottom(endLine) - sliceTop2x) / LAYOUT_SCALE
                currentPage().commands.add(
                    PageCommand.DrawTextSlice(
                        layout = layout,
                        xPt = leftPt,
                        yPt = cursorY,
                        startLine = lineIdx,
                        endLine = endLine
                    )
                )
                collectLinksForLayout(layout, leftPt, cursorY, lineIdx, endLine)
                cursorY += sliceHeightPt
                lineIdx = endLine + 1
                if (lineIdx < layout.lineCount) {
                    newPage()
                }
            }
            cursorY += bottomSpacingPt
        }

        private fun collectLinksForLayout(
            layout: StaticLayout,
            leftPt: Float,
            topPt: Float,
            startLine: Int,
            endLine: Int
        ) {
            val spanned = layout.text as? Spanned ?: return
            val linkSpans = spanned.getSpans(0, spanned.length, LinkTagSpan::class.java)
            if (linkSpans.isEmpty()) return

            val sliceTop2x = layout.getLineTop(startLine).toFloat()
            for (span in linkSpans) {
                val spanStart = spanned.getSpanStart(span)
                val spanEnd = spanned.getSpanEnd(span)
                if (spanStart < 0 || spanEnd <= spanStart) continue

                for (line in startLine..endLine) {
                    val lineStart = layout.getLineStart(line)
                    val lineVisibleEnd = layout.getLineVisibleEnd(line)
                    val segStart = maxOf(spanStart, lineStart)
                    val segEnd = minOf(spanEnd, lineVisibleEnd)
                    if (segStart >= segEnd) continue

                    val h1 = layout.getPrimaryHorizontal(segStart)
                    val h2 = if (segEnd >= lineVisibleEnd) {
                        maxOf(layout.getPrimaryHorizontal(segEnd), layout.getLineRight(line))
                    } else {
                        layout.getPrimaryHorizontal(segEnd)
                    }
                    val boxLeftPt = leftPt + minOf(h1, h2) / LAYOUT_SCALE
                    val boxRightPt = leftPt + maxOf(h1, h2) / LAYOUT_SCALE
                    val boxTopPt = topPt + (layout.getLineTop(line) - sliceTop2x) / LAYOUT_SCALE
                    val boxBottomPt = topPt + (layout.getLineBottom(line) - sliceTop2x) / LAYOUT_SCALE
                    if (boxRightPt > boxLeftPt + 1f && boxBottomPt > boxTopPt + 1f) {
                        links.add(
                            PdfLinkAnnotation(
                                pageIndex = pageIndex,
                                leftPt = boxLeftPt,
                                topPt = boxTopPt,
                                rightPt = boxRightPt,
                                bottomPt = boxBottomPt,
                                url = span.url
                            )
                        )
                    }
                }
            }
        }

        private fun buildSpannable(
            spans: List<MdSpan>,
            basePt: Float,
            defaultColor: Int,
            forceBold: Boolean = false
        ): SpannableStringBuilder {
            val ssb = SpannableStringBuilder()
            for (span in spans) {
                when (span) {
                    MdSpan.LineBreakSpan -> ssb.append('\n')
                    is MdSpan.InlineImageSpan -> {
                        val resolved = media.images[span.source]
                        val defaultImgH = basePt * 1.35f
                        val widthPt: Float
                        val heightPt: Float
                        val fallbackText: String?
                        if (resolved?.imageFile != null && resolved.widthPx > 0 && resolved.heightPx > 0) {
                            val aspect = resolved.widthPx.toFloat() / resolved.heightPx.toFloat()
                            val requestedW = span.widthHint?.takeIf { !it.isPercent }?.value
                            val requestedH = span.heightHint?.takeIf { !it.isPercent }?.value
                            when {
                                requestedH != null -> {
                                    heightPt = requestedH.coerceIn(10f, 120f)
                                    widthPt = (requestedW ?: (heightPt * aspect)).coerceIn(10f, contentWidthPt * 0.9f)
                                }
                                requestedW != null -> {
                                    widthPt = requestedW.coerceIn(10f, contentWidthPt * 0.9f)
                                    heightPt = (widthPt / aspect).coerceIn(10f, 120f)
                                }
                                else -> {
                                    heightPt = minOf(resolved.heightPx * 0.75f, defaultImgH.coerceAtLeast(15f))
                                    widthPt = (heightPt * aspect).coerceIn(12f, contentWidthPt * 0.85f)
                                }
                            }
                            fallbackText = span.alt
                        } else {
                            val label = resolved?.badgeLabel ?: span.alt.ifBlank { span.source.substringAfterLast('/') }
                            fallbackText = label.take(36)
                            widthPt = (fallbackText.length * 5.6f + 16f).coerceIn(36f, contentWidthPt * 0.6f)
                            heightPt = (basePt * 1.35f).coerceAtLeast(14f)
                        }
                        val start = ssb.length
                        ssb.append("\uFFFC")
                        val end = ssb.length
                        ssb.setSpan(
                            InlineBitmapSpan(
                                file = resolved?.imageFile,
                                widthPt = widthPt,
                                heightPt = heightPt,
                                fallbackLabel = fallbackText,
                                bitmapProvider = { f -> ActivePageBitmaps.get()(f) }
                            ),
                            start,
                            end,
                            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                        )
                        span.linkUrl?.takeIf { it.isNotBlank() }?.let { url ->
                            ssb.setSpan(LinkTagSpan(url), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                        }
                    }
                    is MdSpan.TextSpan -> {
                        if (span.text.isEmpty()) continue
                        val start = ssb.length
                        ssb.append(span.text)
                        val end = ssb.length
                        applyInlineStyleSpans(ssb, start, end, span.style, basePt, defaultColor, forceBold)
                    }
                }
            }
            return ssb
        }

        private fun applyInlineStyleSpans(
            ssb: SpannableStringBuilder,
            start: Int,
            end: Int,
            style: MdInlineStyle,
            blockBasePt: Float,
            defaultColor: Int,
            forceBold: Boolean
        ) {
            val effectiveText = ssb.subSequence(start, end).toString()
            val effectiveLink = style.linkUrl?.takeIf { it.isNotBlank() }
            val resolvedColor = style.textColor ?: if (effectiveLink != null) COLOR_LINK else defaultColor
            val unscaledPt = ((style.fontSizePt ?: blockBasePt) * style.fontSizeScale).coerceIn(5.5f, 72f)
            val isExplicitSize = style.fontSizePt != null || kotlin.math.abs(style.fontSizeScale - 1.0f) > 0.01f

            if (style.kbd && effectiveText.length <= 24 && !effectiveText.contains('\n')) {
                if (isExplicitSize) {
                    ssb.setSpan(
                        AbsoluteSizeSpan((unscaledPt * LAYOUT_SCALE).roundToInt(), false),
                        start,
                        end,
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                }
                ssb.setSpan(
                    InlinePillSpan(
                        bgColor = COLOR_KBD_BG,
                        borderColor = COLOR_BORDER,
                        textColor = resolvedColor,
                        scaleFactor = 0.85f,
                        monospace = true
                    ),
                    start,
                    end,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
                effectiveLink?.let { ssb.setSpan(LinkTagSpan(it), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE) }
                return
            }

            if (style.code && !style.mark && style.textColor == null && effectiveText.length <= 28 && !effectiveText.contains('\n')) {
                if (isExplicitSize) {
                    ssb.setSpan(
                        AbsoluteSizeSpan((unscaledPt * LAYOUT_SCALE).roundToInt(), false),
                        start,
                        end,
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                }
                ssb.setSpan(
                    InlinePillSpan(
                        bgColor = COLOR_CODE_PILL_BG,
                        borderColor = null,
                        textColor = if (effectiveLink != null) COLOR_LINK else COLOR_CODE_TEXT,
                        scaleFactor = 0.88f,
                        monospace = true
                    ),
                    start,
                    end,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
                effectiveLink?.let { ssb.setSpan(LinkTagSpan(it), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE) }
                return
            }

            // Font size
            val codeScale = if (style.code || style.kbd) 0.88f else 1.0f
            val resolvedPt = (unscaledPt * codeScale).coerceIn(5.5f, 72f)
            if (isExplicitSize || style.code || style.kbd) {
                ssb.setSpan(
                    AbsoluteSizeSpan((resolvedPt * LAYOUT_SCALE).roundToInt(), false),
                    start,
                    end,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }

            // Weight + Italic + Family
            val weight = style.fontWeight ?: if (style.bold || forceBold) 700 else 400
            if (weight != 400 || style.italic || style.fontFamily != null) {
                ssb.setSpan(
                    WeightTypefaceSpan(style.fontFamily, weight, style.italic),
                    start,
                    end,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }

            // Colors & Backgrounds
            ssb.setSpan(ForegroundColorSpan(resolvedColor), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            val bg = style.backgroundColor ?: when {
                style.mark -> COLOR_MARK_BG
                style.code -> COLOR_CODE_PILL_BG
                else -> null
            }
            if (bg != null) {
                ssb.setSpan(BackgroundColorSpan(bg), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }

            if (style.strikethrough) {
                ssb.setSpan(StrikethroughSpan(), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            if (style.underline || effectiveLink != null) {
                ssb.setSpan(UnderlineSpan(), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            if (style.superscript) {
                ssb.setSpan(SuperscriptSpan(), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            } else if (style.subscript) {
                ssb.setSpan(SubscriptSpan(), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            effectiveLink?.let {
                ssb.setSpan(LinkTagSpan(it), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
        }

        private fun buildStaticLayout(
            text: CharSequence,
            widthPt: Float,
            baseSizePt: Float,
            baseColor: Int,
            alignment: MdAlignment = MdAlignment.Left,
            lineSpacingMult: Float = 1.35f,
            defaultBold: Boolean = false,
            italic: Boolean = false,
            monospace: Boolean = false
        ): StaticLayout {
            val baseFamily = if (monospace) Typeface.MONOSPACE else Typeface.SANS_SERIF
            val styleBits = when {
                defaultBold && italic -> Typeface.BOLD_ITALIC
                defaultBold -> Typeface.BOLD
                italic -> Typeface.ITALIC
                else -> Typeface.NORMAL
            }
            val paint = TextPaint(
                Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG or Paint.LINEAR_TEXT_FLAG
            ).apply {
                color = baseColor
                textSize = baseSizePt * LAYOUT_SCALE
                typeface = Typeface.create(baseFamily, styleBits)
            }
            val layoutAlign = when (alignment) {
                MdAlignment.Center -> Layout.Alignment.ALIGN_CENTER
                MdAlignment.Right -> Layout.Alignment.ALIGN_OPPOSITE
                MdAlignment.Left -> Layout.Alignment.ALIGN_NORMAL
            }
            val width2x = (widthPt * LAYOUT_SCALE).roundToInt().coerceAtLeast(16)
            return StaticLayout.Builder.obtain(text, 0, text.length, paint, width2x)
                .setAlignment(layoutAlign)
                .setLineSpacing(0f, lineSpacingMult)
                .setIncludePad(false)
                .setBreakStrategy(Layout.BREAK_STRATEGY_HIGH_QUALITY)
                .setHyphenationFrequency(Layout.HYPHENATION_FREQUENCY_NORMAL)
                .build()
        }

        private fun formatDuration(durationMs: Long): String {
            val totalSeconds = (durationMs / 1000L).coerceAtLeast(0L)
            val hours = totalSeconds / 3600L
            val minutes = (totalSeconds % 3600L) / 60L
            val seconds = totalSeconds % 60L
            return if (hours > 0L) {
                String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
            } else {
                String.format(Locale.US, "%02d:%02d", minutes, seconds)
            }
        }
    }

    private fun writeVectorPdf(
        plan: LayoutPlan,
        pageWidthPt: Int,
        pageHeightPt: Int,
        bodyPt: Float,
        rawPdfFile: File,
        isCancelled: () -> Boolean,
        onProgress: (Float) -> Unit
    ) {
        val pdfDocument = PdfDocument()
        val totalPages = plan.pages.size.coerceAtLeast(1)

        // Hook up bitmap provider only per-page during draw
        val bitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
        val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
        val footerPaint = TextPaint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
            color = COLOR_TEXT_MUTED
            textSize = 8.5f * LAYOUT_SCALE
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
        }

        try {
            plan.pages.forEachIndexed { idx, pagePlan ->
                if (isCancelled()) return
                val pageBitmapCache = mutableMapOf<File, Bitmap?>()
                // Temporarily expose pageBitmapCache to InlineBitmapSpan via ThreadLocal
                ActivePageBitmaps.set { file ->
                    pageBitmapCache.getOrPut(file) { decodeBitmapForDraw(file) }
                }

                val pageInfo = PdfDocument.PageInfo.Builder(pageWidthPt, pageHeightPt, idx + 1).create()
                val page = pdfDocument.startPage(pageInfo)
                try {
                    val canvas = page.canvas
                    canvas.drawColor(0xFFFFFFFF.toInt())

                    for (cmd in pagePlan.commands) {
                        if (isCancelled()) break
                        when (cmd) {
                            is PageCommand.DrawRect -> {
                                fillPaint.color = cmd.fillColor
                                if (cmd.cornerRadiusPt > 0f) {
                                    canvas.drawRoundRect(cmd.rectPt, cmd.cornerRadiusPt, cmd.cornerRadiusPt, fillPaint)
                                } else {
                                    canvas.drawRect(cmd.rectPt, fillPaint)
                                }
                                cmd.strokeColor?.let { sc ->
                                    strokePaint.color = sc
                                    strokePaint.strokeWidth = cmd.strokeWidthPt
                                    if (cmd.cornerRadiusPt > 0f) {
                                        canvas.drawRoundRect(cmd.rectPt, cmd.cornerRadiusPt, cmd.cornerRadiusPt, strokePaint)
                                    } else {
                                        canvas.drawRect(cmd.rectPt, strokePaint)
                                    }
                                }
                            }

                            is PageCommand.DrawLine -> {
                                strokePaint.color = cmd.color
                                strokePaint.strokeWidth = cmd.strokeWidthPt
                                canvas.drawLine(cmd.x1Pt, cmd.y1Pt, cmd.x2Pt, cmd.y2Pt, strokePaint)
                            }

                            is PageCommand.DrawCheckbox -> {
                                val r = RectF(cmd.xPt, cmd.yPt, cmd.xPt + cmd.sizePt, cmd.yPt + cmd.sizePt)
                                if (cmd.checked) {
                                    fillPaint.color = COLOR_CHECKBOX_CHECKED
                                    canvas.drawRoundRect(r, 2f, 2f, fillPaint)
                                    strokePaint.color = 0xFFFFFFFF.toInt()
                                    strokePaint.strokeWidth = 1.4f
                                    strokePaint.strokeCap = Paint.Cap.ROUND
                                    strokePaint.strokeJoin = Paint.Join.ROUND
                                    val p1x = r.left + r.width() * 0.24f
                                    val p1y = r.top + r.height() * 0.52f
                                    val p2x = r.left + r.width() * 0.44f
                                    val p2y = r.top + r.height() * 0.72f
                                    val p3x = r.left + r.width() * 0.78f
                                    val p3y = r.top + r.height() * 0.30f
                                    canvas.drawLine(p1x, p1y, p2x, p2y, strokePaint)
                                    canvas.drawLine(p2x, p2y, p3x, p3y, strokePaint)
                                } else {
                                    fillPaint.color = 0xFFFFFFFF.toInt()
                                    canvas.drawRoundRect(r, 2f, 2f, fillPaint)
                                    strokePaint.color = COLOR_BORDER
                                    strokePaint.strokeWidth = 1.0f
                                    canvas.drawRoundRect(r, 2f, 2f, strokePaint)
                                }
                            }

                            is PageCommand.DrawTextSlice -> {
                                val sliceTop2x = cmd.layout.getLineTop(cmd.startLine).toFloat()
                                val sliceBottom2x = cmd.layout.getLineBottom(cmd.endLine).toFloat()
                                canvas.save()
                                canvas.translate(cmd.xPt, cmd.yPt)
                                canvas.scale(1f / LAYOUT_SCALE, 1f / LAYOUT_SCALE)
                                canvas.clipRect(0f, 0f, cmd.layout.width.toFloat(), sliceBottom2x - sliceTop2x)
                                canvas.translate(0f, -sliceTop2x)
                                cmd.layout.draw(canvas)
                                canvas.restore()
                            }

                            is PageCommand.DrawImageCard -> {
                                val bmp = pageBitmapCache.getOrPut(cmd.file) { decodeBitmapForDraw(cmd.file) }
                                if (bmp != null) {
                                    canvas.save()
                                    if (cmd.cornerRadiusPt > 0f) {
                                        val path = android.graphics.Path().apply {
                                            addRoundRect(cmd.rectPt, cmd.cornerRadiusPt, cmd.cornerRadiusPt, android.graphics.Path.Direction.CW)
                                        }
                                        canvas.clipPath(path)
                                    }
                                    canvas.drawBitmap(bmp, null, cmd.rectPt, bitmapPaint)
                                    canvas.restore()
                                }
                            }

                            is PageCommand.DrawVideoCard -> {
                                drawVideoCardOnCanvas(
                                    canvas = canvas,
                                    cmd = cmd,
                                    bmp = cmd.frameFile?.let { f -> pageBitmapCache.getOrPut(f) { decodeBitmapForDraw(f) } },
                                    fillPaint = fillPaint,
                                    strokePaint = strokePaint,
                                    bitmapPaint = bitmapPaint
                                )
                            }

                            is PageCommand.DrawMediaBadgeOrFallback -> {
                                fillPaint.color = COLOR_BADGE_BG
                                strokePaint.color = COLOR_BORDER
                                strokePaint.strokeWidth = 0.6f
                                canvas.drawRoundRect(cmd.rectPt, 4f, 4f, fillPaint)
                                canvas.drawRoundRect(cmd.rectPt, 4f, 4f, strokePaint)

                                val labelLayout = StaticLayout.Builder.obtain(
                                    cmd.label,
                                    0,
                                    cmd.label.length,
                                    TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                                        color = if (cmd.isLink) COLOR_LINK else COLOR_TEXT_PRIMARY
                                        textSize = bodyPt * 0.85f * LAYOUT_SCALE
                                        typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
                                    },
                                    ((cmd.rectPt.width() - 16f) * LAYOUT_SCALE).roundToInt().coerceAtLeast(16)
                                ).setMaxLines(1).build()

                                canvas.save()
                                canvas.translate(cmd.rectPt.left + 8f, cmd.rectPt.top + 6f)
                                canvas.scale(1f / LAYOUT_SCALE, 1f / LAYOUT_SCALE)
                                labelLayout.draw(canvas)
                                canvas.restore()

                                cmd.sublabel?.let { sub ->
                                    val subLayout = StaticLayout.Builder.obtain(
                                        sub,
                                        0,
                                        sub.length,
                                        TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                                            color = COLOR_TEXT_MUTED
                                            textSize = bodyPt * 0.72f * LAYOUT_SCALE
                                        },
                                        ((cmd.rectPt.width() - 16f) * LAYOUT_SCALE).roundToInt().coerceAtLeast(16)
                                    ).setMaxLines(1).build()
                                    canvas.save()
                                    canvas.translate(cmd.rectPt.left + 8f, cmd.rectPt.top + 19f)
                                    canvas.scale(1f / LAYOUT_SCALE, 1f / LAYOUT_SCALE)
                                    subLayout.draw(canvas)
                                    canvas.restore()
                                }
                            }
                        }
                    }

                    // Draw page footer: "page / total"
                    val footerText = "${idx + 1} / $totalPages"
                    canvas.save()
                    canvas.translate(0f, pageHeightPt - FOOTER_Y_OFFSET_PT)
                    canvas.scale(1f / LAYOUT_SCALE, 1f / LAYOUT_SCALE)
                    val textW2x = footerPaint.measureText(footerText)
                    val centerX2x = (pageWidthPt * LAYOUT_SCALE - textW2x) / 2f
                    canvas.drawText(footerText, centerX2x, 0f, footerPaint)
                    canvas.restore()
                } finally {
                    ActivePageBitmaps.clear()
                    pageBitmapCache.values.forEach { it?.recycle() }
                    pageBitmapCache.clear()
                    pdfDocument.finishPage(page)
                }
                onProgress(0.42f + 0.40f * ((idx + 1).toFloat() / totalPages))
            }

            if (isCancelled()) return
            FileOutputStream(rawPdfFile).use { out ->
                pdfDocument.writeTo(out)
                out.flush()
            }
        } finally {
            runCatching { pdfDocument.close() }
        }
    }

    private fun drawVideoCardOnCanvas(
        canvas: Canvas,
        cmd: PageCommand.DrawVideoCard,
        bmp: Bitmap?,
        fillPaint: Paint,
        strokePaint: Paint,
        bitmapPaint: Paint
    ) {
        val rect = cmd.rectPt
        val radius = 5f
        canvas.save()
        val clipPath = android.graphics.Path().apply {
            addRoundRect(rect, radius, radius, android.graphics.Path.Direction.CW)
        }
        canvas.clipPath(clipPath)

        fillPaint.color = COLOR_VIDEO_BG
        canvas.drawRect(rect, fillPaint)

        if (bmp != null) {
            canvas.drawBitmap(bmp, null, rect, bitmapPaint)
        }

        // Bottom gradient / info bar
        val barH = 22f
        val barRect = RectF(rect.left, rect.bottom - barH, rect.right, rect.bottom)
        fillPaint.color = 0xCC111418.toInt()
        canvas.drawRect(barRect, fillPaint)

        val infoText = buildString {
            append(cmd.title ?: "Video")
            if (!cmd.durationLabel.isNullOrBlank()) {
                append(" · ").append(cmd.durationLabel)
            }
        }
        val infoPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFFFFFFF.toInt()
            textSize = 8.2f * LAYOUT_SCALE
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
        val infoLayout = StaticLayout.Builder.obtain(
            infoText,
            0,
            infoText.length,
            infoPaint,
            ((rect.width() - 16f) * LAYOUT_SCALE).roundToInt().coerceAtLeast(16)
        ).setMaxLines(1).build()
        canvas.save()
        canvas.translate(rect.left + 8f, rect.bottom - barH + 5f)
        canvas.scale(1f / LAYOUT_SCALE, 1f / LAYOUT_SCALE)
        infoLayout.draw(canvas)
        canvas.restore()

        // Centered Play Circle + Triangle
        val playCenterX = rect.centerX()
        val playCenterY = rect.top + (rect.height() - barH) / 2f
        val playRadius = minOf(18f, (rect.height() - barH) * 0.24f).coerceAtLeast(10f)
        fillPaint.color = COLOR_VIDEO_PLAY_BG
        canvas.drawCircle(playCenterX, playCenterY, playRadius, fillPaint)
        strokePaint.color = 0xCCFFFFFF.toInt()
        strokePaint.strokeWidth = 1.0f
        canvas.drawCircle(playCenterX, playCenterY, playRadius, strokePaint)

        val triSize = playRadius * 0.48f
        val triPath = android.graphics.Path().apply {
            moveTo(playCenterX - triSize * 0.42f, playCenterY - triSize * 0.72f)
            lineTo(playCenterX + triSize * 0.85f, playCenterY)
            lineTo(playCenterX - triSize * 0.42f, playCenterY + triSize * 0.72f)
            close()
        }
        fillPaint.color = 0xFFFFFFFF.toInt()
        canvas.drawPath(triPath, fillPaint)

        canvas.restore()

        strokePaint.color = COLOR_BORDER
        strokePaint.strokeWidth = 0.75f
        canvas.drawRoundRect(rect, radius, radius, strokePaint)
    }

    private fun decodeBitmapForDraw(file: File): Bitmap? {
        if (!file.isFile) return null
        return runCatching { BitmapFactory.decodeFile(file.absolutePath) }.getOrNull()
    }

    private fun postProcessWithPdfBox(
        rawPdfFile: File,
        outputFile: File,
        pageHeightPt: Float,
        links: List<PdfLinkAnnotation>,
        outlines: List<PdfOutlineEntry>,
        title: String?,
        author: String?,
        isCancelled: () -> Boolean
    ) {
        val document = PDDocument.load(rawPdfFile, MemoryUsageSetting.setupTempFileOnly())
        try {
            if (isCancelled()) return

            // 1. Metadata
            val info = document.documentInformation
            if (!title.isNullOrBlank()) info.title = title
            if (!author.isNullOrBlank()) info.author = author
            info.creator = "ZenConverter"
            document.documentInformation = info

            // 2. Clickable URI link annotations
            val linksByPage = links.groupBy { it.pageIndex }
            for ((pageIdx, pageLinks) in linksByPage) {
                if (isCancelled()) return
                if (pageIdx !in 0 until document.numberOfPages) continue
                val pdPage = document.getPage(pageIdx)
                val annotations = pdPage.annotations
                val pageMediaHeight = pdPage.mediaBox?.height ?: pageHeightPt
                for (link in pageLinks) {
                    val safeUrl = link.url.trim()
                    if (safeUrl.isEmpty()) continue
                    val rect = PDRectangle().apply {
                        lowerLeftX = link.leftPt
                        lowerLeftY = (pageMediaHeight - link.bottomPt).coerceAtLeast(0f)
                        upperRightX = link.rightPt
                        upperRightY = (pageMediaHeight - link.topPt).coerceAtLeast(lowerLeftY + 1f)
                    }
                    val linkAnnot = PDAnnotationLink().apply {
                        rectangle = rect
                        action = PDActionURI().apply { uri = safeUrl }
                        borderStyle = PDBorderStyleDictionary().apply { width = 0f }
                        cosObject.setItem(
                            COSName.BORDER,
                            COSArray().apply {
                                add(COSInteger.ZERO)
                                add(COSInteger.ZERO)
                                add(COSInteger.ZERO)
                            }
                        )
                    }
                    annotations.add(linkAnnot)
                }
            }

            // 3. Hierarchical H1-H3 PDF Outline Bookmarks
            if (outlines.isNotEmpty()) {
                val rootOutline = PDDocumentOutline()
                val stack = ArrayDeque<Pair<Int, PDOutlineItem>>()
                for (entry in outlines) {
                    if (entry.pageIndex !in 0 until document.numberOfPages) continue
                    val pdPage = document.getPage(entry.pageIndex)
                    val pageMediaHeight = pdPage.mediaBox?.height ?: pageHeightPt
                    val dest = PDPageXYZDestination().apply {
                        page = pdPage
                        left = 0
                        top = (pageMediaHeight - entry.topPt).roundToInt().coerceIn(0, pageMediaHeight.roundToInt())
                        zoom = 0f
                    }
                    val item = PDOutlineItem().apply {
                        this.title = entry.title
                        setDestination(dest)
                    }
                    while (stack.isNotEmpty() && stack.last().first >= entry.level) {
                        stack.removeLast()
                    }
                    if (stack.isEmpty()) {
                        rootOutline.addLast(item)
                    } else {
                        stack.last().second.addLast(item)
                    }
                    stack.addLast(entry.level to item)
                }
                rootOutline.openNode()
                document.documentCatalog.documentOutline = rootOutline
            }

            if (isCancelled()) return
            document.save(outputFile)
        } finally {
            runCatching { document.close() }
        }
    }

    private object ActivePageBitmaps {
        private val current = ThreadLocal<(File) -> Bitmap?>()
        fun set(provider: (File) -> Bitmap?) = current.set(provider)
        fun clear() = current.remove()
        fun get(): (File) -> Bitmap? = current.get() ?: { null }
    }
}
