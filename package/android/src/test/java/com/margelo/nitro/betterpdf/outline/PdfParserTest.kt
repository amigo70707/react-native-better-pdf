package com.margelo.nitro.betterpdf.outline

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class PdfParserTest {
  private fun parse(text: String): PdfObject =
    PdfParser(ByteArraySource(text.toByteArray(Charsets.ISO_8859_1)), 0).readObject()

  private fun text(value: PdfObject): String = (value as PdfObject.Str).toText()

  @Test
  fun `literal strings with escapes, nesting and line continuations`() {
    assertEquals("a(b)c\n\t\\ A", text(parse("(a\\(b\\)c\\n\\t\\\\ \\101)")))
    assertEquals("x(y)z", text(parse("(x(y)z)")))
    assertEquals("onetwo", text(parse("(one\\\ntwo)")))
  }

  @Test
  fun `hex strings ignore whitespace and pad an odd digit`() {
    assertEquals("AB", text(parse("<41 4 2>")))
    assertEquals("P", text(parse("<5>")))
  }

  @Test
  fun `names decode #xx escapes`() = assertEquals(PdfObject.Name("A B#"), parse("/A#20B#23"))

  @Test
  fun `references need two non-negative integers and R`() {
    assertEquals(PdfObject.Reference(12, 0), parse("12 0 R"))
    assertEquals(
      PdfObject.Array(listOf(PdfObject.Number(1.0), PdfObject.Number(2.0), PdfObject.Number(3.0))),
      parse("[1 2 3]"),
    )
    assertEquals(PdfObject.Array(listOf(PdfObject.Reference(4, 0), PdfObject.Name("Fit"))), parse("[4 0 R /Fit]"))
  }

  @Test
  fun `dictionaries and comments`() {
    val value = parse("<< /A 1 % comment\n /B [true null] >>") as PdfObject.Dictionary
    assertEquals(PdfObject.Number(1.0), value["A"])
    assertEquals(PdfObject.Array(listOf(PdfObject.Bool(true), PdfObject.Null)), value["B"])
  }

  @Test
  fun `text strings in UTF-16 and PDFDocEncoding`() {
    assertEquals("Łódź", PdfObject.Str(byteArrayOf(0xFE.toByte(), 0xFF.toByte(), 0x01, 0x41, 0x00, 0xF3.toByte(), 0x00, 0x64, 0x01, 0x7A)).toText())
    assertEquals("• — €", PdfObject.Str(byteArrayOf(0x80.toByte(), 0x20, 0x84.toByte(), 0x20, 0xA0.toByte())).toText())
  }

  @Test
  fun `deep nesting fails instead of overflowing the stack`() {
    assertThrows(PdfFormatException::class.java) { parse("[".repeat(10_000)) }
  }

  @Test
  fun `unterminated strings fail`() {
    assertThrows(PdfFormatException::class.java) { parse("(never closed") }
  }
}
