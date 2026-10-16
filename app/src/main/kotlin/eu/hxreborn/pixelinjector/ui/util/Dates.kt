package eu.hxreborn.pixelinjector.ui.util

import android.text.format.DateUtils
import androidx.compose.runtime.staticCompositionLocalOf
import eu.hxreborn.pixelinjector.R
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

private val dayFirst = DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.ROOT)
private val monthFirst = DateTimeFormatter.ofPattern("MM/dd/yyyy", Locale.ROOT)
private val dotted = DateTimeFormatter.ofPattern("dd.MM.yyyy", Locale.ROOT)
private const val ISO_DATE_LENGTH = 10

enum class DateStyle(
    val label: Int,
) {
    SYSTEM(R.string.date_style_system),
    LONG(R.string.date_style_long),
    FULL(R.string.date_style_full),
    NUMERIC(R.string.date_style_numeric),
    DAY_FIRST(R.string.date_style_day_first),
    MONTH_FIRST(R.string.date_style_month_first),
    DOTTED(R.string.date_style_dotted),
    ISO(R.string.date_style_iso),
    RELATIVE(R.string.date_style_relative),
    ;

    fun format(date: LocalDate): String =
        when (this) {
            SYSTEM -> date.format(localized(FormatStyle.MEDIUM))
            LONG -> date.format(localized(FormatStyle.LONG))
            FULL -> date.format(localized(FormatStyle.FULL))
            NUMERIC -> date.format(localized(FormatStyle.SHORT))
            DAY_FIRST -> date.format(dayFirst)
            MONTH_FIRST -> date.format(monthFirst)
            DOTTED -> date.format(dotted)
            ISO -> date.toString()
            RELATIVE -> relative(date)
        }

    fun format(epochMs: Long): String = format(Instant.ofEpochMilli(epochMs).atZone(ZoneId.systemDefault()).toLocalDate())

    fun formatDateTime(epochMs: Long): String =
        format(epochMs) + " " +
            Instant
                .ofEpochMilli(epochMs)
                .atZone(ZoneId.systemDefault())
                .toLocalTime()
                .format(localizedTime())

    fun formatIso(isoDate: String?): String? =
        isoDate?.let {
            runCatching {
                if ('T' in it) format(Instant.parse(it).toEpochMilli()) else format(LocalDate.parse(it.take(ISO_DATE_LENGTH)))
            }.getOrNull()
        }

    private fun localized(style: FormatStyle) = DateTimeFormatter.ofLocalizedDate(style).withLocale(Locale.getDefault())

    private fun localizedTime() = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(Locale.getDefault())

    private fun relative(date: LocalDate): String =
        DateUtils
            .getRelativeTimeSpanString(
                date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli(),
                System.currentTimeMillis(),
                DateUtils.DAY_IN_MILLIS,
            ).toString()
}

val LocalDateStyle = staticCompositionLocalOf { DateStyle.SYSTEM }
