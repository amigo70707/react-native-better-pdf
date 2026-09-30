package com.margelo.nitro.betterpdf.outline

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Fixtures come from three producers: benchmarks/generate-fixtures.mjs
 * (classic xref, and object streams with a predicted xref stream), PDFKit
 * and pypdf (benchmarks/outline-fixtures/). Expected titles and pages were
 * cross-checked with PDFKit, except where noted.
 */
class PdfOutlineReaderTest {
  private fun read(name: String): List<OutlineEntry> {
    val bytes = javaClass.getResourceAsStream("/outline/$name")!!.use { it.readBytes() }
    return PdfOutlineReader(PdfDocumentReader(ByteArraySource(bytes))).read()
  }

  /** Every destination form; see OUTLINE_ITEMS in the generator. */
  private val generated = listOf(
    OutlineEntry("• Introduction — overview", 0, 0), // PDFDocEncoding
    OutlineEntry("Rozdział 1: Zażółć gęślą jaźń", 2, 0), // UTF-16, GoTo action
    OutlineEntry("1.1 Named (name tree)", 3, 1),
    // PDFKit leaves PDF 1.1 named destinations (a name looked up in the
    // catalog's /Dests) unresolved; the specification maps it to page 4.
    OutlineEntry("1.2 Named (catalog Dests)", 4, 1),
    OutlineEntry("Głęboko", 5, 2),
    OutlineEntry("1.3 Named dictionary", 6, 1),
    OutlineEntry("الفصل الثاني", 7, 0),
    OutlineEntry("No destination", null, 0),
    OutlineEntry("External link", null, 0),
    OutlineEntry("Last page", 11, 0),
  )

  @Test
  fun `classic cross-reference table`() = assertEquals(generated, read("outline.pdf"))

  @Test
  fun `object streams and a predicted cross-reference stream`() =
    assertEquals(generated, read("outline-compressed.pdf"))

  @Test
  fun `broken startxref is recovered by scanning the file`() =
    assertEquals(generated, read("malformed-outline-garbage-xref.pdf"))

  @Test
  fun `written by PDFKit`() = assertEquals(
    listOf(
      OutlineEntry("Front matter", 0, 0),
      OutlineEntry("Część pierwsza", 1, 0),
      OutlineEntry("Rozdział 1.1", 2, 1),
      OutlineEntry("Rozdział 1.2", 3, 1),
      OutlineEntry("Anhang — Übersicht", 9, 0),
    ),
    read("outline-pdfkit.pdf"),
  )

  @Test
  fun `written by pypdf`() = assertEquals(
    listOf(
      OutlineEntry("Kapitel 1", 0, 0),
      OutlineEntry("Abschnitt 1.1", 1, 1),
      OutlineEntry("Abschnitt 1.2", 2, 1),
      OutlineEntry("Tief — deep", 3, 2),
      OutlineEntry("日本語の章", 5, 0),
      OutlineEntry("Ende", 9, 0),
    ),
    read("outline-pypdf.pdf"),
  )

  @Test
  fun `cycles in sibling and child links terminate`() = assertEquals(
    listOf(OutlineEntry("A", 0, 0), OutlineEntry("B", 1, 0)),
    read("malformed-outline-cycle.pdf"),
  )

  @Test
  fun `depth is limited`() {
    val entries = read("malformed-outline-deep.pdf")
    assertEquals(PdfOutlineReader.MAX_DEPTH, entries.size)
    assertEquals(PdfOutlineReader.MAX_DEPTH - 1, entries.last().depth)
    assertEquals("Level 63", entries.last().title)
  }

  @Test
  fun `a document without an outline has no entries`() = assertTrue(read("sample.pdf").isEmpty())

  @Test
  fun `encrypted documents are rejected`() {
    val bytes = javaClass.getResourceAsStream("/outline/encrypted.pdf")!!.use { it.readBytes() }
    assertThrows(PdfFormatException::class.java) { PdfDocumentReader(ByteArraySource(bytes)) }
  }

  @Test
  fun `garbage is rejected, not looped on`() {
    val bytes = ByteArray(10_000) { (it * 31).toByte() }
    assertThrows(PdfFormatException::class.java) { PdfDocumentReader(ByteArraySource(bytes)) }
  }
}
