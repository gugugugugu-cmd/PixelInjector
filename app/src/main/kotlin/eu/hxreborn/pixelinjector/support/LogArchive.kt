package eu.hxreborn.pixelinjector.support

import android.util.Log
import eu.hxreborn.pixelinjector.ModuleConstants
import java.io.File

class LogArchive(
    root: File,
) {
    private val file = File(root, "module-log.txt")

    @Synchronized
    fun merge(fresh: List<String>): List<String> {
        val kept = runCatching { file.readLines() }.getOrDefault(emptyList())
        val seen = kept.toHashSet()
        val added = fresh.filter { seen.add(it) }
        if (added.isEmpty()) return kept
        val merged = (kept + added).takeLast(MAX_LINES)
        val tmp = File(file.parentFile, "${file.name}.tmp")
        val written =
            runCatching {
                tmp.writeText(merged.joinToString("\n", postfix = "\n"))
                tmp.renameTo(file)
            }.getOrDefault(false)
        if (!written) Log.w(ModuleConstants.LOG_TAG, "log archive write failed path=${file.path}")
        return merged
    }

    companion object {
        private const val MAX_LINES = 3000
    }
}
