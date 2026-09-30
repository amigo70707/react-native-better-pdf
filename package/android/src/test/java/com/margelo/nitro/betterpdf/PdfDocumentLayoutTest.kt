package com.margelo.nitro.betterpdf

import com.margelo.nitro.betterpdf.PdfDocumentLayout.PageSize
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PdfDocumentLayoutTest {
  private val a4 = PageSize(595f, 842f)

  @Test
  fun `pages are scaled to the viewport width`() {
    val layout = PdfDocumentLayout(listOf(a4, PageSize(842f, 595f)), viewportWidth = 1000f, spacing = 0f)
    assertEquals(1000f * 842f / 595f, layout.pageHeight(0), 0.01f)
    assertEquals(1000f * 595f / 842f, layout.pageHeight(1), 0.01f)
    assertEquals(1000f / 595f, layout.pointsToPixels(0), 0.0001f)
  }

  @Test
  fun `spacing separates pages with half margins at both ends`() {
    val layout = PdfDocumentLayout(List(3) { a4 }, viewportWidth = 595f, spacing = 10f)
    assertEquals(5f, layout.pageTop(0), 0f)
    assertEquals(5f + 842f + 10f, layout.pageTop(1), 0f)
    assertEquals(3 * 842f + 3 * 10f, layout.contentHeight, 0.01f)
  }

  @Test
  fun `pageAt finds the containing page and the nearest page in gaps`() {
    val layout = PdfDocumentLayout(List(3) { a4 }, viewportWidth = 595f, spacing = 100f)
    assertEquals(0, layout.pageAt(-50f))
    assertEquals(0, layout.pageAt(400f))
    // Gap between page 0 (ends 892) and page 1 (starts 992).
    assertEquals(0, layout.pageAt(900f))
    assertEquals(1, layout.pageAt(980f))
    assertEquals(2, layout.pageAt(1e9f))
  }

  @Test
  fun `visibleRange returns every intersecting page and nothing else`() {
    val layout = PdfDocumentLayout(List(10) { a4 }, viewportWidth = 595f, spacing = 0f)
    assertEquals(0..0, layout.visibleRange(0f, 500f))
    assertEquals(0..1, layout.visibleRange(800f, 900f))
    assertEquals(2..4, layout.visibleRange(842f * 2 + 1, 842f * 4 + 10))
    assertTrue(layout.visibleRange(10f, 5f).isEmpty())
  }

  @Test
  fun `visibleRange skips a page that is only inside the gap`() {
    val layout = PdfDocumentLayout(List(3) { a4 }, viewportWidth = 595f, spacing = 100f)
    // Viewport entirely inside the gap below page 0.
    val range = layout.visibleRange(900f, 950f)
    assertTrue("expected empty, got $range", range.isEmpty())
  }

  @Test
  fun `lookups stay correct for large documents`() {
    val layout = PdfDocumentLayout(List(10_000) { a4 }, viewportWidth = 595f, spacing = 8f)
    val page = 7_321
    val middle = layout.pageTop(page) + layout.pageHeight(page) / 2
    assertEquals(page, layout.pageAt(middle))
    assertEquals(page..page, layout.visibleRange(middle - 10f, middle + 10f))
  }

  @Test
  fun `degenerate and hostile page sizes are sanitized`() {
    val layout = PdfDocumentLayout(
      listOf(PageSize(0f, 0f), PageSize(Float.NaN, 100f), PageSize(-5f, 10f), PageSize(1e9f, 1e9f)),
      viewportWidth = 612f,
      spacing = 0f,
    )
    assertEquals(792f, layout.pageHeight(0), 0.01f)
    assertEquals(792f, layout.pageHeight(1), 0.01f)
    assertEquals(792f, layout.pageHeight(2), 0.01f)
    assertTrue(layout.pageHeight(3).isFinite())
  }

  @Test
  fun `empty document`() {
    val layout = PdfDocumentLayout(emptyList(), viewportWidth = 100f, spacing = 8f)
    assertEquals(-1, layout.pageAt(0f))
    assertTrue(layout.visibleRange(0f, 100f).isEmpty())
    assertEquals(0f, layout.contentHeight, 0f)
  }

  @Test
  fun `pagePointAt maps content pixels to PDF points from the page's top-left`() {
    // 595 pt wide pages at 1190 px: 2 px per point.
    val layout = PdfDocumentLayout(List(3) { a4 }, viewportWidth = 1190f, spacing = 20f)
    val secondPageTop = layout.pageTop(1)
    val point = layout.pagePointAt(200f, secondPageTop + 100f)
    assertEquals(PagePoint(1, 100f, 50f), point)
  }

  @Test
  fun `pagePointAt returns null in gaps and beside the page`() {
    val layout = PdfDocumentLayout(List(3) { a4 }, viewportWidth = 595f, spacing = 100f)
    assertNull(layout.pagePointAt(10f, layout.pageBottom(0) + 20f)) // gap
    assertNull(layout.pagePointAt(-1f, 10f + layout.pageTop(0)))
    assertNull(layout.pagePointAt(596f, 10f + layout.pageTop(0)))
    assertNull(PdfDocumentLayout(emptyList(), 100f, 0f).pagePointAt(1f, 1f))
  }

  @Test
  fun `horizontal layout fits whole pages in a row, centred vertically`() {
    // Viewport 400 x 1000: an A4 page (595 x 842) fits by width (scale 400/595).
    val layout = PdfDocumentLayout(
      List(3) { a4 }, viewportWidth = 400f, spacing = 10f, viewportHeight = 1000f,
      axis = PdfDocumentLayout.Axis.HORIZONTAL, fit = PdfDocumentLayout.Fit.PAGE,
    )
    val pageHeight = 842f * 400f / 595f
    assertEquals(400f, layout.pageWidth(0), 0.01f)
    assertEquals((1000f - pageHeight) / 2f, layout.pageTop(0), 0.01f)
    assertEquals(5f, layout.pageLeft(0), 0.01f)
    assertEquals(5f + 400f + 10f, layout.pageLeft(1), 0.01f)
    assertEquals(3 * 400f + 3 * 10f, layout.contentWidth, 0.01f)
    assertEquals(1000f, layout.contentHeight, 0f)
    assertEquals(1, layout.pageAt(layout.pageLeft(1) + 1f))
    assertEquals(1..2, layout.visibleRange(layout.pageLeft(1) + 1f, layout.pageLeft(2) + 1f))
  }

  @Test
  fun `paged layout gives every page a viewport-sized slot with the page centred`() {
    val layout = PdfDocumentLayout(
      listOf(a4, PageSize(842f, 595f)), viewportWidth = 1000f, spacing = 10f, viewportHeight = 1000f,
      isPaged = true, fit = PdfDocumentLayout.Fit.PAGE,
    )
    assertEquals(0f, layout.slotStart(0), 0f)
    assertEquals(1000f, layout.slotStart(1), 0f)
    assertEquals(2000f, layout.contentHeight, 0f)
    // Portrait page fits by height, centred horizontally.
    assertEquals(1000f, layout.pageHeight(0), 0.01f)
    assertEquals((1000f - 595f * 1000f / 842f) / 2f, layout.pageLeft(0), 0.01f)
    // Landscape page fits by width, centred vertically in its slot.
    assertEquals(1000f, layout.pageWidth(1), 0.01f)
    assertEquals(1000f + (1000f - 595f * 1000f / 842f) / 2f, layout.pageTop(1), 0.01f)
  }

  @Test
  fun `slotAt finds the slot for any position and clamps outside the document`() {
    val layout = PdfDocumentLayout(List(4) { a4 }, viewportWidth = 500f, spacing = 0f, viewportHeight = 800f, isPaged = true, fit = PdfDocumentLayout.Fit.PAGE)
    assertEquals(0, layout.slotAt(-50f))
    assertEquals(0, layout.slotAt(799f))
    assertEquals(1, layout.slotAt(800f))
    assertEquals(3, layout.slotAt(1e9f))
  }

  @Test
  fun `pagePointAt works in horizontal layouts and rejects the space around a page`() {
    val layout = PdfDocumentLayout(
      List(2) { a4 }, viewportWidth = 595f, spacing = 0f, viewportHeight = 2000f,
      axis = PdfDocumentLayout.Axis.HORIZONTAL, fit = PdfDocumentLayout.Fit.PAGE,
    )
    val top = layout.pageTop(1)
    assertEquals(PagePoint(1, 100f, 50f), layout.pagePointAt(layout.pageLeft(1) + 100f, top + 50f))
    assertNull(layout.pagePointAt(layout.pageLeft(1) + 100f, top - 1f))
  }

  @Test
  fun `the default mode is unchanged - vertical, fit width`() {
    val layout = PdfDocumentLayout(List(2) { a4 }, viewportWidth = 595f, spacing = 8f)
    assertEquals(0f, layout.pageLeft(0), 0f)
    assertEquals(595f, layout.pageWidth(0), 0f)
    assertEquals(595f, layout.contentWidth, 0f)
    assertEquals(layout.pageTop(1) - 4f, layout.slotStart(1), 0.001f)
  }

  @Test
  fun `fit height makes wide pages scroll sideways and centres narrow ones`() {
    // A4 portrait and landscape in a 400 x 800 view, fitted to the height.
    val layout = PdfDocumentLayout(
      listOf(a4, PageSize(842f, 595f)), viewportWidth = 400f, spacing = 0f, viewportHeight = 800f,
      fit = PdfDocumentLayout.Fit.HEIGHT,
    )
    assertEquals(800f, layout.pageHeight(0), 0.01f)
    assertEquals(800f, layout.pageHeight(1), 0.01f)
    val landscapeWidth = 842f * 800f / 595f
    assertEquals(landscapeWidth, layout.contentWidth, 0.01f)
    assertEquals(0f, layout.pageLeft(1), 0.01f)
    assertEquals((landscapeWidth - layout.pageWidth(0)) / 2f, layout.pageLeft(0), 0.01f)
  }

  @Test
  fun `fit page in a vertical continuous layout shrinks pages taller than the view`() {
    val layout = PdfDocumentLayout(
      listOf(a4), viewportWidth = 600f, spacing = 0f, viewportHeight = 421f, fit = PdfDocumentLayout.Fit.PAGE,
    )
    assertEquals(421f, layout.pageHeight(0), 0.01f)
    assertEquals(297.5f, layout.pageWidth(0), 0.01f)
    assertEquals(600f, layout.contentWidth, 0f)
    assertEquals((600f - 297.5f) / 2f, layout.pageLeft(0), 0.01f)
  }

  @Test
  fun `paged fit width gives a page longer than the view a slot of its own length`() {
    // A4 at 595 wide is 842 tall, longer than the 500 px view.
    val layout = PdfDocumentLayout(
      List(3) { a4 }, viewportWidth = 595f, spacing = 0f, viewportHeight = 500f, isPaged = true,
    )
    assertEquals(0f, layout.slotStart(0), 0f)
    assertEquals(842f, layout.slotEnd(0), 0.01f)
    assertEquals(842f, layout.slotStart(1), 0.01f)
    assertEquals(0f, layout.pageTop(0), 0.01f)
    assertEquals(3 * 842f, layout.contentHeight, 0.01f)
    assertEquals(0, layout.slotAt(841f))
    assertEquals(1, layout.slotAt(842f))
  }

  @Test
  fun `right-to-left mirrors horizontal positions but keeps page indices`() {
    // Three pages of different widths, paged, in a 400 x 800 view.
    val sizes = listOf(a4, PageSize(842f, 595f), PageSize(300f, 300f))
    val ltr = PdfDocumentLayout(
      sizes, viewportWidth = 400f, spacing = 0f, viewportHeight = 800f,
      axis = PdfDocumentLayout.Axis.HORIZONTAL, isPaged = true, fit = PdfDocumentLayout.Fit.PAGE,
    )
    val rtl = PdfDocumentLayout(
      sizes, viewportWidth = 400f, spacing = 0f, viewportHeight = 800f,
      axis = PdfDocumentLayout.Axis.HORIZONTAL, isPaged = true, fit = PdfDocumentLayout.Fit.PAGE,
      isRightToLeft = true,
    )
    assertTrue(rtl.isReversed)
    assertEquals(ltr.contentWidth, rtl.contentWidth, 0f)
    // Page 0 sits at the right end, the last page at offset 0.
    assertEquals(800f, rtl.slotStart(0), 0f)
    assertEquals(0f, rtl.slotStart(2), 0f)
    for (page in 0..2) {
      assertEquals(ltr.pageWidth(page), rtl.pageWidth(page), 0.01f)
      assertEquals(ltr.pageHeight(page), rtl.pageHeight(page), 0.01f)
      assertEquals(ltr.contentWidth - ltr.slotEnd(page), rtl.slotStart(page), 0.01f)
    }
    assertEquals(2, rtl.slotAt(10f))
    assertEquals(0, rtl.slotAt(1100f))
    assertEquals(1, rtl.pageAt(600f))
    assertEquals(0..1, rtl.visibleRange(500f, 900f))
    assertEquals(0, rtl.nextInScrollOrder(1))
    assertEquals(2, rtl.previousInScrollOrder(1))
    // Tap on the left part of the rightmost page lands on page 0.
    val point = rtl.pagePointAt(rtl.pageLeft(0) + 1f, rtl.pageTop(0) + 1f)
    assertEquals(0, point?.page)
  }

  @Test
  fun `right-to-left has no effect on vertical layouts`() {
    val layout = PdfDocumentLayout(List(3) { a4 }, viewportWidth = 595f, spacing = 0f, isRightToLeft = true)
    assertFalse(layout.isReversed)
    assertEquals(0f, layout.slotStart(0), 0f)
    assertEquals(1, layout.nextInScrollOrder(0))
  }
}
