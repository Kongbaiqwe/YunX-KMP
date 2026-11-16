package com.yunx.feature.download.util

/**
 * 下载文件名的归一化（纯函数，可单测）。
 */
object DownloadFileNames {

    /**
     * 末尾的冲突序号：`x.apk (1)` / `x.apk（1）`（全角括号、全角空格）。
     * 只认 1~3 位数字。
     */
    private val CONFLICT_SUFFIX_REGEX = Regex("""^(.+?)[\s\u3000]*[（(](\d{1,3})[)）]$""")

    /** 两层扩展名且最外层是 `.txt`：`x.md.txt` → stem=`x`、inner=`md` */
    private val TXT_OVER_TEXT_REGEX =
        Regex("""^(.+)\.([A-Za-z][A-Za-z0-9]{0,7})\.txt$""", RegexOption.IGNORE_CASE)

    private val TEXT_EXTENSIONS = setOf(
        "md", "markdown", "txt", "text", "json", "jsonl", "log", "ini", "cfg", "conf", "properties",
        "csv", "tsv", "yml", "yaml", "xml", "html", "htm", "css", "scss", "less",
        "js", "mjs", "cjs", "ts", "jsx", "tsx", "vue",
        "kt", "kts", "java", "py", "rb", "php", "go", "rs", "c", "h", "cc", "cpp", "hpp", "cs",
        "sh", "bash", "zsh", "bat", "ps1", "sql", "toml", "gradle", "pro", "gitignore", "lock"
    )

    /**
     * 归一化下载文件名。
     */
    fun normalize(raw: String): String {
        val name = raw.trim()
        if (name.isEmpty()) return raw
        val stripped = stripRedundantTxt(name)
        return restoreConflictSuffix(stripped).ifEmpty { name }
    }

    private fun stripRedundantTxt(name: String): String {
        val match = TXT_OVER_TEXT_REGEX.matchEntire(name) ?: return name
        val stem = match.groupValues[1]
        val inner = match.groupValues[2]
        if (inner.lowercase() !in TEXT_EXTENSIONS) return name
        return "$stem.$inner"
    }

    private fun restoreConflictSuffix(name: String): String {
        val match = CONFLICT_SUFFIX_REGEX.matchEntire(name) ?: return name
        val base = match.groupValues[1].trimEnd()
        val index = match.groupValues[2]
        val dot = base.lastIndexOf('.')
        if (dot <= 0 || dot == base.length - 1) return name
        val stem = base.substring(0, dot)
        val ext = base.substring(dot + 1)
        if (stem.isBlank() || ext.any { it.isWhitespace() || it == '(' || it == '（' }) return name
        return "$stem ($index).$ext"
    }
}
