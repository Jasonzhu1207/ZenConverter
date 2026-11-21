package org.zenconverter.app.markdown

enum class MdAlignment {
    Left,
    Center,
    Right
}

enum class MdFontFamily {
    Sans,
    Serif,
    Monospace
}

enum class MdCalloutType {
    Note,
    Tip,
    Important,
    Warning,
    Caution
}

data class MdDimensionHint(
    val value: Float,
    val isPercent: Boolean = false
)

data class MdInlineStyle(
    val bold: Boolean = false,
    val fontWeight: Int? = null,
    val italic: Boolean = false,
    val underline: Boolean = false,
    val strikethrough: Boolean = false,
    val code: Boolean = false,
    val kbd: Boolean = false,
    val mark: Boolean = false,
    val subscript: Boolean = false,
    val superscript: Boolean = false,
    val fontSizePt: Float? = null,
    val fontSizeScale: Float = 1.0f,
    val textColor: Int? = null,
    val backgroundColor: Int? = null,
    val fontFamily: MdFontFamily? = null,
    val linkUrl: String? = null
)

sealed interface MdSpan {
    data class TextSpan(
        val text: String,
        val style: MdInlineStyle = MdInlineStyle()
    ) : MdSpan

    data class InlineImageSpan(
        val source: String,
        val alt: String,
        val linkUrl: String? = null,
        val widthHint: MdDimensionHint? = null,
        val heightHint: MdDimensionHint? = null
    ) : MdSpan

    data object LineBreakSpan : MdSpan
}

data class MdTableCell(
    val spans: List<MdSpan>,
    val alignment: MdAlignment = MdAlignment.Left,
    val isHeader: Boolean = false,
    val colSpan: Int = 1
)

data class MdListItem(
    val checked: Boolean? = null,
    val blocks: List<MdBlock>
)

data class MdFootnoteEntry(
    val id: String,
    val index: Int,
    val spans: List<MdSpan>
)

sealed interface MdBlock {
    data class FrontMatterBlock(
        val title: String?,
        val author: String?,
        val date: String?,
        val entries: List<Pair<String, String>>
    ) : MdBlock

    data class HeadingBlock(
        val level: Int,
        val spans: List<MdSpan>,
        val plainText: String,
        val alignment: MdAlignment = MdAlignment.Left
    ) : MdBlock

    data class ParagraphBlock(
        val spans: List<MdSpan>,
        val alignment: MdAlignment = MdAlignment.Left
    ) : MdBlock

    data class CodeBlock(
        val language: String?,
        val code: String
    ) : MdBlock

    data class BlockQuoteBlock(
        val children: List<MdBlock>,
        val calloutType: MdCalloutType? = null
    ) : MdBlock

    data class ListBlock(
        val ordered: Boolean,
        val startNumber: Int,
        val items: List<MdListItem>
    ) : MdBlock

    data class TableBlock(
        val headerRows: List<List<MdTableCell>>,
        val bodyRows: List<List<MdTableCell>>,
        val columnAlignments: List<MdAlignment>
    ) : MdBlock

    data object ThematicBreakBlock : MdBlock

    data class ImageBlock(
        val source: String,
        val alt: String,
        val title: String?,
        val widthHint: MdDimensionHint? = null,
        val heightHint: MdDimensionHint? = null,
        val linkUrl: String? = null,
        val alignment: MdAlignment = MdAlignment.Center
    ) : MdBlock

    data class VideoBlock(
        val source: String,
        val posterUrl: String?,
        val title: String?,
        val widthHint: MdDimensionHint? = null,
        val heightHint: MdDimensionHint? = null,
        val linkUrl: String? = null,
        val alignment: MdAlignment = MdAlignment.Center
    ) : MdBlock

    data class DetailsBlock(
        val summary: List<MdSpan>,
        val children: List<MdBlock>
    ) : MdBlock

    data class MathBlock(
        val rawLatex: String,
        val formatted: String
    ) : MdBlock

    data class FootnotesSectionBlock(
        val entries: List<MdFootnoteEntry>
    ) : MdBlock
}

data class ParsedMarkdownDocument(
    val title: String?,
    val author: String?,
    val blocks: List<MdBlock>
)
