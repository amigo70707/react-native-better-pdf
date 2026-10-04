package com.margelo.nitro.betterpdf.outline

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

/**
 * Fixtures: benchmarks/generate-fixtures.mjs `metadata*` (METADATA in the
 * generator). Expected values were cross-checked with PDFKit's raw
 * information dictionary (`CGPDFDocument.info`), which the iOS
 * implementation reads.
 */
class PdfInfoReaderTest {
  private fun read(name: String): PdfInfo {
    val bytes = javaClass.getResourceAsStream("/outline/$name")!!.use { it.readBytes() }
    return PdfInfoReader(PdfDocumentReader(ByteArraySource(bytes))).read()
  }

  private val expected = PdfInfo(
    title = "Raport kwartalny — żółć", // UTF-16
    author = "Better PDF fixtures",
    subject = null, // empty
    keywords = "pdf, metadata, fixture",
    creator = "generate-fixtures.mjs", // padded with spaces
    producer = "react-native-better-pdf",
    createdAtMs = 1_773_559_800_000, // 2026-03-15 09:30 +02:00
    modifiedAtMs = 1_790_769_600_000, // 2026-09-30 12:00 UTC
  )

  @Test
  fun `classic cross-reference table`() = assertEquals(expected, read("metadata.pdf"))

  @Test
  fun `object streams and a cross-reference stream`() = assertEquals(expected, read("metadata-compressed.pdf"))

  @Test
  fun `documents without an information dictionary have no metadata`() {
    assertEquals(PdfInfo(), read("outline.pdf"))
    assertEquals(PdfInfo(), read("page-boxes-compressed.pdf"))
  }

  @Test
  fun `metadata of other producers`() {
    // pypdf writes its name as the producer.
    assertEquals(PdfInfo(producer = "pypdf"), read("outline-pypdf.pdf"))
    // Quartz writes dates as D:20260929223140Z00'00'.
    assertEquals(
      PdfInfo(
        producer = "macOS Version 26.6 (Build 25G72) Quartz PDFContext",
        createdAtMs = 1_790_721_100_000,
        modifiedAtMs = 1_790_721_100_000,
      ),
      read("outline-pdfkit.pdf"),
    )
  }

  @Test
  fun `encrypted documents are rejected`() {
    assertThrows(PdfFormatException::class.java) { read("metadata-encrypted.pdf") }
  }
}
