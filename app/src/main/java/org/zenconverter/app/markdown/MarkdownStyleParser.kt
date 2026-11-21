package org.zenconverter.app.markdown

import java.util.Locale
import kotlin.math.roundToInt

internal object MarkdownStyleParser {
    private val VIDEO_EXTENSIONS = setOf(
        "mp4", "m4v", "mov", "mkv", "webm", "avi", "3gp", "3gpp", "ogv", "flv", "ts", "m2ts"
    )

    private val NAMED_COLORS = mapOf(
        "black" to 0xFF000000.toInt(),
        "white" to 0xFFFFFFFF.toInt(),
        "red" to 0xFFD32F2F.toInt(),
        "crimson" to 0xFFDC143C.toInt(),
        "darkred" to 0xFF8B0000.toInt(),
        "maroon" to 0xFF800000.toInt(),
        "tomato" to 0xFFFF6347.toInt(),
        "coral" to 0xFFFF7F50.toInt(),
        "orange" to 0xFFEF6C00.toInt(),
        "darkorange" to 0xFFFF8C00.toInt(),
        "gold" to 0xFFD97706.toInt(),
        "yellow" to 0xFFFBC02D.toInt(),
        "olive" to 0xFF808000.toInt(),
        "green" to 0xFF2E7D32.toInt(),
        "darkgreen" to 0xFF006400.toInt(),
        "limegreen" to 0xFF32CD32.toInt(),
        "lime" to 0xFF00C853.toInt(),
        "teal" to 0xFF008080.toInt(),
        "cyan" to 0xFF00838F.toInt(),
        "aqua" to 0xFF00BCD4.toInt(),
        "blue" to 0xFF1565C0.toInt(),
        "darkblue" to 0xFF00008B.toInt(),
        "navy" to 0xFF000080.toInt(),
        "royalblue" to 0xFF4169E1.toInt(),
        "dodgerblue" to 0xFF1E90FF.toInt(),
        "steelblue" to 0xFF4682B4.toInt(),
        "indigo" to 0xFF4B0082.toInt(),
        "purple" to 0xFF6A1B9A.toInt(),
        "violet" to 0xFF8E24AA.toInt(),
        "magenta" to 0xFFC2185B.toInt(),
        "pink" to 0xFFD81B60.toInt(),
        "brown" to 0xFF6D4C41.toInt(),
        "gray" to 0xFF6E7781.toInt(),
        "grey" to 0xFF6E7781.toInt(),
        "darkgray" to 0xFF4B5563.toInt(),
        "darkgrey" to 0xFF4B5563.toInt(),
        "lightgray" to 0xFFD1D5DB.toInt(),
        "lightgrey" to 0xFFD1D5DB.toInt(),
        "silver" to 0xFFC0C0C0.toInt(),
        "slategray" to 0xFF64748B.toInt(),
        "slategrey" to 0xFF64748B.toInt()
    )

    private val HTML_ATTR_REGEX = Regex(
        """([a-zA-Z_:][-a-zA-Z0-9_:.]*)(?:\s*=\s*(?:"([^"]*)"|'([^']*)'|([^\s"'=<>`]+)))?"""
    )

    private val RGB_REGEX = Regex(
        """rgba?\(\s*(\d{1,3})\s*,\s*(\d{1,3})\s*,\s*(\d{1,3})(?:\s*,\s*([0-9]*\.?[0-9]+%?))?\s*\)""",
        RegexOption.IGNORE_CASE
    )

    private val NAMED_ENTITIES = mapOf(
        "amp" to "&",
        "lt" to "<",
        "gt" to ">",
        "quot" to "\"",
        "apos" to "'",
        "#39" to "'",
        "nbsp" to "\u00A0",
        "mdash" to "—",
        "ndash" to "–",
        "hellip" to "…",
        "copy" to "©",
        "reg" to "®",
        "trade" to "™",
        "times" to "×",
        "divide" to "÷",
        "plusmn" to "±",
        "laquo" to "«",
        "raquo" to "»",
        "bull" to "•",
        "middot" to "·",
        "rarr" to "→",
        "larr" to "←",
        "harr" to "↔",
        "rArr" to "⇒",
        "le" to "≤",
        "ge" to "≥",
        "ne" to "≠",
        "approx" to "≈"
    )

    private val LATEX_COMMANDS = linkedMapOf(
        "\\alpha" to "α",
        "\\beta" to "β",
        "\\gamma" to "γ",
        "\\delta" to "δ",
        "\\epsilon" to "ε",
        "\\varepsilon" to "ε",
        "\\zeta" to "ζ",
        "\\eta" to "η",
        "\\theta" to "θ",
        "\\iota" to "ι",
        "\\kappa" to "κ",
        "\\lambda" to "λ",
        "\\mu" to "μ",
        "\\nu" to "ν",
        "\\xi" to "ξ",
        "\\pi" to "π",
        "\\rho" to "ρ",
        "\\sigma" to "σ",
        "\\tau" to "τ",
        "\\upsilon" to "υ",
        "\\phi" to "φ",
        "\\varphi" to "φ",
        "\\chi" to "χ",
        "\\psi" to "ψ",
        "\\omega" to "ω",
        "\\Gamma" to "Γ",
        "\\Delta" to "Δ",
        "\\Theta" to "Θ",
        "\\Lambda" to "Λ",
        "\\Xi" to "Ξ",
        "\\Pi" to "Π",
        "\\Sigma" to "Σ",
        "\\Phi" to "Φ",
        "\\Psi" to "Ψ",
        "\\Omega" to "Ω",
        "\\sum" to "∑",
        "\\prod" to "∏",
        "\\int" to "∫",
        "\\oint" to "∮",
        "\\infty" to "∞",
        "\\partial" to "∂",
        "\\nabla" to "∇",
        "\\forall" to "∀",
        "\\exists" to "∃",
        "\\nexists" to "∄",
        "\\in" to "∈",
        "\\notin" to "∉",
        "\\subset" to "⊂",
        "\\subseteq" to "⊆",
        "\\supset" to "⊃",
        "\\supseteq" to "⊇",
        "\\cup" to "∪",
        "\\cap" to "∩",
        "\\emptyset" to "∅",
        "\\varnothing" to "∅",
        "\\pm" to "±",
        "\\mp" to "∓",
        "\\times" to "×",
        "\\div" to "÷",
        "\\cdot" to "·",
        "\\ast" to "∗",
        "\\star" to "★",
        "\\leq" to "≤",
        "\\le" to "≤",
        "\\geq" to "≥",
        "\\ge" to "≥",
        "\\neq" to "≠",
        "\\ne" to "≠",
        "\\approx" to "≈",
        "\\equiv" to "≡",
        "\\sim" to "∼",
        "\\simeq" to "≃",
        "\\cong" to "≅",
        "\\propto" to "∝",
        "\\rightarrow" to "→",
        "\\to" to "→",
        "\\leftarrow" to "←",
        "\\leftrightarrow" to "↔",
        "\\Rightarrow" to "⇒",
        "\\implies" to "⇒",
        "\\Leftarrow" to "⇐",
        "\\Leftrightarrow" to "⇔",
        "\\iff" to "⇔",
        "\\mapsto" to "↦",
        "\\land" to "∧",
        "\\lor" to "∨",
        "\\neg" to "¬",
        "\\angle" to "∠",
        "\\perp" to "⊥",
        "\\parallel" to "∥",
        "\\degree" to "°",
        "\\cdots" to "⋯",
        "\\ldots" to "…",
        "\\dots" to "…",
        "\\vdots" to "⋮",
        "\\therefore" to "∴",
        "\\because" to "∵",
        "\\quad" to "  ",
        "\\qquad" to "    ",
        "\\," to " ",
        "\\;" to " ",
        "\\:" to " ",
        "\\!" to ""
    )

    private val SUPERSCRIPT_MAP = mapOf(
        '0' to '⁰', '1' to '¹', '2' to '²', '3' to '³', '4' to '⁴',
        '5' to '⁵', '6' to '⁶', '7' to '⁷', '8' to '⁸', '9' to '⁹',
        '+' to '⁺', '-' to '⁻', '=' to '⁼', '(' to '⁽', ')' to '⁾',
        'n' to 'ⁿ', 'i' to 'ⁱ'
    )

    private val SUBSCRIPT_MAP = mapOf(
        '0' to '₀', '1' to '₁', '2' to '₂', '3' to '₃', '4' to '₄',
        '5' to '₅', '6' to '₆', '7' to '₇', '8' to '₈', '9' to '₉',
        '+' to '₊', '-' to '₋', '=' to '₌', '(' to '₍', ')' to '₎',
        'a' to 'ₐ', 'e' to 'ₑ', 'i' to 'ᵢ', 'j' to 'ⱼ', 'k' to 'ₖ',
        'n' to 'ₙ', 'o' to 'ₒ', 'p' to 'ₚ', 'r' to 'ᵣ', 's' to 'ₛ',
        't' to 'ₜ', 'u' to 'ᵤ', 'v' to 'ᵥ', 'x' to 'ₓ'
    )

    fun parseHtmlAttributes(tagInner: String): Map<String, String> {
        val attrs = linkedMapOf<String, String>()
        for (match in HTML_ATTR_REGEX.findAll(tagInner)) {
            val key = match.groupValues[1].lowercase(Locale.US)
            val value = when {
                match.groups[2] != null -> match.groupValues[2]
                match.groups[3] != null -> match.groupValues[3]
                match.groups[4] != null -> match.groupValues[4]
                else -> ""
            }
            attrs[key] = decodeHtmlEntities(value.trim())
        }
        return attrs
    }

    fun parseInlineCss(styleAttr: String?): Map<String, String> {
        if (styleAttr.isNullOrBlank()) return emptyMap()
        val result = linkedMapOf<String, String>()
        val parts = splitTopLevel(styleAttr, ';')
        for (part in parts) {
            val colonIdx = part.indexOf(':')
            if (colonIdx <= 0) continue
            val prop = part.substring(0, colonIdx).trim().lowercase(Locale.US)
            val value = part.substring(colonIdx + 1).trim()
            if (prop.isNotEmpty() && value.isNotEmpty()) {
                result[prop] = value
            }
        }
        return result
    }

    private fun splitTopLevel(text: String, delimiter: Char): List<String> {
        val result = mutableListOf<String>()
        val current = StringBuilder()
        var parenDepth = 0
        var quote: Char? = null
        for (ch in text) {
            if (quote != null) {
                current.append(ch)
                if (ch == quote) quote = null
                continue
            }
            when (ch) {
                '\'', '"' -> {
                    quote = ch
                    current.append(ch)
                }
                '(' -> {
                    parenDepth += 1
                    current.append(ch)
                }
                ')' -> {
                    if (parenDepth > 0) parenDepth -= 1
                    current.append(ch)
                }
                else -> if (ch == delimiter && parenDepth == 0) {
                    result.add(current.toString())
                    current.clear()
                } else {
                    current.append(ch)
                }
            }
        }
        if (current.isNotEmpty()) {
            result.add(current.toString())
        }
        return result
    }

    fun parseCssColor(raw: String?): Int? {
        val value = raw?.trim()?.lowercase(Locale.US)?.takeIf { it.isNotEmpty() } ?: return null
        if (value == "transparent" || value == "inherit" || value == "initial" || value == "unset") {
            return null
        }
        NAMED_COLORS[value]?.let { return it }

        if (value.startsWith("#")) {
            val hex = value.substring(1)
            return when (hex.length) {
                3 -> {
                    val r = hex[0].digitToIntOrNull(16) ?: return null
                    val g = hex[1].digitToIntOrNull(16) ?: return null
                    val b = hex[2].digitToIntOrNull(16) ?: return null
                    (0xFF shl 24) or ((r * 17) shl 16) or ((g * 17) shl 8) or (b * 17)
                }
                4 -> {
                    val r = hex[0].digitToIntOrNull(16) ?: return null
                    val g = hex[1].digitToIntOrNull(16) ?: return null
                    val b = hex[2].digitToIntOrNull(16) ?: return null
                    val a = hex[3].digitToIntOrNull(16) ?: return null
                    ((a * 17) shl 24) or ((r * 17) shl 16) or ((g * 17) shl 8) or (b * 17)
                }
                6 -> {
                    val rgb = hex.toLongOrNull(16) ?: return null
                    (0xFF000000L or rgb).toInt()
                }
                8 -> {
                    // CSS #RRGGBBAA -> Android ARGB
                    val rgba = hex.toLongOrNull(16) ?: return null
                    val rr = ((rgba ushr 24) and 0xFF).toInt()
                    val gg = ((rgba ushr 16) and 0xFF).toInt()
                    val bb = ((rgba ushr 8) and 0xFF).toInt()
                    val aa = (rgba and 0xFF).toInt()
                    (aa shl 24) or (rr shl 16) or (gg shl 8) or bb
                }
                else -> null
            }
        }

        val rgbMatch = RGB_REGEX.matchEntire(value)
        if (rgbMatch != null) {
            val r = rgbMatch.groupValues[1].toIntOrNull()?.coerceIn(0, 255) ?: return null
            val g = rgbMatch.groupValues[2].toIntOrNull()?.coerceIn(0, 255) ?: return null
            val b = rgbMatch.groupValues[3].toIntOrNull()?.coerceIn(0, 255) ?: return null
            val alphaRaw = rgbMatch.groupValues[4]
            val a = if (alphaRaw.isBlank()) {
                255
            } else if (alphaRaw.endsWith("%")) {
                val pct = alphaRaw.dropLast(1).toFloatOrNull() ?: return null
                (pct.coerceIn(0f, 100f) * 2.55f).roundToInt()
            } else {
                val f = alphaRaw.toFloatOrNull() ?: return null
                (f.coerceIn(0f, 1f) * 255f).roundToInt()
            }
            if (a == 0) return null
            return (a shl 24) or (r shl 16) or (g shl 8) or b
        }

        return null
    }

    /**
     * Parses CSS `font-size` or HTML `<font size="...">`.
     * Returns Pair(absolutePtOrNull, relativeScaleOrNull).
     */
    fun parseFontSize(raw: String?, isLegacyFontTag: Boolean = false): Pair<Float?, Float?> {
        val value = raw?.trim()?.lowercase(Locale.US)?.takeIf { it.isNotEmpty() } ?: return null to null

        if (isLegacyFontTag) {
            when (value) {
                "1" -> return null to 0.65f
                "2" -> return null to 0.82f
                "3" -> return null to 1.0f
                "4" -> return null to 1.18f
                "5" -> return null to 1.45f
                "6" -> return null to 1.85f
                "7" -> return null to 2.35f
                "+1" -> return null to 1.18f
                "+2" -> return null to 1.45f
                "+3" -> return null to 1.85f
                "-1" -> return null to 0.82f
                "-2" -> return null to 0.65f
            }
        }

        return when {
            value == "xx-small" -> null to 0.60f
            value == "x-small" -> null to 0.72f
            value == "small" || value == "smaller" -> null to 0.85f
            value == "medium" -> null to 1.0f
            value == "large" || value == "larger" -> null to 1.22f
            value == "x-large" -> null to 1.48f
            value == "xx-large" -> null to 1.85f
            value.endsWith("px") -> {
                val px = value.dropLast(2).trim().toFloatOrNull()
                if (px != null && px > 0f) (px * 0.75f).coerceIn(5f, 72f) to null else null to null
            }
            value.endsWith("pt") -> {
                val pt = value.dropLast(2).trim().toFloatOrNull()
                if (pt != null && pt > 0f) pt.coerceIn(5f, 72f) to null else null to null
            }
            value.endsWith("rem") -> {
                val rem = value.dropLast(3).trim().toFloatOrNull()
                if (rem != null && rem > 0f) null to rem.coerceIn(0.4f, 4.0f) else null to null
            }
            value.endsWith("em") -> {
                val em = value.dropLast(2).trim().toFloatOrNull()
                if (em != null && em > 0f) null to em.coerceIn(0.4f, 4.0f) else null to null
            }
            value.endsWith("%") -> {
                val pct = value.dropLast(1).trim().toFloatOrNull()
                if (pct != null && pct > 0f) null to (pct / 100f).coerceIn(0.4f, 4.0f) else null to null
            }
            else -> {
                val num = value.toFloatOrNull()
                if (num != null && num > 0f) (num * 0.75f).coerceIn(5f, 72f) to null else null to null
            }
        }
    }

    fun parseFontWeight(raw: String?): Int? {
        val value = raw?.trim()?.lowercase(Locale.US)?.takeIf { it.isNotEmpty() } ?: return null
        return when (value) {
            "normal", "regular" -> 400
            "medium" -> 500
            "semibold", "semi-bold", "demibold" -> 600
            "bold", "bolder" -> 700
            "extrabold", "extra-bold" -> 800
            "black", "heavy" -> 900
            "light", "lighter" -> 300
            "thin" -> 100
            else -> value.toIntOrNull()?.coerceIn(100, 900)
        }
    }

    fun parseFontFamily(raw: String?): MdFontFamily? {
        val value = raw?.trim()?.lowercase(Locale.US)?.takeIf { it.isNotEmpty() } ?: return null
        return when {
            value.contains("mono") ||
                value.contains("courier") ||
                value.contains("consolas") ||
                value.contains("menlo") ||
                value.contains("monaco") ||
                value.contains("fira code") ||
                value.contains("jetbrains") -> MdFontFamily.Monospace
            value.contains("sans") ||
                value.contains("arial") ||
                value.contains("helvetica") ||
                value.contains("roboto") ||
                value.contains("pingfang") ||
                value.contains("yahei") ||
                value.contains("heiti") -> MdFontFamily.Sans
            value.contains("serif") ||
                value.contains("georgia") ||
                value.contains("times") ||
                value.contains("garamond") ||
                value.contains("simsun") ||
                value.contains("stsong") ||
                value.contains("songti") ||
                value.contains("kaiti") -> MdFontFamily.Serif
            else -> null
        }
    }

    fun parseAlignment(raw: String?): MdAlignment? {
        return when (raw?.trim()?.lowercase(Locale.US)) {
            "center", "middle" -> MdAlignment.Center
            "right", "end" -> MdAlignment.Right
            "left", "start" -> MdAlignment.Left
            else -> null
        }
    }

    fun parseDimensionHint(raw: String?): MdDimensionHint? {
        val value = raw?.trim()?.lowercase(Locale.US)?.takeIf { it.isNotEmpty() } ?: return null
        return when {
            value.endsWith("%") -> {
                val pct = value.dropLast(1).trim().toFloatOrNull() ?: return null
                if (pct > 0f) MdDimensionHint(pct.coerceIn(5f, 100f), isPercent = true) else null
            }
            value.endsWith("px") -> {
                val px = value.dropLast(2).trim().toFloatOrNull() ?: return null
                if (px > 0f) MdDimensionHint((px * 0.75f).coerceIn(16f, 2000f), isPercent = false) else null
            }
            value.endsWith("pt") -> {
                val pt = value.dropLast(2).trim().toFloatOrNull() ?: return null
                if (pt > 0f) MdDimensionHint(pt.coerceIn(16f, 2000f), isPercent = false) else null
            }
            value.endsWith("cm") -> {
                val cm = value.dropLast(2).trim().toFloatOrNull() ?: return null
                if (cm > 0f) MdDimensionHint((cm * 28.3465f).coerceIn(16f, 2000f), isPercent = false) else null
            }
            value.endsWith("mm") -> {
                val mm = value.dropLast(2).trim().toFloatOrNull() ?: return null
                if (mm > 0f) MdDimensionHint((mm * 2.83465f).coerceIn(16f, 2000f), isPercent = false) else null
            }
            else -> {
                val px = value.toFloatOrNull() ?: return null
                if (px > 0f) MdDimensionHint((px * 0.75f).coerceIn(16f, 2000f), isPercent = false) else null
            }
        }
    }

    fun applyHtmlTagStyle(
        current: MdInlineStyle,
        tagName: String,
        attrs: Map<String, String>
    ): MdInlineStyle {
        var next = current
        when (tagName.lowercase(Locale.US)) {
            "b", "strong" -> next = next.copy(bold = true, fontWeight = maxOf(next.fontWeight ?: 700, 700))
            "i", "em", "cite", "var" -> next = next.copy(italic = true)
            "u", "ins" -> next = next.copy(underline = true)
            "s", "del", "strike" -> next = next.copy(strikethrough = true)
            "small" -> next = next.copy(fontSizeScale = (next.fontSizeScale * 0.85f).coerceIn(0.4f, 4.0f))
            "big" -> next = next.copy(fontSizeScale = (next.fontSizeScale * 1.22f).coerceIn(0.4f, 4.0f))
            "sub" -> next = next.copy(subscript = true, superscript = false, fontSizeScale = (next.fontSizeScale * 0.78f).coerceIn(0.4f, 4.0f))
            "sup" -> next = next.copy(superscript = true, subscript = false, fontSizeScale = (next.fontSizeScale * 0.78f).coerceIn(0.4f, 4.0f))
            "mark" -> next = next.copy(mark = true)
            "kbd" -> next = next.copy(kbd = true, fontFamily = MdFontFamily.Monospace)
            "code", "tt" -> next = next.copy(code = true, fontFamily = MdFontFamily.Monospace)
            "a" -> {
                val href = attrs["href"]?.takeIf { it.isNotBlank() && !it.startsWith("#") }
                if (href != null) {
                    next = next.copy(linkUrl = href, underline = true)
                }
            }
            "font" -> {
                attrs["color"]?.let { c ->
                    parseCssColor(c)?.let { next = next.copy(textColor = it) }
                }
                attrs["size"]?.let { s ->
                    val (absPt, scale) = parseFontSize(s, isLegacyFontTag = true)
                    if (absPt != null) {
                        next = next.copy(fontSizePt = absPt, fontSizeScale = 1.0f)
                    } else if (scale != null) {
                        next = next.copy(fontSizePt = null, fontSizeScale = scale)
                    }
                }
                attrs["face"]?.let { f ->
                    parseFontFamily(f)?.let { next = next.copy(fontFamily = it) }
                }
            }
        }

        val css = parseInlineCss(attrs["style"])
        if (css.isNotEmpty()) {
            next = applyCssProperties(next, css)
        }
        return next
    }

    fun applyCssProperties(current: MdInlineStyle, css: Map<String, String>): MdInlineStyle {
        var next = current
        css["color"]?.let { c ->
            parseCssColor(c)?.let { next = next.copy(textColor = it) }
        }
        (css["background-color"] ?: css["background"])?.let { bg ->
            parseCssColor(bg)?.let { next = next.copy(backgroundColor = it) }
        }
        css["font-size"]?.let { fs ->
            val (absPt, scale) = parseFontSize(fs, isLegacyFontTag = false)
            if (absPt != null) {
                next = next.copy(fontSizePt = absPt, fontSizeScale = 1.0f)
            } else if (scale != null) {
                next = next.copy(fontSizeScale = (next.fontSizeScale * scale).coerceIn(0.4f, 4.0f))
            }
        }
        css["font-weight"]?.let { fw ->
            parseFontWeight(fw)?.let { weight ->
                next = next.copy(
                    bold = weight >= 600,
                    fontWeight = weight
                )
            }
        }
        css["font-style"]?.lowercase(Locale.US)?.let { fs ->
            if (fs.contains("italic") || fs.contains("oblique")) {
                next = next.copy(italic = true)
            } else if (fs == "normal") {
                next = next.copy(italic = false)
            }
        }
        css["font-family"]?.let { ff ->
            parseFontFamily(ff)?.let { next = next.copy(fontFamily = it) }
        }
        css["text-decoration"]?.lowercase(Locale.US)?.let { td ->
            if (td.contains("underline")) next = next.copy(underline = true)
            if (td.contains("line-through")) next = next.copy(strikethrough = true)
            if (td == "none") next = next.copy(underline = false, strikethrough = false)
        }
        css["text-decoration-line"]?.lowercase(Locale.US)?.let { td ->
            if (td.contains("underline")) next = next.copy(underline = true)
            if (td.contains("line-through")) next = next.copy(strikethrough = true)
        }
        return next
    }

    fun isLikelyVideoSource(source: String): Boolean {
        val trimmed = source.trim()
        if (trimmed.isEmpty() || trimmed.startsWith("data:", ignoreCase = true)) return false
        val withoutFragment = trimmed.substringBefore('#')
        val withoutQuery = withoutFragment.substringBefore('?')
        val ext = withoutQuery.substringAfterLast('.', "").lowercase(Locale.US)
        return ext in VIDEO_EXTENSIONS
    }

    fun decodeHtmlEntities(text: String): String {
        if (!text.contains('&')) return text
        val result = StringBuilder(text.length)
        var i = 0
        while (i < text.length) {
            if (text[i] == '&') {
                val semi = text.indexOf(';', i + 1)
                if (semi in (i + 2)..(i + 12)) {
                    val entity = text.substring(i + 1, semi)
                    val decoded = decodeSingleEntity(entity)
                    if (decoded != null) {
                        result.append(decoded)
                        i = semi + 1
                        continue
                    }
                }
            }
            result.append(text[i])
            i += 1
        }
        return result.toString()
    }

    private fun decodeSingleEntity(entity: String): String? {
        NAMED_ENTITIES[entity]?.let { return it }
        NAMED_ENTITIES[entity.lowercase(Locale.US)]?.let { return it }
        if (entity.startsWith("#x", ignoreCase = true)) {
            val codePoint = entity.substring(2).toIntOrNull(16) ?: return null
            if (Character.isValidCodePoint(codePoint)) {
                return String(Character.toChars(codePoint))
            }
        } else if (entity.startsWith("#")) {
            val codePoint = entity.substring(1).toIntOrNull() ?: return null
            if (Character.isValidCodePoint(codePoint)) {
                return String(Character.toChars(codePoint))
            }
        }
        return null
    }

    fun formatLatexMath(raw: String): String {
        var out = raw.trim()
        // Handle \frac{a}{b} and \dfrac{a}{b}
        out = replaceTwoArgCommand(out, "\\frac") { num, den -> "($num)/($den)" }
        out = replaceTwoArgCommand(out, "\\dfrac") { num, den -> "($num)/($den)" }
        out = replaceTwoArgCommand(out, "\\tfrac") { num, den -> "($num)/($den)" }
        // Handle \sqrt{x}
        out = replaceOneArgCommand(out, "\\sqrt") { inner -> "√($inner)" }
        // Handle \text{...}, \mathrm{...}, \mathbf{...}, \mathit{...}
        for (cmd in listOf("\\text", "\\mathrm", "\\mathbf", "\\mathit", "\\mathsf", "\\operatorname")) {
            out = replaceOneArgCommand(out, cmd) { inner -> inner }
        }
        // Replace known symbol commands (longer keys first)
        for ((cmd, symbol) in LATEX_COMMANDS.entries.sortedByDescending { it.key.length }) {
            out = out.replace(cmd, symbol)
        }
        // Superscripts ^{...} or ^x
        out = replaceScripts(out, '^', SUPERSCRIPT_MAP)
        // Subscripts _{...} or _x
        out = replaceScripts(out, '_', SUBSCRIPT_MAP)
        // Strip redundant braces left around simple tokens
        out = out.replace("\\left", "").replace("\\right", "")
            .replace("\\{", "{").replace("\\}", "}")
            .replace(Regex("""\s+"""), " ")
            .trim()
        return out
    }

    private fun replaceOneArgCommand(
        input: String,
        command: String,
        transform: (String) -> String
    ): String {
        var text = input
        var safety = 0
        while (safety++ < 32) {
            val idx = text.indexOf(command)
            if (idx < 0) break
            val afterCmd = idx + command.length
            if (afterCmd < text.length && text[afterCmd].isLetter()) break
            var pos = afterCmd
            while (pos < text.length && text[pos].isWhitespace()) pos++
            if (pos >= text.length || text[pos] != '{') break
            val close = findMatchingBrace(text, pos) ?: break
            val inner = text.substring(pos + 1, close)
            text = text.substring(0, idx) + transform(inner) + text.substring(close + 1)
        }
        return text
    }

    private fun replaceTwoArgCommand(
        input: String,
        command: String,
        transform: (String, String) -> String
    ): String {
        var text = input
        var safety = 0
        while (safety++ < 32) {
            val idx = text.indexOf(command)
            if (idx < 0) break
            val afterCmd = idx + command.length
            if (afterCmd < text.length && text[afterCmd].isLetter()) break
            var p1 = afterCmd
            while (p1 < text.length && text[p1].isWhitespace()) p1++
            if (p1 >= text.length || text[p1] != '{') break
            val c1 = findMatchingBrace(text, p1) ?: break
            var p2 = c1 + 1
            while (p2 < text.length && text[p2].isWhitespace()) p2++
            if (p2 >= text.length || text[p2] != '{') break
            val c2 = findMatchingBrace(text, p2) ?: break
            val arg1 = text.substring(p1 + 1, c1)
            val arg2 = text.substring(p2 + 1, c2)
            text = text.substring(0, idx) + transform(arg1, arg2) + text.substring(c2 + 1)
        }
        return text
    }

    private fun findMatchingBrace(text: String, openIndex: Int): Int? {
        var depth = 0
        var i = openIndex
        while (i < text.length) {
            val ch = text[i]
            if (ch == '\\') {
                i += 2
                continue
            }
            if (ch == '{') depth++
            else if (ch == '}') {
                depth--
                if (depth == 0) return i
            }
            i++
        }
        return null
    }

    private fun replaceScripts(
        input: String,
        marker: Char,
        map: Map<Char, Char>
    ): String {
        val out = StringBuilder(input.length)
        var i = 0
        while (i < input.length) {
            if (input[i] == marker && i + 1 < input.length) {
                if (input[i + 1] == '{') {
                    val close = findMatchingBrace(input, i + 1)
                    if (close != null) {
                        val content = input.substring(i + 2, close)
                        if (content.isNotEmpty() && content.all { it in map }) {
                            content.forEach { out.append(map[it]) }
                        } else {
                            out.append(marker).append('(').append(content).append(')')
                        }
                        i = close + 1
                        continue
                    }
                } else {
                    val nextChar = input[i + 1]
                    val mapped = map[nextChar]
                    if (mapped != null) {
                        out.append(mapped)
                        i += 2
                        continue
                    }
                }
            }
            out.append(input[i])
            i++
        }
        return out.toString()
    }
}
