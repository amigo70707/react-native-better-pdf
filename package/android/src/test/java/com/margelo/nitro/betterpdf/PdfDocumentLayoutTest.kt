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

  @Test
  fun `pagePointNear maps any content point to the nearest page, also outside it`() {
    // A4 at 1 px per point, 100 px gaps: page 1 starts at 50 + 842 + 100.
    val layout = PdfDocumentLayout(List(3) { a4 }, viewportWidth = 595f, spacing = 100f)
    val inPage = layout.pagePointNear(10f, 50f + 842f + 100f + 20f)!!
    assertEquals(PagePoint(1, 10f, 20f), inPage)
    // In the gap just above page 1: negative y on page 1.
    val inGap = layout.pagePointNear(0f, 50f + 842f + 100f - 30f)!!
    assertEquals(1, inGap.page)
    assertEquals(-30f, inGap.y, 0.001f)
    assertNull(PdfDocumentLayout(emptyList(), viewportWidth = 595f, spacing = 0f).pagePointNear(0f, 0f))
    // goToPage puts the slot start (mid-gap) at the top: that is the next
    // page, also when the scroller stops a pixel short.
    assertEquals(1, layout.pagePointNear(0f, layout.slotStart(1))!!.page)
    assertEquals(1, layout.pagePointNear(0f, layout.slotStart(1) - 0.9f)!!.page)
    assertEquals(-50f, layout.pagePointNear(0f, layout.slotStart(1))!!.y, 0.001f)
  }

  @Test
  fun `contentX and contentY invert pagePointNear across zoom-independent points`() {
    val layout = PdfDocumentLayout(List(5) { a4 }, viewportWidth = 1190f, spacing = 16f)
    for (y in listOf(-8f, 0f, 1234.5f, 5000f)) {
      val point = layout.pagePointNear(37f, y)!!
      assertEquals(37f, layout.contentX(point), 0.01f)
      assertEquals(y, layout.contentY(point), 0.01f)
    }
    // 2 px per point: a point is half as many content pixels from the page top.
    assertEquals(layout.pageTop(3) + 200f, layout.contentY(PagePoint(3, 0f, 100f)), 0.01f)
  }

  @Test
  fun `isEndVisible is true only while the last page's far edge is on screen`() {
    val layout = PdfDocumentLayout(List(3) { a4 }, viewportWidth = 595f, spacing = 0f)
    val end = 3 * 842f
    assertTrue(layout.isEndVisible(end - 800f, end))
    assertFalse(layout.isEndVisible(end - 900f, end - 100f))
    // A short document that fits entirely.
    val single = PdfDocumentLayout(listOf(a4), viewportWidth = 595f, spacing = 0f)
    assertTrue(single.isEndVisible(0f, 2000f))
    // Hidden only by the half gap after it: still the end.
    val spaced = PdfDocumentLayout(List(3) { a4 }, viewportWidth = 595f, spacing = 20f)
    assertTrue(spaced.isEndVisible(spaced.pageBottom(2) - 800f - 10f, spaced.pageBottom(2) - 10f))
    assertFalse(spaced.isEndVisible(spaced.pageBottom(2) - 800f - 11f, spaced.pageBottom(2) - 11f))
  }

  @Test
  fun `isEndVisible uses the far edge of the last page in horizontal and right-to-left layouts`() {
    val sizes = List(3) { a4 }
    val ltr = PdfDocumentLayout(sizes, 595f, 0f, 842f, PdfDocumentLayout.Axis.HORIZONTAL, fit = PdfDocumentLayout.Fit.PAGE)
    assertTrue(ltr.isEndVisible(ltr.pageRight(2) - 595f, ltr.pageRight(2)))
    assertFalse(ltr.isEndVisible(0f, 595f))
    val rtl = PdfDocumentLayout(sizes, 595f, 0f, 842f, PdfDocumentLayout.Axis.HORIZONTAL, fit = PdfDocumentLayout.Fit.PAGE, isRightToLeft = true)
    // The last page is leftmost in RTL.
    assertTrue(rtl.isEndVisible(0f, 595f))
    assertFalse(rtl.isEndVisible(rtl.contentWidth - 595f, rtl.contentWidth))
  }

  // region Facing pages

  private fun spreads(
    count: Int,
    spread: PdfDocumentLayout.Spread,
    sizes: List<PageSize> = List(count) { a4 },
    fit: PdfDocumentLayout.Fit = PdfDocumentLayout.Fit.WIDTH,
  ) = PdfDocumentLayout(sizes, viewportWidth = 1000f, spacing = 10f, viewportHeight = 800f, fit = fit, spread = spread)

  @Test
  fun `pairs put two pages in a row, each fitting half the width and meeting in the middle`() {
    val layout = spreads(4, PdfDocumentLayout.Spread.PAIRS)
    val height = 500f * 842f / 595f
    assertEquals(500f, layout.pageWidth(0), 0.01f)
    assertEquals(0f, layout.pageLeft(0), 0.01f)
    assertEquals(500f, layout.pageLeft(1), 0.01f)
    assertEquals(layout.pageTop(0), layout.pageTop(1), 0f)
    assertEquals(5f, layout.pageTop(0), 0f)
    assertEquals(5f + height + 10f, layout.pageTop(2), 0.01f)
    assertEquals(layout.pageTop(2), layout.pageTop(3), 0f)
    assertEquals(2 * height + 2 * 10f, layout.contentHeight, 0.01f)
    assertEquals(1000f, layout.contentWidth, 0.01f)
  }

  @Test
  fun `book shows the first page alone and centred, then pairs`() {
    val layout = spreads(4, PdfDocumentLayout.Spread.BOOK)
    // Rows: [0], [1, 2], [3].
    assertEquals(250f, layout.pageLeft(0), 0.01f)
    assertEquals(0f, layout.pageLeft(1), 0.01f)
    assertEquals(500f, layout.pageLeft(2), 0.01f)
    assertEquals(layout.pageTop(1), layout.pageTop(2), 0f)
    // A last page without a partner is centred too.
    assertEquals(250f, layout.pageLeft(3), 0.01f)
    assertTrue(layout.pageTop(3) > layout.pageBottom(1))
  }

  @Test
  fun `an odd page at the end of pairs is centred`() {
    val layout = spreads(3, PdfDocumentLayout.Spread.PAIRS)
    assertEquals(250f, layout.pageLeft(2), 0.01f)
  }

  @Test
  fun `a shorter facing page is centred along its row, and rows of different widths are centred as a whole`() {
    val landscape = PageSize(842f, 595f)
    val layout = PdfDocumentLayout(
      listOf(a4, landscape),
      viewportWidth = 1000f,
      spacing = 10f,
      viewportHeight = 600f,
      fit = PdfDocumentLayout.Fit.PAGE,
      spread = PdfDocumentLayout.Spread.PAIRS,
    )
    // Fit page within a 500-wide column, 600 high: A4 is limited by height.
    val a4Width = 600f * 595f / 842f
    val landscapeHeight = 500f * 595f / 842f
    assertEquals(600f, layout.pageHeight(0), 0.01f)
    assertEquals(a4Width, layout.pageWidth(0), 0.01f)
    assertEquals(500f, layout.pageWidth(1), 0.01f)
    assertEquals(5f, layout.pageTop(0), 0.01f)
    assertEquals(5f + (600f - landscapeHeight) / 2f, layout.pageTop(1), 0.01f)
    // The pair is centred, its pages touching.
    assertEquals((1000f - a4Width - 500f) / 2f, layout.pageLeft(0), 0.01f)
    assertEquals(layout.pageRight(0), layout.pageLeft(1), 0.01f)
  }

  @Test
  fun `fit height ignores the columns`() {
    val layout = spreads(2, PdfDocumentLayout.Spread.PAIRS, fit = PdfDocumentLayout.Fit.HEIGHT)
    assertEquals(800f, layout.pageHeight(0), 0.01f)
    assertEquals(800f * 595f / 842f, layout.pageWidth(0), 0.01f)
  }

  @Test
  fun `lookups report the first page of a spread and move by rows`() {
    val layout = spreads(5, PdfDocumentLayout.Spread.BOOK)
    // Rows: [0], [1, 2], [3, 4].
    assertEquals(1, layout.pageAt(layout.pageTop(2) + 10f))
    assertEquals(3, layout.slotAt(layout.slotStart(4) + 1f))
    assertEquals(layout.slotStart(1), layout.slotStart(2), 0f)
    assertEquals(0, layout.spreadStart(0))
    assertEquals(1, layout.spreadStart(2))
    assertEquals(3, layout.spreadStart(4))
    assertEquals(1, layout.nextInScrollOrder(0))
    assertEquals(3, layout.nextInScrollOrder(2))
    assertEquals(5, layout.nextInScrollOrder(4))
    assertEquals(1, layout.previousInScrollOrder(4))
    assertEquals(-1, layout.previousInScrollOrder(0))
    // Both pages of every intersecting row.
    assertEquals(1..4, layout.visibleRange(layout.pageTop(1) + 1f, layout.pageTop(3) + 1f))
  }

  @Test
  fun `pagePointAt and pagePointNear pick the facing page by x`() {
    val layout = spreads(2, PdfDocumentLayout.Spread.PAIRS)
    val y = layout.pageTop(0) + 100f
    assertEquals(0, layout.pagePointAt(100f, y)?.page)
    val right = layout.pagePointAt(600f, y)
    assertEquals(1, right?.page)
    assertEquals(100f / layout.pointsToPixels(1), right!!.x, 0.01f)
    assertEquals(1, layout.pagePointNear(900f, layout.slotStart(0))?.page)
    assertEquals(0, layout.pagePointNear(10f, layout.slotStart(0))?.page)
    // Round trip.
    val point = layout.pagePointNear(700f, 300f)!!
    assertEquals(700f, layout.contentX(point), 0.01f)
    assertEquals(300f, layout.contentY(point), 0.01f)
  }

  @Test
  fun `pagePointNear beside a lone page gives a point off the page`() {
    val layout = spreads(3, PdfDocumentLayout.Spread.BOOK)
    val point = layout.pagePointNear(0f, layout.slotStart(0))!!
    assertEquals(0, point.page)
    assertTrue(point.x < 0f)
  }

  @Test
  fun `isEndVisible uses the bottom of the last row`() {
    val layout = spreads(4, PdfDocumentLayout.Spread.PAIRS)
    assertTrue(layout.isEndVisible(layout.pageBottom(3) - 800f, layout.pageBottom(3)))
    assertFalse(layout.isEndVisible(0f, 800f))
  }

  @Test
  fun `facing pages apply to vertical continuous layouts only`() {
    val sizes = List(4) { a4 }
    val horizontal = PdfDocumentLayout(sizes, 1000f, 10f, 800f, PdfDocumentLayout.Axis.HORIZONTAL, spread = PdfDocumentLayout.Spread.PAIRS)
    val paged = PdfDocumentLayout(sizes, 1000f, 10f, 800f, isPaged = true, spread = PdfDocumentLayout.Spread.BOOK)
    val single = PdfDocumentLayout(sizes, 1000f, 10f, 800f, PdfDocumentLayout.Axis.HORIZONTAL)
    assertEquals(PdfDocumentLayout.Spread.NONE, horizontal.spread)
    assertEquals(PdfDocumentLayout.Spread.NONE, paged.spread)
    for (page in 0 until 4) {
      assertEquals(single.pageLeft(page), horizontal.pageLeft(page), 0f)
      assertEquals(page, paged.spreadStart(page))
    }
  }

  @Test
  fun `facing pages handle tiny documents`() {
    assertEquals(-1, spreads(0, PdfDocumentLayout.Spread.BOOK).pageAt(0f))
    val one = spreads(1, PdfDocumentLayout.Spread.PAIRS)
    assertEquals(250f, one.pageLeft(0), 0.01f)
    assertEquals(0..0, one.visibleRange(0f, 2000f))
  }

  // endregion
}
