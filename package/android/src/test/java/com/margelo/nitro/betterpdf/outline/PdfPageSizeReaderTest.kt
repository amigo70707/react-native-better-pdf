package com.margelo.nitro.betterpdf.outline

import com.margelo.nitro.betterpdf.PdfDocumentLayout.PageSize
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

/**
 * Fixtures: benchmarks/generate-fixtures.mjs `page-boxes*` (expected sizes
 * in each page's heading), and the outline fixtures. Every expected size and
 * page count was cross-checked with PDFKit (crop box, rotation applied).
 */
class PdfPageSizeReaderTest {
  private fun read(name: String): List<PageSize> {
    val bytes = javaClass.getResourceAsStream("/outline/$name")!!.use { it.readBytes() }
    return PdfPageSizeReader(PdfDocumentReader(ByteArraySource(bytes))).read()
  }

  private val expected = listOf(
    PageSize(612f, 792f), // media box inherited from /Pages
    PageSize(842f, 595f), // rotate 90
    PageSize(500f, 600f), // crop box
    PageSize(792f, 400f), // inherited media box, crop box, rotate 270
    PageSize(300f, 400f), // offset origin, rotate 180
    PageSize(595f, 842f), // crop box clipped to the media box
    PageSize(500f, 300f), // rotate -90
  )

  @Test
  fun `classic cross-reference table`() = assertEquals(expected, read("page-boxes.pdf"))

  @Test
  fun `object streams and a cross-reference stream`() = assertEquals(expected, read("page-boxes-compressed.pdf"))

  @Test
  fun `uniform documents from other producers`() {
    assertEquals(List(12) { PageSize(595f, 842f) }, read("outline.pdf"))
    assertEquals(List(12) { PageSize(595f, 842f) }, read("outline-pdfkit.pdf"))
    assertEquals(List(10) { PageSize(595f, 842f) }, read("outline-pypdf.pdf"))
  }

  @Test
  fun `encrypted documents are left to the renderer`() {
    assertThrows(PdfFormatException::class.java) { read("page-boxes-encrypted.pdf") }
  }

  @Test
  fun `a cyclic page tree terminates`() {
    // Only the outline is cyclic in this fixture; the page tree must still read.
    assertEquals(2, read("malformed-outline-cycle.pdf").size)
  }
}
