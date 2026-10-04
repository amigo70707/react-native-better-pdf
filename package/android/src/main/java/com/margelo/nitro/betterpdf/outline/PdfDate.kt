package com.margelo.nitro.betterpdf.outline

import java.util.GregorianCalendar
import java.util.TimeZone

/**
 * PDF dates (ISO 32000-1 §7.9.4): `D:YYYYMMDDHHmmSSOHH'mm'`, every part
 * after the year optional (defaults: January, the 1st, midnight), `O` one of
 * `Z`, `+`, `-`. Without a time zone the date is read as UTC, as PDFKit
 * does. The same rules as iOS's `PdfDate`.
 */
internal object PdfDate {
  private val FORMAT = Regex(
    """^(?:D:)?([0-9]{4})([0-9]{2})?([0-9]{2})?([0-9]{2})?([0-9]{2})?([0-9]{2})?(?:([Zz+-])(?:([0-9]{2})(?:'?([0-9]{2}))?'?)?)?$""",
  )

  /** Milliseconds since the Unix epoch; null for anything that is not a valid PDF date. */
  fun parse(text: String): Long? {
    val match = FORMAT.matchEntire(text.trim()) ?: return null
    val parts = match.groupValues
    fun part(index: Int, default: Int): Int = parts[index].takeIf { it.isNotEmpty() }?.toInt() ?: default
    val year = part(1, 0)
    val month = part(2, 1)
    val day = part(3, 1)
    val hour = part(4, 0)
    val minute = part(5, 0)
    val second = part(6, 0)
    val offsetHours = part(8, 0)
    val offsetMinutes = part(9, 0)
    if (year < 1 || month !in 1..12 || day !in 1..31 || hour > 23 || minute > 59 || second > 59) return null
    if (offsetHours > 23 || offsetMinutes > 59) return null
    // Julian before October 1582, like Foundation's Gregorian calendar on iOS.
    val calendar = GregorianCalendar(TimeZone.getTimeZone("UTC")).apply {
      isLenient = false
      clear()
      set(year, month - 1, day, hour, minute, second)
    }
    val asUtc = try {
      calendar.timeInMillis
    } catch (_: IllegalArgumentException) {
      // A day the month does not have.
      return null
    }
    val offsetMs = (offsetHours * 60L + offsetMinutes) * 60_000L
    return when (parts[7]) {
      "+" -> asUtc - offsetMs
      "-" -> asUtc + offsetMs
      else -> asUtc
    }
  }
}
