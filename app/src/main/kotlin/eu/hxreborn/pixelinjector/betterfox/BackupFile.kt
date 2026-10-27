package eu.hxreborn.pixelinjector.betterfox

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale

const val BACKUP_MAGIC = "// pixelinjector-betterfox 1"
const val BACKUP_SUFFIX = ".user.js"
const val BACKUP_PREFIX = "betterfox-"
const val BACKUP_MAX_BYTES = 64 * 1024

private const val OVERRIDES_HEADER = "/** OVERRIDES ***/"
private const val STOCK_MARK = "// stock"
private const val NONE = "none"
private val createdFormat = DateTimeFormatter.ofPattern("yyyyMMdd HH:mm", Locale.ROOT)
private val fileStamp = DateTimeFormatter.ofPattern("yyyyMMdd-HHmm", Locale.ROOT)
private val HEADER = Regex("^// (created|app|list|browser|groups-off) (.*)$")
private val LIST_HEADER = Regex("^Betterfox (\\S+) revision (\\d+)$")
private val BROWSER_HEADER = Regex("^(\\S+) (\\S+) Gecko (\\S+)$")
private val USER_PREF = Regex("^(// )?user_pref\\(\"([^\"]+)\",\\s*(.+?)\\);\\s*(// stock)?\\s*$")

class BetterfoxBackup(
    val created: LocalDateTime,
    val app: String,
    val betterfox: String,
    val revision: Int,
    val browser: String,
    val browserVersion: String,
    val gecko: String,
    val offGroups: Set<String>,
    val overrides: Map<String, BetterfoxOverride>,
) {
    fun encode(list: BetterfoxList): String =
        buildString {
            appendLine(BACKUP_MAGIC)
            appendLine("// created ${created.format(createdFormat)}")
            appendLine("// app $app")
            appendLine("// list Betterfox $betterfox revision $revision")
            appendLine("// browser $browser $browserVersion Gecko $gecko")
            appendLine("// groups-off ${offGroups.sorted().joinToString(" ").ifEmpty { NONE }}")
            appendLine("//")
            appendLine("// Edit only the OVERRIDES section at the end and import the file back.")
            appendLine(
                "// A line there wins over the list. Set a pref to its stock value to turn it off.",
            )
            appendLine("// This file also works as a plain user.js in any Firefox profile.")
            for (group in list.groups.filter { it !in offGroups }) {
                appendLine()
                appendLine("/** ${group.uppercase(Locale.ROOT)} ***/")
                for (entry in list.prefs.filter { it.group == group }) {
                    val value = formatPrefValue(entry.value)
                    val stock = formatPrefValue(entry.stock)
                    appendLine("user_pref(\"${entry.pref}\", $value); // stock $stock")
                }
            }
            appendLine()
            appendLine(OVERRIDES_HEADER)
            for ((pref, override) in overrides.toSortedMap()) {
                when (override) {
                    is BetterfoxOverride.Custom -> {
                        appendLine("user_pref(\"$pref\", ${formatPrefValue(override.value)});")
                    }

                    BetterfoxOverride.Stock -> {
                        val stock = list.pref(pref)?.stock
                        val comment = if (stock == null) "// " else ""
                        val value = formatPrefValue(stock)
                        appendLine("${comment}user_pref(\"$pref\", $value); $STOCK_MARK")
                    }
                }
            }
        }

    companion object {
        fun fileName(
            browser: String,
            at: LocalDateTime,
        ): String =
            "$BACKUP_PREFIX${browser.substringAfterLast('.')}-${at.format(fileStamp)}$BACKUP_SUFFIX"

        fun parse(text: String): BetterfoxBackup {
            require(text.length <= BACKUP_MAX_BYTES) { "backup size" }
            val lines = text.lines()
            require(lines.firstOrNull()?.trim() == BACKUP_MAGIC) { "not a PixelInjector backup" }
            val header = HashMap<String, String>()
            for (line in lines.drop(1)) {
                val match = HEADER.find(line.trim()) ?: break
                header.putIfAbsent(match.groupValues[1], match.groupValues[2].trim())
            }
            val list =
                LIST_HEADER.find(header["list"].orEmpty())
                    ?: throw IllegalArgumentException("list header")
            val browser =
                BROWSER_HEADER.find(header["browser"].orEmpty())
                    ?: throw IllegalArgumentException("browser header")
            val created =
                try {
                    LocalDateTime.parse(header["created"].orEmpty(), createdFormat)
                } catch (e: DateTimeParseException) {
                    throw IllegalArgumentException("created header", e)
                }
            val offGroups =
                header["groups-off"]
                    .orEmpty()
                    .split(' ')
                    .filter { it.isNotEmpty() && it != NONE }
                    .toSet()
            val start = lines.indexOfFirst { it.trim() == OVERRIDES_HEADER }
            require(start >= 0) { "no overrides section" }
            val overrides = LinkedHashMap<String, BetterfoxOverride>()
            for (line in lines.drop(start + 1)) {
                val match = USER_PREF.find(line.trim()) ?: continue
                val (_, commented, pref, raw, stockMark) = match.groupValues
                when {
                    stockMark.isNotEmpty() -> {
                        overrides[pref] = BetterfoxOverride.Stock
                    }

                    commented.isEmpty() -> {
                        val value = requireNotNull(parsePrefValue(raw)) { "value of $pref" }
                        overrides[pref] = BetterfoxOverride.Custom(value)
                    }
                }
            }
            return BetterfoxBackup(
                created = created,
                app = header["app"].orEmpty(),
                betterfox = list.groupValues[1],
                revision = list.groupValues[2].toInt(),
                browser = browser.groupValues[1],
                browserVersion = browser.groupValues[2],
                gecko = browser.groupValues[3],
                offGroups = offGroups,
                overrides = overrides,
            )
        }
    }
}
