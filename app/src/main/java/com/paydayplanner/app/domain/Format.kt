package com.paydayplanner.app.domain

import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.math.abs

object Money {
    fun format(cents: Long, symbol: String): String {
        val sign = if (cents < 0) "-" else ""
        val a = abs(cents)
        return sign + symbol + String.format("%,d.%02d", a / 100, a % 100)
    }

    /** Parses user input like "1500" or "1,500.50" into cents. Returns null if invalid. */
    fun parse(text: String): Long? {
        val value = text.replace(",", "").trim().toBigDecimalOrNull() ?: return null
        if (value.signum() < 0) return null
        return runCatching { value.movePointRight(2).setScale(0, RoundingMode.HALF_UP).longValueExact() }.getOrNull()
    }

    fun toInput(cents: Long): String =
        if (cents == 0L) "" else BigDecimal.valueOf(cents, 2).stripTrailingZeros().toPlainString()
}

object Dates {
    private val shortFmt = DateTimeFormatter.ofPattern("MMM d")
    private val longFmt = DateTimeFormatter.ofPattern("MMM d, yyyy")

    fun short(d: LocalDate): String = d.format(shortFmt)
    fun long(d: LocalDate): String = d.format(longFmt)

    fun range(p: Period): String =
        if (p.start.year == p.end.year) "${short(p.start)} – ${long(p.end)}"
        else "${long(p.start)} – ${long(p.end)}"

    fun ordinal(n: Int): String = n.toString() + when {
        n % 100 in 11..13 -> "th"
        n % 10 == 1 -> "st"
        n % 10 == 2 -> "nd"
        n % 10 == 3 -> "rd"
        else -> "th"
    }
}
