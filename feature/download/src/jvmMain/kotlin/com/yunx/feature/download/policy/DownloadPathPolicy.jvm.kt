package com.yunx.feature.download.policy

import java.io.File

fun DownloadPathPolicy.isContained(base: File, candidate: File): Boolean {
    val basePath = base.canonicalFile.path.trimEnd(File.separatorChar)
    val candidatePath = candidate.canonicalFile.path
    return candidatePath != basePath && candidatePath.startsWith(basePath + File.separator)
}
