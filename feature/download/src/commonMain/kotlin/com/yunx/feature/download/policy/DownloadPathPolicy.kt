package com.yunx.feature.download.policy

/**
 * 下载路径与文件名安全清洗策略
 */
object DownloadPathPolicy {

    data class SafePath(val fileName: String, val relativeDirectory: String)

    fun sanitize(relativePath: String, fallbackName: String): SafePath? {
        val normalized = relativePath.replace('\\', '/')
        if (normalized.startsWith('/')) return null

        val rawParts = normalized.split('/')
        if (rawParts.any { it == "." || it == ".." }) return null

        val parts = rawParts.filter { it.isNotBlank() }
        val nameMissing = normalized.endsWith('/')
        val rawName = if (nameMissing) "" else parts.lastOrNull().orEmpty()
        val safeName = sanitizeName(rawName).ifBlank { fallbackName }
        val dirParts = if (nameMissing) parts else parts.dropLast(1)
        val safeDirectory = dirParts.joinToString("/") { sanitizeName(it) }
        return SafePath(safeName, safeDirectory)
    }

    fun isContainedPath(basePath: String, candidatePath: String): Boolean {
        val baseNorm = basePath.replace('\\', '/').trimEnd('/')
        val candNorm = candidatePath.replace('\\', '/').trimEnd('/')
        if (candNorm == baseNorm) return false
        return candNorm.startsWith("$baseNorm/")
    }

    fun sanitizeName(name: String): String {
        var cleaned = name
            .replace(Regex("""[\\/:*?"<>|\x00-\x1f]"""), "_")
            .trim()
        if (cleaned.length > 120) {
            val ext = cleaned.substringAfterLast('.', "").take(10)
            val base = cleaned.substringBeforeLast('.').take(100)
            cleaned = if (ext.isNotBlank() && ext != cleaned) "$base.$ext" else cleaned.take(120)
        }
        return cleaned
    }
}
