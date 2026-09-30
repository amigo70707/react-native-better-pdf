package com.margelo.nitro.betterpdf

import com.margelo.nitro.betterpdf.PageTextSelection.End
import com.margelo.nitro.betterpdf.TextSelectionQuery.Boundary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PageTextSelectionTest {
  // "brown fox" wrapped over two lines: "brown" ends line 1, "fox" starts line 2.
  // Ends as PdfRenderer resolves them: points on the baseline.
  private val start = Boundary.Point(400f, 110f)
  private val stop = Boundary.Point(100f, 124f)
  private val twoLines = PageTextSelection(
    page = 2,
    start = start,
    stop = stop,
    text = "brown fox",
    bounds = listOf(PageRect(400f, 100f, 520f, 112f), PageRect(72f, 114f, 100f, 126f)),
  )

  @Test
  fun `handles point below the first and last characters`() {
    assertEquals(PagePoint(2, 400f, 112f), twoLines.anchor(End.START))
    assertEquals(PagePoint(2, 100f, 126f), twoLines.anchor(End.STOP))
  }

  @Test
  fun `the union covers every line`() {
    assertEquals(PageRect(72f, 100f, 520f, 126f), twoLines.union)
  }

  @Test
  fun `dragging the stop handle keeps the start`() {
    val query = twoLines.drag(End.STOP).query(300f, 130f, generation = 5)
    assertEquals(TextSelectionQuery(5, 2, start, Boundary.Point(300f, 130f)), query)
  }

  @Test
  fun `dragging the start handle keeps the stop`() {
    val query = twoLines.drag(End.START).query(80f, 90f, generation = 6)
    assertEquals(TextSelectionQuery(6, 2, Boundary.Point(80f, 90f), stop), query)
  }

  @Test
  fun `a drag stays anchored at the original end after the handles cross`() {
    val drag = twoLines.drag(End.STOP)
    // The stop handle was dragged above the start: the result comes back reversed.
    val crossed = PageTextSelection.of(
      page = 2,
      start = start,
      stop = Boundary.Point(300f, 110f),
      text = "quick ",
      bounds = listOf(PageRect(300f, 100f, 400f, 112f)),
    )
    assertEquals(Boundary.Point(300f, 110f), crossed?.start)
    assertEquals(start, crossed?.stop)
    // Later moves of the same drag still keep the original start.
    assertEquals(start, drag.query(250f, 105f, generation = 7).start)
  }

  @Test
  fun `reading order runs down the page, then left to right within a line`() {
    assertTrue(PageTextSelection.isAfter(Boundary.Point(50f, 124f), Boundary.Point(400f, 110f)))
    assertTrue(PageTextSelection.isAfter(Boundary.Point(401f, 110.5f), Boundary.Point(400f, 110f)))
    assertFalse(PageTextSelection.isAfter(Boundary.Point(399f, 111f), Boundary.Point(400f, 110f)))
    assertTrue(PageTextSelection.isAfter(Boundary.Index(9), Boundary.Index(3)))
  }

  @Test
  fun `a result without text is no selection`() {
    assertNull(PageTextSelection.of(page = 0, start = start, stop = start, text = "", bounds = emptyList()))
    assertNull(PageTextSelection.of(page = 0, start = start, stop = stop, text = "a", bounds = emptyList()))
  }
}
