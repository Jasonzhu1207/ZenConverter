package org.zenconverter.app.markdown

import android.util.Base64
import org.commonmark.Extension
import org.commonmark.ext.autolink.AutolinkExtension
import org.commonmark.ext.front.matter.YamlFrontMatterBlock
import org.commonmark.ext.front.matter.YamlFrontMatterExtension
import org.commonmark.ext.front.matter.YamlFrontMatterVisitor
import org.commonmark.ext.gfm.strikethrough.Strikethrough
import org.commonmark.ext.gfm.strikethrough.StrikethroughExtension
import org.commonmark.ext.gfm.tables.TableBlock as CmTableBlock
import org.commonmark.ext.gfm.tables.TableBody
import org.commonmark.ext.gfm.tables.TableCell
import org.commonmark.ext.gfm.tables.TableHead
import org.commonmark.ext.gfm.tables.TableRow
import org.commonmark.ext.gfm.tables.TablesExtension
import org.commonmark.ext.task.list.items.TaskListItemMarker
import org.commonmark.ext.task.list.items.TaskListItemsExtension
import org.commonmark.node.AbstractVisitor
import org.commonmark.node.BlockQuote
import org.commonmark.node.BulletList
import org.commonmark.node.Code
import org.commonmark.node.CustomBlock
import org.commonmark.node.CustomNode
import org.commonmark.node.Document
import org.commonmark.node.Emphasis
import org.commonmark.node.FencedCodeBlock
import org.commonmark.node.HardLineBreak
import org.commonmark.node.Heading
import org.commonmark.node.HtmlBlock
import org.commonmark.node.HtmlInline
import org.commonmark.node.Image
import org.commonmark.node.IndentedCodeBlock
import org.commonmark.node.Link
import org.commonmark.node.ListItem
import org.commonmark.node.Node
import org.commonmark.node.OrderedList
import org.commonmark.node.Paragraph
import org.commonmark.node.SoftLineBreak
import org.commonmark.node.StrongEmphasis
import org.commonmark.node.Text
import org.commonmark.node.ThematicBreak
import org.commonmark.parser.Parser
import org.zenconverter.app.R
import org.zenconverter.app.i18n.LocalizedFailure
import org.zenconverter.app.i18n.localizedText
import java.nio.charset.Charset
import java.util.Locale

internal object MarkdownAstParser {
    private val UTF8_BOM = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte())
    private const val REPLACEMENT_CHAR = '\uFFFD'
    private const val GB18030_CHARSET = "GB18030"
    private const val MATH_SENTINEL_PREFIX = "<!--ZENMD_MATH:"
    private const val MATH_SENTINEL_SUFFIX = "-->"
    private const val WIDTH_TITLE_PREFIX = "zen-width="

    private val EXTENSIONS: List<Extension> = listOf(
        TablesExtension.create(),
        StrikethroughExtension.create(),
        AutolinkExtension.create(),
        TaskListItemsExtension.create(),
        YamlFrontMatterExtension.create()
    )

    private val PARSER: Parser = Parser.builder()
        .extensions(EXTENSIONS)
        .build()

    private val FOOTNOTE_DEF_START = Regex("""^\[\^([^\]\s]+)]:\s*(.*)$""")
    private val FENCE_LINE = Regex("""^\s{0,3}(`{3,}|~{3,})(.*)$""")
    private val OBSIDIAN_EMBED = Regex("""!\[\[([^\]|\r\n]+?)(?:\|([^\]\r\n]+?))?]]""")
    private val CALLOUT_HEADER = Regex("""^\[!(NOTE|TIP|IMPORTANT|WARNING|CAUTION)]\s*(?:\r?\n|$)""", RegexOption.IGNORE_CASE)
    private val HTML_TAG_REGEX = Regex("""^<(/?)([a-zA-Z][a-zA-Z0-9]*)\b([^>]*?)(/?)>$""", RegexOption.DOT_MATCHES_ALL)

    private val SELF_CLOSING_TAGS = setOf("br", "hr", "img", "input", "source", "meta", "link", "wbr")

    fun decodeMarkdownBytes(bytes: ByteArray): String {
        if (bytes.isEmpty()) {
            throw LocalizedFailure(localizedText(R.string.text_task_message_input_file_is_empty))
        }
        val offset = if (
            bytes.size >= UTF8_BOM.size &&
            bytes[0] == UTF8_BOM[0] &&
            bytes[1] == UTF8_BOM[1] &&
            bytes[2] == UTF8_BOM[2]
        ) {
            UTF8_BOM.size
        } else {
            0
        }
        val length = bytes.size - offset
        if (length <= 0) {
            throw LocalizedFailure(localizedText(R.string.text_task_message_input_file_is_empty))
        }
        val utf8 = String(bytes, offset, length, Charsets.UTF_8)
        if (!utf8.contains(REPLACEMENT_CHAR)) return utf8
        return runCatching {
            String(bytes, offset, length, Charset.forName(GB18030_CHARSET))
        }.getOrElse { utf8 }
    }

    fun parse(markdown: String, fallbackTitle: String): ParsedMarkdownDocument {
        val normalized = markdown.replace("\r\n", "\n").replace('\r', '\n')
        val preprocessed = preprocess(normalized)
        val root = PARSER.parse(preprocessed.markdown)
        val context = ParseContext(
            footnoteDefinitions = preprocessed.footnotes
        )
        val blocks = parseContainerBlocks(
            firstChild = root.firstChild,
            inheritedStyle = MdInlineStyle(),
            inheritedAlignment = MdAlignment.Left,
            context = context
        ).toMutableList()

        if (context.referencedFootnoteOrder.isNotEmpty()) {
            val entries = context.referencedFootnoteOrder.mapIndexed { idx, id ->
                val rawDef = context.footnoteDefinitions[id].orEmpty()
                val spans = if (rawDef.isBlank()) {
                    listOf(MdSpan.TextSpan(id))
                } else {
                    parseInlineMarkdown(rawDef, MdInlineStyle(), context)
                }
                MdFootnoteEntry(id = id, index = idx + 1, spans = spans)
            }
            blocks.add(MdBlock.FootnotesSectionBlock(entries))
        }

        val fmBlock = blocks.filterIsInstance<MdBlock.FrontMatterBlock>().firstOrNull()
        val firstH1 = blocks.filterIsInstance<MdBlock.HeadingBlock>().firstOrNull { it.level == 1 }?.plainText
        val resolvedTitle = fmBlock?.title?.takeIf { it.isNotBlank() }
            ?: firstH1?.takeIf { it.isNotBlank() }
            ?: fallbackTitle
        val resolvedAuthor = fmBlock?.author?.takeIf { it.isNotBlank() }

        return ParsedMarkdownDocument(
            title = resolvedTitle,
            author = resolvedAuthor,
            blocks = blocks
        )
    }

    private data class PreprocessResult(
        val markdown: String,
        val footnotes: Map<String, String>
    )

    private fun preprocess(input: String): PreprocessResult {
        val lines = input.split('\n')
        val outLines = ArrayList<String>(lines.size)
        val footnotes = linkedMapOf<String, StringBuilder>()

        var fenceChar: Char? = null
        var fenceLen = 0
        var inMathBlock = false
        val mathBuffer = StringBuilder()
        var currentFootnoteId: String? = null

        var i = 0
        while (i < lines.size) {
            val line = lines[i]

            if (inMathBlock) {
                val trimmed = line.trim()
                if (trimmed == "$$" || trimmed.endsWith("$$")) {
                    if (trimmed != "$$") {
                        mathBuffer.append(trimmed.removeSuffix("$$").trimEnd()).append('\n')
                    }
                    emitMathSentinel(outLines, mathBuffer.toString().trim())
                    mathBuffer.clear()
                    inMathBlock = false
                } else {
                    mathBuffer.append(line).append('\n')
                }
                i++
                continue
            }

            val fenceMatch = FENCE_LINE.matchEntire(line)
            if (fenceMatch != null) {
                val marker = fenceMatch.groupValues[1]
                val info = fenceMatch.groupValues[2].trim()
                val mChar = marker[0]
                if (fenceChar == null) {
                    currentFootnoteId = null
                    fenceChar = mChar
                    fenceLen = marker.length
                } else if (mChar == fenceChar && marker.length >= fenceLen && info.isEmpty()) {
                    fenceChar = null
                    fenceLen = 0
                }
                outLines.add(line)
                i++
                continue
            }

            if (fenceChar != null) {
                outLines.add(line)
                i++
                continue
            }

            // Handle footnote continuation lines
            if (currentFootnoteId != null) {
                if (line.startsWith("    ") || line.startsWith("\t")) {
                    val trimmedCont = line.trimStart()
                    val sb = footnotes[currentFootnoteId]!!
                    if (sb.isNotEmpty()) sb.append('\n')
                    sb.append(trimmedCont)
                    i++
                    continue
                } else if (line.isBlank() && i + 1 < lines.size && (lines[i + 1].startsWith("    ") || lines[i + 1].startsWith("\t"))) {
                    footnotes[currentFootnoteId]!!.append("\n\n")
                    i++
                    continue
                } else {
                    currentFootnoteId = null
                }
            }

            // Footnote definition start
            val fnMatch = FOOTNOTE_DEF_START.matchEntire(line)
            if (fnMatch != null) {
                val id = fnMatch.groupValues[1]
                val body = fnMatch.groupValues[2]
                footnotes[id] = StringBuilder(body)
                currentFootnoteId = id
                i++
                continue
            }

            // Standalone single-line or multi-line $$...$$ math block
            val trimmed = line.trim()
            if (trimmed == "$$") {
                inMathBlock = true
                mathBuffer.clear()
                i++
                continue
            }
            if (trimmed.startsWith("$$") && trimmed.endsWith("$$") && trimmed.length > 4) {
                val inner = trimmed.substring(2, trimmed.length - 2).trim()
                emitMathSentinel(outLines, inner)
                i++
                continue
            }

            outLines.add(rewriteObsidianEmbedsOutsideCode(line))
            i++
        }

        if (inMathBlock && mathBuffer.isNotEmpty()) {
            emitMathSentinel(outLines, mathBuffer.toString().trim())
        }

        return PreprocessResult(
            markdown = outLines.joinToString("\n"),
            footnotes = footnotes.mapValues { it.value.toString().trim() }
        )
    }

    private fun emitMathSentinel(outLines: MutableList<String>, latex: String) {
        val encoded = Base64.encodeToString(latex.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
        outLines.add("")
        outLines.add("$MATH_SENTINEL_PREFIX$encoded$MATH_SENTINEL_SUFFIX")
        outLines.add("")
    }

    private fun rewriteObsidianEmbedsOutsideCode(line: String): String {
        if (!line.contains("![[")) return line
        val out = StringBuilder(line.length)
        var i = 0
        while (i < line.length) {
            if (line[i] == '`') {
                var tickLen = 0
                while (i + tickLen < line.length && line[i + tickLen] == '`') tickLen++
                val closePattern = "`".repeat(tickLen)
                val closeIdx = line.indexOf(closePattern, i + tickLen)
                if (closeIdx >= 0) {
                    out.append(line, i, closeIdx + tickLen)
                    i = closeIdx + tickLen
                    continue
                }
            }
            if (line.startsWith("![[", i)) {
                val match = OBSIDIAN_EMBED.matchAt(line, i)
                if (match != null) {
                    val target = match.groupValues[1].trim()
                    val qualifier = match.groupValues[2].trim()
                    val widthNum = qualifier.toIntOrNull()
                    val alt = when {
                        qualifier.isEmpty() -> target.substringAfterLast('/')
                        widthNum != null -> target.substringAfterLast('/')
                        else -> qualifier
                    }
                    val titlePart = if (widthNum != null) " \"$WIDTH_TITLE_PREFIX$widthNum\"" else ""
                    out.append("![").append(alt).append("](<").append(target).append(">").append(titlePart).append(")")
                    i = match.range.last + 1
                    continue
                }
            }
            out.append(line[i])
            i++
        }
        return out.toString()
    }

    private class ParseContext(
        val footnoteDefinitions: Map<String, String>
    ) {
        private val footnoteIndices = linkedMapOf<String, Int>()
        val referencedFootnoteOrder = mutableListOf<String>()

        fun referenceFootnote(id: String): Int? {
            if (!footnoteDefinitions.containsKey(id)) return null
            return footnoteIndices.getOrPut(id) {
                referencedFootnoteOrder.add(id)
                referencedFootnoteOrder.size
            }
        }
    }

    private sealed interface OpenContainerKind {
        data class Details(val summary: List<MdSpan>) : OpenContainerKind
        data class StyleWrapper(val tagName: String) : OpenContainerKind
    }

    private data class OpenContainer(
        val kind: OpenContainerKind,
        val prevStyle: MdInlineStyle,
        val prevAlignment: MdAlignment,
        val blocks: MutableList<MdBlock> = mutableListOf()
    )

    private fun parseContainerBlocks(
        firstChild: Node?,
        inheritedStyle: MdInlineStyle,
        inheritedAlignment: MdAlignment,
        context: ParseContext
    ): List<MdBlock> {
        val rootBlocks = mutableListOf<MdBlock>()
        val openContainers = ArrayDeque<OpenContainer>()
        var currentStyle = inheritedStyle
        var currentAlignment = inheritedAlignment

        fun targetList(): MutableList<MdBlock> =
            openContainers.lastOrNull()?.blocks ?: rootBlocks

        var node = firstChild
        while (node != null) {
            when (node) {
                is YamlFrontMatterBlock -> {
                    val visitor = YamlFrontMatterVisitor()
                    node.accept(visitor)
                    val data = visitor.data
                    if (data.isNotEmpty()) {
                        val entries = mutableListOf<Pair<String, String>>()
                        var title: String? = null
                        var author: String? = null
                        var date: String? = null
                        for ((k, values) in data) {
                            val joined = values.joinToString(", ").trim()
                            if (joined.isEmpty()) continue
                            entries.add(k to joined)
                            when (k.lowercase(Locale.US)) {
                                "title" -> title = joined
                                "author" -> author = joined
                                "date" -> date = joined
                            }
                        }
                        if (entries.isNotEmpty()) {
                            targetList().add(MdBlock.FrontMatterBlock(title, author, date, entries))
                        }
                    }
                }

                is HtmlBlock -> {
                    val raw = node.literal.trim()
                    val mathBlock = tryDecodeMathSentinel(raw)
                    if (mathBlock != null) {
                        targetList().add(mathBlock)
                    } else {
                        val handledAsBoundary = tryHandleSplitHtmlBoundary(
                            rawHtml = raw,
                            openContainers = openContainers,
                            currentStyle = currentStyle,
                            currentAlignment = currentAlignment,
                            context = context,
                            onStyleUpdate = { s, a ->
                                currentStyle = s
                                currentAlignment = a
                            },
                            onCloseDetails = { detailsBlock ->
                                targetList().add(detailsBlock)
                            },
                            onCloseWrapper = { innerBlocks ->
                                targetList().addAll(innerBlocks)
                            }
                        )
                        if (!handledAsBoundary) {
                            targetList().addAll(
                                parseStandaloneHtmlBlock(raw, currentStyle, currentAlignment, context)
                            )
                        }
                    }
                }

                is Heading -> {
                    val spans = collectSpansFromChildren(node.firstChild, currentStyle, context)
                    val plainText = spansToPlainText(spans)
                    if (spans.isNotEmpty()) {
                        targetList().add(
                            MdBlock.HeadingBlock(
                                level = node.level.coerceIn(1, 6),
                                spans = spans,
                                plainText = plainText,
                                alignment = currentAlignment
                            )
                        )
                    }
                }

                is Paragraph -> {
                    val block = parseParagraphNode(node, currentStyle, currentAlignment, context)
                    if (block != null) {
                        targetList().add(block)
                    }
                }

                is FencedCodeBlock -> {
                    val lang = node.info?.trim()?.substringBefore(' ')?.takeIf { it.isNotEmpty() }
                    val code = node.literal.removeSuffix("\n")
                    targetList().add(MdBlock.CodeBlock(lang, code))
                }

                is IndentedCodeBlock -> {
                    val code = node.literal.removeSuffix("\n")
                    targetList().add(MdBlock.CodeBlock(null, code))
                }

                is BlockQuote -> {
                    val rawChildren = parseContainerBlocks(
                        firstChild = node.firstChild,
                        inheritedStyle = currentStyle,
                        inheritedAlignment = currentAlignment,
                        context = context
                    ).toMutableList()
                    val (calloutType, cleanedChildren) = extractGitHubCallout(rawChildren)
                    if (cleanedChildren.isNotEmpty() || calloutType != null) {
                        targetList().add(MdBlock.BlockQuoteBlock(cleanedChildren, calloutType))
                    }
                }

                is BulletList -> {
                    val items = parseListItems(node, currentStyle, context)
                    if (items.isNotEmpty()) {
                        targetList().add(MdBlock.ListBlock(ordered = false, startNumber = 1, items = items))
                    }
                }

                is OrderedList -> {
                    val items = parseListItems(node, currentStyle, context)
                    if (items.isNotEmpty()) {
                        targetList().add(
                            MdBlock.ListBlock(
                                ordered = true,
                                startNumber = node.startNumber.coerceAtLeast(1),
                                items = items
                            )
                        )
                    }
                }

                is CmTableBlock -> {
                    parseGfmTable(node, currentStyle, context)?.let { targetList().add(it) }
                }

                is ThematicBreak -> {
                    targetList().add(MdBlock.ThematicBreakBlock)
                }

                is CustomBlock -> {
                    val children = parseContainerBlocks(node.firstChild, currentStyle, currentAlignment, context)
                    targetList().addAll(children)
                }
            }
            node = node.next
        }

        // Unwind any unclosed <details> or style wrappers cleanly
        while (openContainers.isNotEmpty()) {
            val unclosed = openContainers.removeLast()
            when (val kind = unclosed.kind) {
                is OpenContainerKind.Details -> targetList().add(MdBlock.DetailsBlock(kind.summary, unclosed.blocks))
                is OpenContainerKind.StyleWrapper -> targetList().addAll(unclosed.blocks)
            }
        }

        return rootBlocks
    }

    private fun tryDecodeMathSentinel(raw: String): MdBlock.MathBlock? {
        if (!raw.startsWith(MATH_SENTINEL_PREFIX) || !raw.endsWith(MATH_SENTINEL_SUFFIX)) return null
        val b64 = raw.removePrefix(MATH_SENTINEL_PREFIX).removeSuffix(MATH_SENTINEL_SUFFIX).trim()
        val latex = runCatching {
            String(Base64.decode(b64, Base64.DEFAULT), Charsets.UTF_8)
        }.getOrNull() ?: return null
        return MdBlock.MathBlock(
            rawLatex = latex,
            formatted = MarkdownStyleParser.formatLatexMath(latex)
        )
    }

    private fun tryHandleSplitHtmlBoundary(
        rawHtml: String,
        openContainers: ArrayDeque<OpenContainer>,
        currentStyle: MdInlineStyle,
        currentAlignment: MdAlignment,
        context: ParseContext,
        onStyleUpdate: (MdInlineStyle, MdAlignment) -> Unit,
        onCloseDetails: (MdBlock.DetailsBlock) -> Unit,
        onCloseWrapper: (List<MdBlock>) -> Unit
    ): Boolean {
        val trimmed = rawHtml.trim()
        // Case 1: opening <details> (optionally with <summary>...</summary>) without closing </details> in same block
        if (
            trimmed.contains("<details", ignoreCase = true) &&
            !trimmed.contains("</details>", ignoreCase = true)
        ) {
            val summaryRegex = Regex("""<summary\b[^>]*>(.*?)</summary>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
            val summaryMatch = summaryRegex.find(trimmed)
            val summaryRaw = summaryMatch?.groupValues?.get(1)?.trim() ?: "Details"
            val summarySpans = parseInlineMarkdown(summaryRaw, currentStyle, context)
            val afterTag = trimmed.substringAfter('>')
            val trailingBody = if (summaryMatch != null) {
                trimmed.substring(summaryMatch.range.last + 1).trim()
            } else {
                afterTag.trim()
            }
            openContainers.addLast(
                OpenContainer(
                    kind = OpenContainerKind.Details(summarySpans),
                    prevStyle = currentStyle,
                    prevAlignment = currentAlignment
                )
            )
            if (trailingBody.isNotEmpty()) {
                openContainers.last().blocks.addAll(
                    parseStandaloneHtmlBlock(trailingBody, currentStyle, currentAlignment, context)
                )
            }
            return true
        }

        // Case 2: closing </details>
        if (trimmed.equals("</details>", ignoreCase = true)) {
            val idx = openContainers.indexOfLast { it.kind is OpenContainerKind.Details }
            if (idx >= 0) {
                while (openContainers.size - 1 > idx) {
                    val inner = openContainers.removeLast()
                    onStyleUpdate(inner.prevStyle, inner.prevAlignment)
                    if (inner.kind is OpenContainerKind.Details) {
                        onCloseDetails(MdBlock.DetailsBlock(inner.kind.summary, inner.blocks))
                    } else {
                        onCloseWrapper(inner.blocks)
                    }
                }
                val details = openContainers.removeLast()
                onStyleUpdate(details.prevStyle, details.prevAlignment)
                val detailsKind = details.kind as OpenContainerKind.Details
                onCloseDetails(MdBlock.DetailsBlock(detailsKind.summary, details.blocks))
                return true
            }
        }

        // Case 3: pure opening wrapper tag like <div align="center"> or <center> or <span style="...">
        val singleTag = HTML_TAG_REGEX.matchEntire(trimmed)
        if (singleTag != null) {
            val isClosing = singleTag.groupValues[1] == "/"
            val tagName = singleTag.groupValues[2].lowercase(Locale.US)
            val attrText = singleTag.groupValues[3]
            val selfClosing = singleTag.groupValues[4] == "/" || tagName in SELF_CLOSING_TAGS
            if (!isClosing && !selfClosing && tagName in setOf("div", "center", "span", "font", "p", "section", "article")) {
                val attrs = MarkdownStyleParser.parseHtmlAttributes(attrText)
                val nextStyle = MarkdownStyleParser.applyHtmlTagStyle(currentStyle, tagName, attrs)
                val nextAlign = if (tagName == "center") {
                    MdAlignment.Center
                } else {
                    MarkdownStyleParser.parseAlignment(attrs["align"])
                        ?: MarkdownStyleParser.parseAlignment(MarkdownStyleParser.parseInlineCss(attrs["style"])["text-align"])
                        ?: currentAlignment
                }
                openContainers.addLast(
                    OpenContainer(
                        kind = OpenContainerKind.StyleWrapper(tagName),
                        prevStyle = currentStyle,
                        prevAlignment = currentAlignment
                    )
                )
                onStyleUpdate(nextStyle, nextAlign)
                return true
            }
            if (isClosing && tagName in setOf("div", "center", "span", "font", "p", "section", "article")) {
                val idx = openContainers.indexOfLast {
                    val k = it.kind
                    k is OpenContainerKind.StyleWrapper && k.tagName == tagName
                }
                if (idx >= 0) {
                    while (openContainers.size - 1 > idx) {
                        val inner = openContainers.removeLast()
                        onStyleUpdate(inner.prevStyle, inner.prevAlignment)
                        if (inner.kind is OpenContainerKind.Details) {
                            onCloseDetails(MdBlock.DetailsBlock(inner.kind.summary, inner.blocks))
                        } else {
                            onCloseWrapper(inner.blocks)
                        }
                    }
                    val closed = openContainers.removeLast()
                    onStyleUpdate(closed.prevStyle, closed.prevAlignment)
                    onCloseWrapper(closed.blocks)
                    return true
                }
            }
        }
        return false
    }

    private fun parseStandaloneHtmlBlock(
        rawHtml: String,
        inheritedStyle: MdInlineStyle,
        inheritedAlignment: MdAlignment,
        context: ParseContext
    ): List<MdBlock> {
        val strippedComments = rawHtml.replace(Regex("""<!--.*?-->""", setOf(RegexOption.DOT_MATCHES_ALL)), "").trim()
        if (strippedComments.isEmpty()) return emptyList()

        val result = mutableListOf<MdBlock>()
        var remaining = strippedComments

        // Check <details>...</details>
        val detailsRegex = Regex("""<details\b[^>]*>(.*?)</details>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
        val detailsMatch = detailsRegex.matchEntire(remaining)
        if (detailsMatch != null) {
            val inner = detailsMatch.groupValues[1]
            val summaryRegex = Regex("""<summary\b[^>]*>(.*?)</summary>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
            val summaryMatch = summaryRegex.find(inner)
            val summarySpans = parseInlineMarkdown(
                summaryMatch?.groupValues?.get(1)?.trim() ?: "Details",
                inheritedStyle,
                context
            )
            val bodyRaw = if (summaryMatch != null) {
                (inner.substring(0, summaryMatch.range.first) + inner.substring(summaryMatch.range.last + 1)).trim()
            } else {
                inner.trim()
            }
            val bodyDoc = PARSER.parse(bodyRaw)
            val children = parseContainerBlocks(bodyDoc.firstChild, inheritedStyle, inheritedAlignment, context)
            result.add(MdBlock.DetailsBlock(summarySpans, children))
            return result
        }

        // Check <video ...>...</video> or self-closing <video .../>
        val videoRegex = Regex("""^<video\b([^>]*)>(.*?)</video>$|^<video\b([^>]*)/?>$""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
        val videoMatch = videoRegex.matchEntire(remaining)
        if (videoMatch != null) {
            val attrText = if (videoMatch.groupValues[1].isNotEmpty() || videoMatch.groups[2] != null) {
                videoMatch.groupValues[1]
            } else {
                videoMatch.groupValues[3]
            }
            val innerBody = videoMatch.groupValues[2]
            val attrs = MarkdownStyleParser.parseHtmlAttributes(attrText)
            var src = attrs["src"]?.takeIf { it.isNotBlank() }
            if (src == null && innerBody.isNotBlank()) {
                val sourceTagRegex = Regex("""<source\b([^>]*)/?>""", RegexOption.IGNORE_CASE)
                for (m in sourceTagRegex.findAll(innerBody)) {
                    val sAttrs = MarkdownStyleParser.parseHtmlAttributes(m.groupValues[1])
                    val candidate = sAttrs["src"]?.takeIf { it.isNotBlank() }
                    if (candidate != null) {
                        src = candidate
                        break
                    }
                }
            }
            if (src != null) {
                val css = MarkdownStyleParser.parseInlineCss(attrs["style"])
                val poster = attrs["poster"]?.takeIf { it.isNotBlank() }
                val fallbackTitle = stripAllHtmlTags(innerBody).trim().takeIf { it.isNotBlank() }
                val title = attrs["title"]?.takeIf { it.isNotBlank() }
                    ?: attrs["alt"]?.takeIf { it.isNotBlank() }
                    ?: fallbackTitle
                val widthHint = MarkdownStyleParser.parseDimensionHint(attrs["width"] ?: css["width"])
                val heightHint = MarkdownStyleParser.parseDimensionHint(attrs["height"] ?: css["height"])
                val alignment = MarkdownStyleParser.parseAlignment(attrs["align"] ?: css["text-align"])
                    ?: inheritedAlignment
                result.add(
                    MdBlock.VideoBlock(
                        source = src,
                        posterUrl = poster,
                        title = title,
                        widthHint = widthHint,
                        heightHint = heightHint,
                        linkUrl = src.takeIf { it.startsWith("http://", true) || it.startsWith("https://", true) },
                        alignment = alignment
                    )
                )
                return result
            }
        }

        // Check HTML <table>...</table>
        val tableRegex = Regex("""^<table\b[^>]*>(.*?)</table>$""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
        val tableMatch = tableRegex.matchEntire(remaining)
        if (tableMatch != null) {
            parseHtmlTable(tableMatch.groupValues[1], inheritedStyle, context)?.let {
                result.add(it)
                return result
            }
        }

        // Check <hr>
        if (Regex("""^<hr\b[^>]*/?>$""", RegexOption.IGNORE_CASE).matches(remaining)) {
            result.add(MdBlock.ThematicBreakBlock)
            return result
        }

        // Check <pre>...</pre>
        val preRegex = Regex("""^<pre\b[^>]*>(?:<code\b([^>]*)>)?(.*?)(?:</code>)?</pre>$""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
        val preMatch = preRegex.matchEntire(remaining)
        if (preMatch != null) {
            val codeAttrs = MarkdownStyleParser.parseHtmlAttributes(preMatch.groupValues[1])
            val cls = codeAttrs["class"].orEmpty()
            val lang = Regex("""language-([a-zA-Z0-9_+-]+)""").find(cls)?.groupValues?.get(1)
            val rawCode = MarkdownStyleParser.decodeHtmlEntities(stripAllHtmlTags(preMatch.groupValues[2])).trimEnd('\n')
            result.add(MdBlock.CodeBlock(lang, rawCode))
            return result
        }

        // Check <blockquote>...</blockquote>
        val quoteRegex = Regex("""^<blockquote\b[^>]*>(.*?)</blockquote>$""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
        val quoteMatch = quoteRegex.matchEntire(remaining)
        if (quoteMatch != null) {
            val innerDoc = PARSER.parse(quoteMatch.groupValues[1].trim())
            val children = parseContainerBlocks(innerDoc.firstChild, inheritedStyle, inheritedAlignment, context)
            if (children.isNotEmpty()) {
                result.add(MdBlock.BlockQuoteBlock(children, null))
            }
            return result
        }

        // Check wrapper <p ...>...</p>, <div ...>...</div>, <center>...</center>, <h1..h6 ...>...</h1..h6>
        val wrapperRegex = Regex(
            """^<(p|div|center|h[1-6])\b([^>]*)>(.*?)</\1>$""",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
        )
        val wrapperMatch = wrapperRegex.matchEntire(remaining)
        if (wrapperMatch != null) {
            val tag = wrapperMatch.groupValues[1].lowercase(Locale.US)
            val attrs = MarkdownStyleParser.parseHtmlAttributes(wrapperMatch.groupValues[2])
            val css = MarkdownStyleParser.parseInlineCss(attrs["style"])
            val nextStyle = MarkdownStyleParser.applyHtmlTagStyle(inheritedStyle, tag, attrs)
            val nextAlignment = if (tag == "center") {
                MdAlignment.Center
            } else {
                MarkdownStyleParser.parseAlignment(attrs["align"])
                    ?: MarkdownStyleParser.parseAlignment(css["text-align"])
                    ?: inheritedAlignment
            }
            val inner = wrapperMatch.groupValues[3].trim()

            if (tag.startsWith("h") && tag.length == 2 && tag[1] in '1'..'6') {
                val level = tag[1].digitToInt()
                val spans = parseInlineMarkdown(inner, nextStyle, context)
                if (spans.isNotEmpty()) {
                    result.add(MdBlock.HeadingBlock(level, spans, spansToPlainText(spans), nextAlignment))
                }
                return result
            }

            // If inner contains nested block elements (<p>, <div>, <table>, <video>, <h1>..<h6>), recurse!
            if (Regex("""<(?:div|p|table|details|video|h[1-6]|blockquote|pre|ul|ol|hr)\b""", RegexOption.IGNORE_CASE).containsMatchIn(inner)) {
                val innerDoc = PARSER.parse(inner)
                result.addAll(parseContainerBlocks(innerDoc.firstChild, nextStyle, nextAlignment, context))
                return result
            }

            // Otherwise check single standalone <img> or <a href><img ...></a> inside wrapper
            val singleImg = tryExtractSingleHtmlImage(inner, nextAlignment)
            if (singleImg != null) {
                result.add(singleImg)
                return result
            }

            val spans = parseInlineMarkdown(inner, nextStyle, context)
            if (spans.isNotEmpty()) {
                result.add(MdBlock.ParagraphBlock(spans, nextAlignment))
            }
            return result
        }

        // Check standalone <img ...> or <a href="..."><img ...></a>
        val singleImg = tryExtractSingleHtmlImage(remaining, inheritedAlignment)
        if (singleImg != null) {
            result.add(singleImg)
            return result
        }

        // Check <ul> / <ol>
        val listRegex = Regex("""^<(ul|ol)\b[^>]*>(.*?)</\1>$""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
        val listMatch = listRegex.matchEntire(remaining)
        if (listMatch != null) {
            val ordered = listMatch.groupValues[1].equals("ol", ignoreCase = true)
            val liRegex = Regex("""<li\b[^>]*>(.*?)</li>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
            val items = liRegex.findAll(listMatch.groupValues[2]).mapNotNull { m ->
                val spans = parseInlineMarkdown(m.groupValues[1].trim(), inheritedStyle, context)
                if (spans.isEmpty()) null else MdListItem(null, listOf(MdBlock.ParagraphBlock(spans, inheritedAlignment)))
            }.toList()
            if (items.isNotEmpty()) {
                result.add(MdBlock.ListBlock(ordered = ordered, startNumber = 1, items = items))
                return result
            }
        }

        // Generic inline-HTML paragraph fallback
        val spans = parseInlineMarkdown(remaining, inheritedStyle, context)
        if (spans.isNotEmpty()) {
            result.add(MdBlock.ParagraphBlock(spans, inheritedAlignment))
        }
        return result
    }

    private fun tryExtractSingleHtmlImage(raw: String, alignment: MdAlignment): MdBlock? {
        val trimmed = raw.trim()
        val linkedImgRegex = Regex(
            """^<a\b([^>]*)>\s*<img\b([^>]*)/?>\s*</a>$""",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
        )
        val imgOnlyRegex = Regex("""^<img\b([^>]*)/?>$""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))

        var linkUrl: String? = null
        val imgAttrText: String
        val linkedMatch = linkedImgRegex.matchEntire(trimmed)
        if (linkedMatch != null) {
            val aAttrs = MarkdownStyleParser.parseHtmlAttributes(linkedMatch.groupValues[1])
            linkUrl = aAttrs["href"]?.takeIf { it.isNotBlank() && !it.startsWith("#") }
            imgAttrText = linkedMatch.groupValues[2]
        } else {
            val imgMatch = imgOnlyRegex.matchEntire(trimmed) ?: return null
            imgAttrText = imgMatch.groupValues[1]
        }

        val attrs = MarkdownStyleParser.parseHtmlAttributes(imgAttrText)
        val src = attrs["src"]?.takeIf { it.isNotBlank() } ?: return null
        val css = MarkdownStyleParser.parseInlineCss(attrs["style"])
        val alt = attrs["alt"].orEmpty()
        val title = attrs["title"]?.takeIf { it.isNotBlank() }
        val widthHint = MarkdownStyleParser.parseDimensionHint(attrs["width"] ?: css["width"])
        val heightHint = MarkdownStyleParser.parseDimensionHint(attrs["height"] ?: css["height"])
        val resolvedAlign = MarkdownStyleParser.parseAlignment(attrs["align"] ?: css["text-align"]) ?: alignment

        return if (MarkdownStyleParser.isLikelyVideoSource(src)) {
            MdBlock.VideoBlock(
                source = src,
                posterUrl = null,
                title = title ?: alt.takeIf { it.isNotBlank() },
                widthHint = widthHint,
                heightHint = heightHint,
                linkUrl = linkUrl ?: src.takeIf { it.startsWith("http://", true) || it.startsWith("https://", true) },
                alignment = resolvedAlign
            )
        } else {
            MdBlock.ImageBlock(
                source = src,
                alt = alt,
                title = title,
                widthHint = widthHint,
                heightHint = heightHint,
                linkUrl = linkUrl,
                alignment = resolvedAlign
            )
        }
    }

    private fun parseHtmlTable(
        innerHtml: String,
        inheritedStyle: MdInlineStyle,
        context: ParseContext
    ): MdBlock.TableBlock? {
        val headerRows = mutableListOf<List<MdTableCell>>()
        val bodyRows = mutableListOf<List<MdTableCell>>()

        val theadRanges = Regex("""<thead\b[^>]*>.*?</thead>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
            .findAll(innerHtml)
            .map { it.range }
            .toList()

        val rowRegex = Regex("""<tr\b[^>]*>(.*?)</tr>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
        val cellRegex = Regex("""<(th|td)\b([^>]*)>(.*?)</\1>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))

        for (rowMatch in rowRegex.findAll(innerHtml)) {
            val inThead = theadRanges.any { rowMatch.range.first in it }
            val cells = mutableListOf<MdTableCell>()
            var hasTh = false
            for (cellMatch in cellRegex.findAll(rowMatch.groupValues[1])) {
                val tag = cellMatch.groupValues[1].lowercase(Locale.US)
                val isHeader = tag == "th" || inThead
                if (tag == "th") hasTh = true
                val attrs = MarkdownStyleParser.parseHtmlAttributes(cellMatch.groupValues[2])
                val css = MarkdownStyleParser.parseInlineCss(attrs["style"])
                val align = MarkdownStyleParser.parseAlignment(attrs["align"] ?: css["text-align"])
                    ?: if (tag == "th") MdAlignment.Center else MdAlignment.Left
                val colSpan = attrs["colspan"]?.toIntOrNull()?.coerceIn(1, 16) ?: 1
                val cellStyle = MarkdownStyleParser.applyHtmlTagStyle(inheritedStyle, tag, attrs)
                val spans = parseInlineMarkdown(cellMatch.groupValues[3].trim(), cellStyle, context)
                cells.add(MdTableCell(spans = spans, alignment = align, isHeader = isHeader, colSpan = colSpan))
            }
            if (cells.isNotEmpty()) {
                if ((inThead || (hasTh && bodyRows.isEmpty()))) {
                    headerRows.add(cells)
                } else {
                    bodyRows.add(cells)
                }
            }
        }
        if (headerRows.isEmpty() && bodyRows.isEmpty()) return null
        val colCount = (headerRows + bodyRows).maxOfOrNull { row -> row.sumOf { it.colSpan } } ?: 1
        val alignments = MutableList(colCount) { MdAlignment.Left }
        val sampleRow = headerRows.firstOrNull() ?: bodyRows.firstOrNull().orEmpty()
        var colCursor = 0
        for (cell in sampleRow) {
            for (offset in 0 until cell.colSpan) {
                if (colCursor + offset < alignments.size) {
                    alignments[colCursor + offset] = cell.alignment
                }
            }
            colCursor += cell.colSpan
        }
        return MdBlock.TableBlock(headerRows, bodyRows, alignments)
    }

    private fun parseParagraphNode(
        paragraph: Paragraph,
        inheritedStyle: MdInlineStyle,
        inheritedAlignment: MdAlignment,
        context: ParseContext
    ): MdBlock? {
        val first = paragraph.firstChild
        if (first != null && first.next == null) {
            if (first is Image) {
                return blockForImageNode(first, linkUrl = null, alignment = MdAlignment.Center, context = context)
            }
            if (first is Link) {
                val linkFirst = first.firstChild
                if (linkFirst is Image && linkFirst.next == null) {
                    return blockForImageNode(
                        linkFirst,
                        linkUrl = first.destination,
                        alignment = MdAlignment.Center,
                        context = context
                    )
                }
                if (MarkdownStyleParser.isLikelyVideoSource(first.destination)) {
                    val spans = collectSpansFromChildren(first.firstChild, inheritedStyle, context)
                    val title = spansToPlainText(spans).takeIf { it.isNotBlank() }
                    return MdBlock.VideoBlock(
                        source = first.destination,
                        posterUrl = null,
                        title = title,
                        linkUrl = first.destination,
                        alignment = MdAlignment.Center
                    )
                }
            }
        }

        val spans = collectSpansFromChildren(paragraph.firstChild, inheritedStyle, context)
        if (spans.isEmpty()) return null
        if (spans.size == 1 && spans[0] is MdSpan.InlineImageSpan) {
            val inlineImg = spans[0] as MdSpan.InlineImageSpan
            return if (MarkdownStyleParser.isLikelyVideoSource(inlineImg.source)) {
                MdBlock.VideoBlock(
                    source = inlineImg.source,
                    posterUrl = null,
                    title = inlineImg.alt.takeIf { it.isNotBlank() },
                    widthHint = inlineImg.widthHint,
                    heightHint = inlineImg.heightHint,
                    linkUrl = inlineImg.linkUrl,
                    alignment = inheritedAlignment
                )
            } else {
                MdBlock.ImageBlock(
                    source = inlineImg.source,
                    alt = inlineImg.alt,
                    title = null,
                    widthHint = inlineImg.widthHint,
                    heightHint = inlineImg.heightHint,
                    linkUrl = inlineImg.linkUrl,
                    alignment = inheritedAlignment
                )
            }
        }
        return MdBlock.ParagraphBlock(spans, inheritedAlignment)
    }

    private fun blockForImageNode(
        image: Image,
        linkUrl: String?,
        alignment: MdAlignment,
        context: ParseContext
    ): MdBlock {
        val altText = spansToPlainText(collectSpansFromChildren(image.firstChild, MdInlineStyle(), context))
        val rawTitle = image.title?.trim()
        var widthHint: MdDimensionHint? = null
        var displayTitle: String? = rawTitle
        if (rawTitle != null && rawTitle.startsWith(WIDTH_TITLE_PREFIX)) {
            widthHint = MarkdownStyleParser.parseDimensionHint(rawTitle.removePrefix(WIDTH_TITLE_PREFIX))
            displayTitle = null
        }
        val dest = image.destination.orEmpty()
        return if (MarkdownStyleParser.isLikelyVideoSource(dest)) {
            MdBlock.VideoBlock(
                source = dest,
                posterUrl = null,
                title = displayTitle ?: altText.takeIf { it.isNotBlank() },
                widthHint = widthHint,
                linkUrl = linkUrl ?: dest.takeIf { it.startsWith("http://", true) || it.startsWith("https://", true) },
                alignment = alignment
            )
        } else {
            MdBlock.ImageBlock(
                source = dest,
                alt = altText,
                title = displayTitle,
                widthHint = widthHint,
                linkUrl = linkUrl,
                alignment = alignment
            )
        }
    }

    private fun extractGitHubCallout(blocks: MutableList<MdBlock>): Pair<MdCalloutType?, List<MdBlock>> {
        val first = blocks.firstOrNull() as? MdBlock.ParagraphBlock ?: return null to blocks
        val firstTextSpan = first.spans.firstOrNull() as? MdSpan.TextSpan ?: return null to blocks
        val match = CALLOUT_HEADER.find(firstTextSpan.text) ?: return null to blocks

        val type = when (match.groupValues[1].uppercase(Locale.US)) {
            "NOTE" -> MdCalloutType.Note
            "TIP" -> MdCalloutType.Tip
            "IMPORTANT" -> MdCalloutType.Important
            "WARNING" -> MdCalloutType.Warning
            "CAUTION" -> MdCalloutType.Caution
            else -> return null to blocks
        }

        val remainder = firstTextSpan.text.substring(match.range.last + 1)
        val nextSpans = first.spans.toMutableList()
        if (remainder.isNotEmpty()) {
            nextSpans[0] = firstTextSpan.copy(text = remainder)
        } else {
            nextSpans.removeAt(0)
            if (nextSpans.firstOrNull() is MdSpan.LineBreakSpan) {
                nextSpans.removeAt(0)
            }
        }
        val cleanedBlocks = blocks.toMutableList()
        if (nextSpans.isEmpty()) {
            cleanedBlocks.removeAt(0)
        } else {
            cleanedBlocks[0] = first.copy(spans = nextSpans)
        }
        return type to cleanedBlocks
    }

    private fun parseListItems(
        listNode: Node,
        inheritedStyle: MdInlineStyle,
        context: ParseContext
    ): List<MdListItem> {
        val items = mutableListOf<MdListItem>()
        var child = listNode.firstChild
        while (child != null) {
            if (child is ListItem) {
                var checked: Boolean? = null
                var itemChild = child.firstChild
                if (itemChild is TaskListItemMarker) {
                    checked = itemChild.isChecked
                    itemChild = itemChild.next
                }
                val itemBlocks = parseContainerBlocks(
                    firstChild = itemChild,
                    inheritedStyle = inheritedStyle,
                    inheritedAlignment = MdAlignment.Left,
                    context = context
                )
                items.add(MdListItem(checked = checked, blocks = itemBlocks))
            }
            child = child.next
        }
        return items
    }

    private fun parseGfmTable(
        tableNode: CmTableBlock,
        inheritedStyle: MdInlineStyle,
        context: ParseContext
    ): MdBlock.TableBlock? {
        val headerRows = mutableListOf<List<MdTableCell>>()
        val bodyRows = mutableListOf<List<MdTableCell>>()

        var section = tableNode.firstChild
        while (section != null) {
            val isHead = section is TableHead
            if (section is TableHead || section is TableBody) {
                var rowNode = section.firstChild
                while (rowNode != null) {
                    if (rowNode is TableRow) {
                        val rowCells = mutableListOf<MdTableCell>()
                        var cellNode = rowNode.firstChild
                        while (cellNode != null) {
                            if (cellNode is TableCell) {
                                val alignment = when (cellNode.alignment) {
                                    TableCell.Alignment.CENTER -> MdAlignment.Center
                                    TableCell.Alignment.RIGHT -> MdAlignment.Right
                                    TableCell.Alignment.LEFT, null -> MdAlignment.Left
                                }
                                val baseCellStyle = if (cellNode.isHeader || isHead) {
                                    inheritedStyle.copy(bold = true, fontWeight = maxOf(inheritedStyle.fontWeight ?: 700, 700))
                                } else {
                                    inheritedStyle
                                }
                                val spans = collectSpansFromChildren(cellNode.firstChild, baseCellStyle, context)
                                rowCells.add(
                                    MdTableCell(
                                        spans = spans,
                                        alignment = alignment,
                                        isHeader = cellNode.isHeader || isHead
                                    )
                                )
                            }
                            cellNode = cellNode.next
                        }
                        if (rowCells.isNotEmpty()) {
                            if (isHead) headerRows.add(rowCells) else bodyRows.add(rowCells)
                        }
                    }
                    rowNode = rowNode.next
                }
            }
            section = section.next
        }

        if (headerRows.isEmpty() && bodyRows.isEmpty()) return null
        val colCount = (headerRows + bodyRows).maxOfOrNull { it.size } ?: 1
        val alignments = List(colCount) { colIdx ->
            headerRows.firstOrNull()?.getOrNull(colIdx)?.alignment
                ?: bodyRows.firstOrNull()?.getOrNull(colIdx)?.alignment
                ?: MdAlignment.Left
        }
        return MdBlock.TableBlock(headerRows, bodyRows, alignments)
    }

    private fun parseInlineMarkdown(
        markdown: String,
        baseStyle: MdInlineStyle,
        context: ParseContext
    ): List<MdSpan> {
        if (markdown.isEmpty()) return emptyList()
        val doc = PARSER.parse(markdown)
        val out = mutableListOf<MdSpan>()
        var child = doc.firstChild
        while (child != null) {
            if (out.isNotEmpty()) {
                out.add(MdSpan.LineBreakSpan)
            }
            when (child) {
                is Paragraph -> out.addAll(collectSpansFromChildren(child.firstChild, baseStyle, context))
                is HtmlBlock -> out.addAll(collectSpansFromRawHtmlFragment(child.literal, baseStyle, context))
                else -> out.addAll(collectSpansFromChildren(child.firstChild, baseStyle, context))
            }
            child = child.next
        }
        return out
    }

    private fun collectSpansFromRawHtmlFragment(
        html: String,
        baseStyle: MdInlineStyle,
        context: ParseContext
    ): List<MdSpan> {
        val out = mutableListOf<MdSpan>()
        val styleStack = ArrayDeque<Pair<String, MdInlineStyle>>()
        styleStack.addLast("root" to baseStyle)
        val tagFinder = Regex("""<[^>]+>""")
        var cursor = 0
        for (match in tagFinder.findAll(html)) {
            if (match.range.first > cursor) {
                val text = MarkdownStyleParser.decodeHtmlEntities(html.substring(cursor, match.range.first))
                collectPlainTextExtras(text, styleStack.last().second, context, out)
            }
            handleInlineHtmlTag(match.value, styleStack, out)
            cursor = match.range.last + 1
        }
        if (cursor < html.length) {
            val text = MarkdownStyleParser.decodeHtmlEntities(html.substring(cursor))
            collectPlainTextExtras(text, styleStack.last().second, context, out)
        }
        return out
    }

    private fun collectSpansFromChildren(
        firstChild: Node?,
        baseStyle: MdInlineStyle,
        context: ParseContext
    ): List<MdSpan> {
        val out = mutableListOf<MdSpan>()
        val htmlStyleStack = ArrayDeque<Pair<String, MdInlineStyle>>()
        htmlStyleStack.addLast("root" to baseStyle)
        walkInlineNodes(firstChild, htmlStyleStack, context, out)
        return mergeAdjacentTextSpans(out)
    }

    private fun walkInlineNodes(
        firstChild: Node?,
        styleStack: ArrayDeque<Pair<String, MdInlineStyle>>,
        context: ParseContext,
        out: MutableList<MdSpan>
    ) {
        var node = firstChild
        while (node != null) {
            val currentStyle = styleStack.last().second
            when (node) {
                is Text -> {
                    collectPlainTextExtras(node.literal, currentStyle, context, out)
                }

                is Code -> {
                    out.add(
                        MdSpan.TextSpan(
                            text = node.literal,
                            style = currentStyle.copy(
                                code = true,
                                fontFamily = MdFontFamily.Monospace
                            )
                        )
                    )
                }

                is Emphasis -> {
                    styleStack.addLast("em-md" to currentStyle.copy(italic = true))
                    walkInlineNodes(node.firstChild, styleStack, context, out)
                    styleStack.removeLast()
                }

                is StrongEmphasis -> {
                    styleStack.addLast(
                        "strong-md" to currentStyle.copy(
                            bold = true,
                            fontWeight = maxOf(currentStyle.fontWeight ?: 700, 700)
                        )
                    )
                    walkInlineNodes(node.firstChild, styleStack, context, out)
                    styleStack.removeLast()
                }

                is Strikethrough -> {
                    styleStack.addLast("del-md" to currentStyle.copy(strikethrough = true))
                    walkInlineNodes(node.firstChild, styleStack, context, out)
                    styleStack.removeLast()
                }

                is Link -> {
                    val dest = node.destination?.takeIf { it.isNotBlank() }
                    val linkStyle = if (dest != null) {
                        currentStyle.copy(linkUrl = dest, underline = true)
                    } else {
                        currentStyle
                    }
                    styleStack.addLast("a-md" to linkStyle)
                    walkInlineNodes(node.firstChild, styleStack, context, out)
                    styleStack.removeLast()
                }

                is Image -> {
                    val altText = spansToPlainText(
                        collectSpansFromChildren(node.firstChild, MdInlineStyle(), context)
                    )
                    val titleRaw = node.title?.trim()
                    val widthHint = if (titleRaw != null && titleRaw.startsWith(WIDTH_TITLE_PREFIX)) {
                        MarkdownStyleParser.parseDimensionHint(titleRaw.removePrefix(WIDTH_TITLE_PREFIX))
                    } else {
                        null
                    }
                    out.add(
                        MdSpan.InlineImageSpan(
                            source = node.destination.orEmpty(),
                            alt = altText,
                            linkUrl = currentStyle.linkUrl,
                            widthHint = widthHint
                        )
                    )
                }

                is SoftLineBreak -> {
                    // CJK-friendly soft break: keep no extra space between adjacent CJK characters
                    val prevChar = (out.lastOrNull() as? MdSpan.TextSpan)?.text?.lastOrNull()
                    if (prevChar != null && isCjkChar(prevChar)) {
                        out.add(MdSpan.TextSpan("", currentStyle))
                    } else {
                        out.add(MdSpan.TextSpan(" ", currentStyle))
                    }
                }

                is HardLineBreak -> {
                    out.add(MdSpan.LineBreakSpan)
                }

                is HtmlInline -> {
                    handleInlineHtmlTag(node.literal, styleStack, out)
                }

                is CustomNode -> {
                    walkInlineNodes(node.firstChild, styleStack, context, out)
                }
            }
            node = node.next
        }
    }

    private fun handleInlineHtmlTag(
        rawTag: String,
        styleStack: ArrayDeque<Pair<String, MdInlineStyle>>,
        out: MutableList<MdSpan>
    ) {
        val trimmed = rawTag.trim()
        if (trimmed.startsWith("<!--")) return
        val match = HTML_TAG_REGEX.matchEntire(trimmed) ?: return
        val isClosing = match.groupValues[1] == "/"
        val tagName = match.groupValues[2].lowercase(Locale.US)
        val attrText = match.groupValues[3]
        val isSelfClosing = match.groupValues[4] == "/" || tagName in SELF_CLOSING_TAGS

        if (isClosing) {
            val matchIndex = styleStack.indexOfLast { it.first == tagName }
            if (matchIndex > 0) {
                while (styleStack.size - 1 >= matchIndex) {
                    styleStack.removeLast()
                }
            }
            return
        }

        if (tagName == "br") {
            out.add(MdSpan.LineBreakSpan)
            return
        }

        if (tagName == "img") {
            val attrs = MarkdownStyleParser.parseHtmlAttributes(attrText)
            val src = attrs["src"]?.takeIf { it.isNotBlank() } ?: return
            val css = MarkdownStyleParser.parseInlineCss(attrs["style"])
            out.add(
                MdSpan.InlineImageSpan(
                    source = src,
                    alt = attrs["alt"].orEmpty(),
                    linkUrl = styleStack.last().second.linkUrl,
                    widthHint = MarkdownStyleParser.parseDimensionHint(attrs["width"] ?: css["width"]),
                    heightHint = MarkdownStyleParser.parseDimensionHint(attrs["height"] ?: css["height"])
                )
            )
            return
        }

        if (!isSelfClosing) {
            val attrs = MarkdownStyleParser.parseHtmlAttributes(attrText)
            val nextStyle = MarkdownStyleParser.applyHtmlTagStyle(styleStack.last().second, tagName, attrs)
            styleStack.addLast(tagName to nextStyle)
        }
    }

    /**
     * Parses inline extras inside plain text nodes:
     * - Footnote refs: [^id]
     * - Highlight: ==text==
     * - Inline math: $expr$ (strict Pandoc-like currency-safe boundaries)
     */
    private fun collectPlainTextExtras(
        text: String,
        style: MdInlineStyle,
        context: ParseContext,
        out: MutableList<MdSpan>
    ) {
        if (text.isEmpty()) return
        var i = 0
        val plain = StringBuilder()

        fun flushPlain() {
            if (plain.isNotEmpty()) {
                out.add(MdSpan.TextSpan(plain.toString(), style))
                plain.clear()
            }
        }

        while (i < text.length) {
            // 1. Footnote reference [^id]
            if (text.startsWith("[^", i)) {
                val close = text.indexOf(']', i + 2)
                if (close > i + 2) {
                    val id = text.substring(i + 2, close)
                    if (!id.any { it.isWhitespace() }) {
                        val fnNumber = context.referenceFootnote(id)
                        if (fnNumber != null) {
                            flushPlain()
                            out.add(
                                MdSpan.TextSpan(
                                    text = "[$fnNumber]",
                                    style = style.copy(
                                        superscript = true,
                                        subscript = false,
                                        fontSizeScale = (style.fontSizeScale * 0.78f).coerceIn(0.4f, 4.0f)
                                    )
                                )
                            )
                            i = close + 1
                            continue
                        }
                    }
                }
            }

            // 2. Highlight ==text==
            if (text.startsWith("==", i) && i + 2 < text.length && !text[i + 2].isWhitespace()) {
                val close = text.indexOf("==", i + 2)
                if (close > i + 2 && !text[close - 1].isWhitespace()) {
                    val highlighted = text.substring(i + 2, close)
                    flushPlain()
                    out.add(MdSpan.TextSpan(highlighted, style.copy(mark = true)))
                    i = close + 2
                    continue
                }
            }

            // 3. Inline math $expr$ (guard against $100 or $5 to $10 currency strings)
            if (text[i] == '$' && (i == 0 || text[i - 1] != '\\') && (i + 1 >= text.length || text[i + 1] != '$')) {
                if (i + 1 < text.length && !text[i + 1].isWhitespace()) {
                    val close = findInlineMathEnd(text, i + 1)
                    if (close != null) {
                        val rawMath = text.substring(i + 1, close)
                        if (!looksLikeCurrencyRange(rawMath)) {
                            flushPlain()
                            val formatted = MarkdownStyleParser.formatLatexMath(rawMath)
                            out.add(
                                MdSpan.TextSpan(
                                    text = formatted,
                                    style = style.copy(italic = true)
                                )
                            )
                            i = close + 1
                            continue
                        }
                    }
                }
            }

            plain.append(text[i])
            i++
        }
        flushPlain()
    }

    private fun findInlineMathEnd(text: String, startIdx: Int): Int? {
        var j = startIdx
        while (j < text.length) {
            val ch = text[j]
            if (ch == '\n') return null
            if (ch == '\\') {
                j += 2
                continue
            }
            if (ch == '$') {
                val prev = text[j - 1]
                val next = text.getOrNull(j + 1)
                if (!prev.isWhitespace() && next != '$' && (next == null || !next.isDigit())) {
                    return j
                }
            }
            j++
        }
        return null
    }

    private fun looksLikeCurrencyRange(inner: String): Boolean {
        // e.g. "$5 and $10" -> inner starts with digit + space
        val trimmed = inner.trim()
        if (trimmed.isEmpty()) return true
        if (trimmed.first().isDigit() && trimmed.any { it.isWhitespace() } && !trimmed.contains('\\') && !trimmed.contains('=') && !trimmed.contains('^') && !trimmed.contains('_')) {
            return true
        }
        return false
    }

    private fun mergeAdjacentTextSpans(spans: List<MdSpan>): List<MdSpan> {
        if (spans.size <= 1) return spans
        val merged = ArrayList<MdSpan>(spans.size)
        for (span in spans) {
            if (span is MdSpan.TextSpan) {
                if (span.text.isEmpty()) continue
                val prev = merged.lastOrNull()
                if (prev is MdSpan.TextSpan && prev.style == span.style) {
                    merged[merged.lastIndex] = prev.copy(text = prev.text + span.text)
                    continue
                }
            }
            merged.add(span)
        }
        return merged
    }

    private fun spansToPlainText(spans: List<MdSpan>): String {
        val sb = StringBuilder()
        for (span in spans) {
            when (span) {
                is MdSpan.TextSpan -> sb.append(span.text)
                is MdSpan.InlineImageSpan -> sb.append(span.alt)
                MdSpan.LineBreakSpan -> sb.append(' ')
            }
        }
        return sb.toString().trim()
    }

    private fun stripAllHtmlTags(html: String): String {
        return html.replace(Regex("""<[^>]+>"""), "")
    }

    private fun isCjkChar(ch: Char): Boolean {
        val block = Character.UnicodeBlock.of(ch)
        return block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS ||
            block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_A ||
            block == Character.UnicodeBlock.CJK_COMPATIBILITY_IDEOGRAPHS ||
            block == Character.UnicodeBlock.HIRAGANA ||
            block == Character.UnicodeBlock.KATAKANA ||
            block == Character.UnicodeBlock.HANGUL_SYLLABLES ||
            block == Character.UnicodeBlock.CJK_SYMBOLS_AND_PUNCTUATION
    }
}
