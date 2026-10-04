package com.margelo.nitro.betterpdf.outline

import java.util.GregorianCalendar
import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PdfDateTest {
  /** Milliseconds of a UTC date-time. */
  private fun utc(year: Int, month: Int, day: Int, hour: Int = 0, minute: Int = 0, second: Int = 0): Long =
    GregorianCalendar(TimeZone.getTimeZone("UTC")).apply {
      clear()
      set(year, month - 1, day, hour, minute, second)
    }.timeInMillis

  @Test
  fun `full dates with every time-zone form`() {
    assertEquals(utc(2026, 3, 15, 7, 30), PdfDate.parse("D:20260315093000+02'00'"))
    assertEquals(utc(2026, 3, 15, 15, 0), PdfDate.parse("D:20260315093000-05'30'"))
    assertEquals(utc(2026, 3, 15, 7, 30), PdfDate.parse("D:20260315093000+02'00")) // PDF 2.0
    assertEquals(utc(2026, 3, 15, 7, 30), PdfDate.parse("D:20260315093000+0200"))
    assertEquals(utc(2026, 3, 15, 7, 30), PdfDate.parse("D:20260315093000+02"))
    assertEquals(utc(2026, 9, 30, 12), PdfDate.parse("D:20260930120000Z"))
    assertEquals(utc(2026, 9, 30, 12), PdfDate.parse("D:20260930120000Z00'00'"))
  }

  @Test
  fun `without a time zone the date is UTC, like PDFKit`() {
    assertEquals(utc(2026, 9, 30, 12), PdfDate.parse("D:20260930120000"))
  }

  @Test
  fun `parts after the year are optional`() {
    assertEquals(utc(2026, 1, 1), PdfDate.parse("D:2026"))
    assertEquals(utc(2026, 3, 1), PdfDate.parse("D:202603"))
    assertEquals(utc(2026, 3, 15, 9), PdfDate.parse("D:2026031509"))
    assertEquals(utc(2026, 3, 15), PdfDate.parse("20260315")) // without D:
    assertEquals(utc(2026, 3, 15), PdfDate.parse(" D:20260315 "))
  }

  @Test
  fun `dates before October 1582 are Julian, like iOS`() {
    // 1500-01-01 Julian = 1500-01-10 proleptic Gregorian.
    assertEquals(-14_830_992_000_000, PdfDate.parse("D:15000101"))
  }

  @Test
  fun `invalid dates are null`() {
    for (text in listOf(
      "",
      "garbage",
      "D:",
      "D:00000101", // year 0
      "D:２０２６", // non-ASCII digits
      "D:20261315", // month 13
      "D:20260230", // 30 February
      "D:20260315250000", // hour 25
      "D:191000101000000", // the "19100" year bug
      "D:2026031", // a part cut in half
      "2026-03-15T09:30:00Z", // XMP form
      "D:20260315093000+2'00'", // offset hour with one digit
      "D:20260315093000+02'00'X",
    )) {
      assertNull(text, PdfDate.parse(text))
    }
  }
}
