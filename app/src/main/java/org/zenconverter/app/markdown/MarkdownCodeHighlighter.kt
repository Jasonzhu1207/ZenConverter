package org.zenconverter.app.markdown

import java.util.Locale

internal data class CodeToken(
    val text: String,
    val color: Int
)

/**
 * Lightweight, zero-dependency lexical syntax highlighter tuned for crisp light-paper PDF printing.
 * Guarantees exact character preservation across every input line.
 */
internal object MarkdownCodeHighlighter {
    const val COLOR_DEFAULT = 0xFF24292F.toInt()
    private const val COLOR_COMMENT = 0xFF6E7781.toInt()
    private const val COLOR_STRING = 0xFF0A3069.toInt()
    private const val COLOR_KEYWORD = 0xFFCF222E.toInt()
    private const val COLOR_NUMBER = 0xFF0550AE.toInt()
    private const val COLOR_ANNOTATION = 0xFF8250DF.toInt()
    private const val COLOR_TYPE_OR_TAG = 0xFF116329.toInt()
    private const val COLOR_KEY = 0xFF0550AE.toInt()

    private val C_LIKE_KEYWORDS = setOf(
        "fun", "val", "var", "class", "interface", "object", "enum", "sealed", "data",
        "override", "private", "public", "protected", "internal", "open", "abstract",
        "final", "static", "void", "return", "if", "else", "when", "switch", "case",
        "default", "for", "while", "do", "break", "continue", "try", "catch", "finally",
        "throw", "throws", "import", "package", "new", "this", "super", "null", "true",
        "false", "suspend", "inline", "companion", "lateinit", "by", "in", "out", "is", "as",
        "fn", "let", "mut", "pub", "impl", "struct", "trait", "mod", "use", "match",
        "async", "await", "const", "type", "func", "defer", "go", "chan", "select",
        "function", "export", "from", "extends", "implements", "typeof", "undefined",
        "int", "long", "float", "double", "boolean", "char", "byte", "short", "string"
    )

    private val PYTHON_KEYWORDS = setOf(
        "def", "class", "return", "if", "elif", "else", "for", "while", "break", "continue",
        "pass", "try", "except", "finally", "raise", "import", "from", "as", "with", "yield",
        "lambda", "async", "await", "and", "or", "not", "in", "is", "None", "True", "False",
        "global", "nonlocal", "assert", "del", "self"
    )

    private val SHELL_KEYWORDS = setOf(
        "if", "then", "else", "elif", "fi", "for", "in", "do", "done", "while", "until",
        "case", "esac", "function", "export", "local", "readonly", "return", "exit",
        "echo", "cd", "sudo", "set", "unset", "true", "false"
    )

    private val SQL_KEYWORDS = setOf(
        "select", "from", "where", "insert", "into", "values", "update", "set", "delete",
        "create", "table", "alter", "drop", "join", "inner", "left", "right", "outer",
        "on", "group", "by", "order", "asc", "desc", "limit", "offset", "having", "as",
        "and", "or", "not", "null", "is", "in", "distinct", "count", "sum", "avg", "union"
    )

    fun highlightLines(code: String, language: String?): List<List<CodeToken>> {
        val lang = language?.trim()?.lowercase(Locale.US).orEmpty()
        val lines = code.split('\n')
        if (lang.isEmpty() || lang in setOf("text", "txt", "plain", "plaintext")) {
            return lines.map { line -> listOf(CodeToken(line, COLOR_DEFAULT)) }
        }

        val isJson = lang == "json" || lang == "jsonc"
        val isYaml = lang == "yaml" || lang == "yml"
        val isXml = lang in setOf("html", "htm", "xml", "svg")
        val isCss = lang in setOf("css", "scss", "less")
        val isSql = lang == "sql"
        val isPython = lang in setOf("py", "python")
        val isShell = lang in setOf("sh", "bash", "zsh", "shell")

        val keywords = when {
            isPython -> PYTHON_KEYWORDS
            isShell -> SHELL_KEYWORDS
            isSql -> SQL_KEYWORDS
            isJson || isYaml || isXml || isCss -> emptySet()
            else -> C_LIKE_KEYWORDS
        }

        val supportsSlashComments = !isPython && !isShell && !isYaml && !isSql && !isXml
        val supportsHashComments = isPython || isShell || isYaml || lang == "toml"
        val supportsBlockComments = supportsSlashComments || isCss || isSql

        var inBlockComment = false
        return lines.map { line ->
            val tokens = mutableListOf<CodeToken>()
            var i = 0

            fun addToken(text: String, color: Int) {
                if (text.isEmpty()) return
                val last = tokens.lastOrNull()
                if (last != null && last.color == color) {
                    tokens[tokens.lastIndex] = last.copy(text = last.text + text)
                } else {
                    tokens.add(CodeToken(text, color))
                }
            }

            if (line.isEmpty()) {
                return@map listOf(CodeToken("", COLOR_DEFAULT))
            }

            while (i < line.length) {
                if (inBlockComment) {
                    val endIdx = line.indexOf("*/", i)
                    if (endIdx < 0) {
                        addToken(line.substring(i), COLOR_COMMENT)
                        i = line.length
                    } else {
                        addToken(line.substring(i, endIdx + 2), COLOR_COMMENT)
                        inBlockComment = false
                        i = endIdx + 2
                    }
                    continue
                }

                if (supportsBlockComments && line.startsWith("/*", i)) {
                    val endIdx = line.indexOf("*/", i + 2)
                    if (endIdx < 0) {
                        addToken(line.substring(i), COLOR_COMMENT)
                        inBlockComment = true
                        i = line.length
                    } else {
                        addToken(line.substring(i, endIdx + 2), COLOR_COMMENT)
                        i = endIdx + 2
                    }
                    continue
                }

                if ((supportsSlashComments && line.startsWith("//", i)) ||
                    (supportsHashComments && line[i] == '#') ||
                    (isSql && line.startsWith("--", i))
                ) {
                    addToken(line.substring(i), COLOR_COMMENT)
                    break
                }

                if (isXml && line.startsWith("<!--", i)) {
                    val endIdx = line.indexOf("-->", i + 4)
                    if (endIdx < 0) {
                        addToken(line.substring(i), COLOR_COMMENT)
                        break
                    } else {
                        addToken(line.substring(i, endIdx + 3), COLOR_COMMENT)
                        i = endIdx + 3
                        continue
                    }
                }

                // Strings
                val ch = line[i]
                if (ch == '"' || ch == '\'' || (ch == '`' && !isShell)) {
                    var j = i + 1
                    while (j < line.length) {
                        if (line[j] == '\\') {
                            j += 2
                            continue
                        }
                        if (line[j] == ch) {
                            j++
                            break
                        }
                        j++
                    }
                    val str = line.substring(i, j.coerceAtMost(line.length))
                    val afterWs = skipSpaces(line, j)
                    val color = if ((isJson || isYaml) && line.getOrNull(afterWs) == ':') {
                        COLOR_KEY
                    } else {
                        COLOR_STRING
                    }
                    addToken(str, color)
                    i = j.coerceAtMost(line.length)
                    continue
                }

                // Annotations (@Override, @Composable)
                if (ch == '@' && i + 1 < line.length && line[i + 1].isLetter()) {
                    var j = i + 1
                    while (j < line.length && (line[j].isLetterOrDigit() || line[j] == '_')) j++
                    addToken(line.substring(i, j), COLOR_ANNOTATION)
                    i = j
                    continue
                }

                // XML/HTML tag name
                if (isXml && ch == '<') {
                    var j = i + 1
                    if (j < line.length && line[j] == '/') j++
                    val nameStart = j
                    while (j < line.length && (line[j].isLetterOrDigit() || line[j] == '-' || line[j] == ':')) j++
                    if (j > nameStart) {
                        addToken(line.substring(i, nameStart), COLOR_DEFAULT)
                        addToken(line.substring(nameStart, j), COLOR_TYPE_OR_TAG)
                        i = j
                        continue
                    }
                }

                // YAML key at line start
                if (isYaml && i == skipSpaces(line, 0)) {
                    val colon = line.indexOf(':', i)
                    if (colon > i && !line.substring(i, colon).contains(' ')) {
                        addToken(line.substring(i, colon), COLOR_KEY)
                        addToken(":", COLOR_DEFAULT)
                        i = colon + 1
                        continue
                    }
                }

                // Numbers
                if (ch.isDigit() && (i == 0 || !line[i - 1].isLetterOrDigit())) {
                    var j = i + 1
                    while (j < line.length && (line[j].isLetterOrDigit() || line[j] == '.' || line[j] == '_')) j++
                    addToken(line.substring(i, j), COLOR_NUMBER)
                    i = j
                    continue
                }

                // Identifiers / Keywords
                if (ch.isLetter() || ch == '_') {
                    var j = i + 1
                    while (j < line.length && (line[j].isLetterOrDigit() || line[j] == '_')) j++
                    val word = line.substring(i, j)
                    val normalizedWord = if (isSql) word.lowercase(Locale.US) else word
                    val color = when {
                        normalizedWord in keywords -> COLOR_KEYWORD
                        word.first().isUpperCase() && word.any { it.isLowerCase() } -> COLOR_TYPE_OR_TAG
                        else -> COLOR_DEFAULT
                    }
                    addToken(word, color)
                    i = j
                    continue
                }

                addToken(ch.toString(), COLOR_DEFAULT)
                i++
            }
            tokens
        }
    }

    private fun skipSpaces(text: String, from: Int): Int {
        var idx = from
        while (idx < text.length && text[idx] == ' ') idx++
        return idx
    }
}
