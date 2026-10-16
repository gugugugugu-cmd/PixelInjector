package eu.hxreborn.pixelinjector.support

object Redactor {
    const val MARK = "‹redacted›"

    private const val OCTET = """(?:25[0-5]|2[0-4]\d|1\d\d|[1-9]?\d)"""

    private val rules =
        listOf(
            Regex("""[A-Za-z0-9._%+-]+@[A-Za-z0-9-]+(?:\.[A-Za-z0-9-]+)*\.[A-Za-z]{2,}""") to MARK,
            Regex("""\b(?:[0-9a-fA-F]{2}:){5}[0-9a-fA-F]{2}\b""") to MARK,
            Regex("""\b(?:$OCTET\.){3}$OCTET\b""") to MARK,
            Regex(
                """\b[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}\b""",
            ) to
                MARK,
            Regex("""\+\d[\d -]{6,}\d""") to MARK,
            Regex("""(?<![\w.])(?:\+\d{1,3}[ -]?)?\(?\d{3}\)?[ -]\d{3}[ -]\d{4}\b""") to MARK,
            Regex("""\b(?:[0-9a-fA-F]{1,4}:){7}[0-9a-fA-F]{1,4}\b""") to MARK,
            Regex(
                """\b[0-9a-fA-F]{1,4}(?::[0-9a-fA-F]{1,4})*::(?:[0-9a-fA-F]{1,4}(?::[0-9a-fA-F]{1,4})*)?(?:%\w+)?""",
            ) to
                MARK,
            Regex("""(?i)(\bb?ssid["']?[=:\s]+)(?:"[^"\n]*"|'[^'\n]*'|[^"',\s]+)""") to "$1$MARK",
            Regex(
                """(?i)\b(serial(?:no)?|android_id|imei|imsi|iccid|meid)([=:\s"']+)[A-Za-z0-9]{6,}""",
            ) to
                "$1$2$MARK",
            Regex(
                """(/storage/(?:emulated/\d+|[0-9A-Fa-f]{4}-[0-9A-Fa-f]{4})/|""" +
                    """/mnt/media_rw/[0-9A-Fa-f]{4}-[0-9A-Fa-f]{4}/|/sdcard/|""" +
                    """/data/user/\d+/|/data/media/\d+/)(?:(?!\s\w+=)[^\n"'])*""",
            ) to
                "$1$MARK",
            Regex("""(?i)([?&](?:token|access_token|key|sig|auth)=)[^&\s]+""") to "$1$MARK",
            Regex("""(?i)(bearer\s+)[A-Za-z0-9._~+/-]+=*""") to "$1$MARK",
        )

    class Redacted(
        val text: String,
        val hidden: Int,
    )

    fun redact(text: String): Redacted {
        var hidden = 0
        val out =
            rules.fold(text) { acc, (regex, replacement) ->
                regex.replace(acc) { match ->
                    hidden++
                    replacement
                        .replace(
                            "$1",
                            match.groupValues.getOrElse(1) {
                                ""
                            },
                        ).replace("$2", match.groupValues.getOrElse(2) { "" })
                }
            }
        return Redacted(out, hidden)
    }
}
