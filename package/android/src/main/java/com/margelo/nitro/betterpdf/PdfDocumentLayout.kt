package com.margelo.nitro.betterpdf

/**
 * Placement of every page in unzoomed content pixels.
 *
 * Pages are laid out in rows that follow each other along the main [axis]
 * (vertical by default, horizontal when requested). A row holds one page,
 * or two facing pages with [spread] (vertical continuous layouts only).
 * Every page is scaled individually by [fit]: to the viewport width (with
 * facing pages, half of it), height, or entirely into the viewport. Rows
 * narrower than the widest row are centred on the cross axis, facing pages
 * meeting at the row's middle; pages shorter than their row are centred
 * along it. The cross extent is at least the viewport.
 *
 * Continuous layouts separate rows by [spacing], with half of it before the
 * first and after the last row (matching PDFKit's page break margins on iOS).
 * Paged layouts give every page a slot of at least one viewport with the page
 * centred in it, so a scroll offset of `slotStart(i)` shows the start of
 * page `i`; a page longer than the viewport gets a slot of its own length.
 *
 * A right-to-left horizontal layout ([isRightToLeft]) places the first page
 * at the right end. Page indices stay in document order everywhere in this
 * API; only positions are mirrored, so a larger scroll offset can mean a
 * smaller page index.
 *
 * Pure and immutable: rebuilt when the viewport, spacing, axis, paging, fit,
 * direction or spread change. Lookups are O(log n) so they stay cheap on
 * every scroll frame even for documents with thousands of pages.
 */
class PdfDocumentLayout(
  pageSizes: List<PageSize>,
  val viewportWidth: Float,
  val spacing: Float,
  val viewportHeight: Float = 0f,
  val axis: Axis = Axis.VERTICAL,
  val isPaged: Boolean = false,
  val fit: Fit = Fit.WIDTH,
  isRightToLeft: Boolean = false,
  spread: Spread = Spread.NONE,
) {
  data class PageSize(val widthPoints: Float, val heightPoints: Float) {
    /**
     * This size, or US Letter when degenerate. PDF user space is limited to
     * 14,400 units per side (PDF 1.7 Annex C); larger values are clamped.
     */
    fun sanitized(): PageSize {
      if (!widthPoints.isFinite() || !heightPoints.isFinite() || widthPoints <= 0f || heightPoints <= 0f) {
        return PageSize(612f, 792f)
      }
      return PageSize(widthPoints.coerceAtMost(MAX_POINTS), heightPoints.coerceAtMost(MAX_POINTS))
    }

    private companion object {
      const val MAX_POINTS = 14_400f
    }
  }

  enum class Axis { VERTICAL, HORIZONTAL }

  /** How a page is scaled to the viewport at zoom 1. */
  enum class Fit { WIDTH, HEIGHT, PAGE }

  /**
   * Facing pages side by side: [PAIRS] groups pages 0–1, 2–3, …; [BOOK]
   * shows page 0 alone (a cover), then 1–2, 3–4, ….
   */
  enum class Spread { NONE, PAIRS, BOOK }

  val pageCount: Int = pageSizes.size

  /** Whether page order runs against the scroll offset (RTL, horizontal only). */
  val isReversed: Boolean = isRightToLeft && axis == Axis.HORIZONTAL

  /** [spread] where it applies: vertical continuous layouts; [Spread.NONE] elsewhere. */
  val spread: Spread = if (axis == Axis.VERTICAL && !isPaged) spread else Spread.NONE

  // Pages, by document index.
  private val lefts = FloatArray(pageCount)
  private val tops = FloatArray(pageCount)
  private val widths = FloatArray(pageCount)
  private val heights = FloatArray(pageCount)
  private val pointsToPixels = FloatArray(pageCount)
  private val rowOfPage = IntArray(pageCount)

  // Rows, in visual order along the main axis. A row's pages are
  // consecutive: [rowFirstPages, rowLastPages] in document order.
  private val rowCount: Int
  private val rowFirstPages: IntArray
  private val rowLastPages: IntArray
  private val slotStarts: FloatArray
  private val slotEnds: FloatArray
  // Main-axis extent of the row's pages, without the gaps around them.
  private val rowMainStarts: FloatArray
  private val rowMainEnds: FloatArray

  /** Content size at zoom 1. */
  val contentWidth: Float
  val contentHeight: Float

  init {
    val horizontal = axis == Axis.HORIZONTAL
    val mainViewport = if (horizontal) viewportWidth else viewportHeight
    val crossViewport = if (horizontal) viewportHeight else viewportWidth
    val columnWidth = if (this.spread == Spread.NONE) viewportWidth else viewportWidth / 2f
    for (page in 0 until pageCount) {
      val size = pageSizes[page].sanitized()
      val scale = when (fit) {
        Fit.WIDTH -> columnWidth / size.widthPoints
        Fit.HEIGHT -> viewportHeight / size.heightPoints
        Fit.PAGE -> minOf(columnWidth / size.widthPoints, viewportHeight / size.heightPoints)
      }
      widths[page] = size.widthPoints * scale
      heights[page] = size.heightPoints * scale
      pointsToPixels[page] = scale
    }

    val rows = buildRows()
    rowCount = rows.size
    rowFirstPages = IntArray(rowCount) { rows[it].first }
    rowLastPages = IntArray(rowCount) { rows[it].last }
    slotStarts = FloatArray(rowCount)
    slotEnds = FloatArray(rowCount)
    rowMainStarts = FloatArray(rowCount)
    rowMainEnds = FloatArray(rowCount)

    val mainLengths = if (horizontal) widths else heights
    val crossLengths = if (horizontal) heights else widths
    val rowCrossLengths = FloatArray(rowCount) { row -> rows[row].sumOf { crossLengths[it].toDouble() }.toFloat() }
    val crossExtent = maxOf(crossViewport, rowCrossLengths.maxOrNull() ?: 0f)

    var cursor = if (isPaged) 0f else spacing / 2f
    for (row in 0 until rowCount) {
      val pages = rows[row]
      val rowMainLength = pages.maxOf { mainLengths[it] }
      val slotLength = if (isPaged) maxOf(mainViewport, rowMainLength) else rowMainLength
      // Facing pages meet in the middle of a row that is centred as a whole.
      var crossCursor = (crossExtent - rowCrossLengths[row]) / 2f
      for (page in pages) {
        rowOfPage[page] = row
        val mainStart = cursor + (slotLength - mainLengths[page]) / 2f
        if (horizontal) {
          lefts[page] = mainStart
          tops[page] = crossCursor
        } else {
          lefts[page] = crossCursor
          tops[page] = mainStart
        }
        crossCursor += crossLengths[page]
      }
      slotStarts[row] = if (isPaged) cursor else cursor - spacing / 2f
      slotEnds[row] = if (isPaged) cursor + slotLength else cursor + slotLength + spacing / 2f
      rowMainStarts[row] = cursor + (slotLength - rowMainLength) / 2f
      rowMainEnds[row] = rowMainStarts[row] + rowMainLength
      cursor += slotLength + if (isPaged) 0f else spacing
    }
    val mainLength = when {
      rowCount == 0 -> 0f
      isPaged -> cursor
      else -> cursor - spacing / 2f
    }
    contentWidth = if (horizontal) mainLength else crossExtent
    contentHeight = if (horizontal) crossExtent else mainLength
  }

  /** Page indices per row in visual order. */
  private fun buildRows(): List<IntRange> = when (spread) {
    Spread.NONE -> List(pageCount) { slot -> (if (isReversed) pageCount - 1 - slot else slot).let { it..it } }
    Spread.PAIRS -> (0 until pageCount step 2).map { it..minOf(it + 1, pageCount - 1) }
    Spread.BOOK -> if (pageCount == 0) {
      emptyList()
    } else {
      listOf(0..0) + (1 until pageCount step 2).map { it..minOf(it + 1, pageCount - 1) }
    }
  }

  fun pageLeft(index: Int): Float = lefts[index]

  fun pageTop(index: Int): Float = tops[index]

  fun pageRight(index: Int): Float = pageLeft(index) + pageWidth(index)

  fun pageBottom(index: Int): Float = pageTop(index) + pageHeight(index)

  fun pageWidth(index: Int): Float = widths[index]

  fun pageHeight(index: Int): Float = heights[index]

  /** Main-axis position to scroll to so the row of page [index] starts the viewport. */
  fun slotStart(index: Int): Float = slotStarts[rowOfPage[index]]

  fun slotEnd(index: Int): Float = slotEnds[rowOfPage[index]]

  /**
   * The first page of the spread showing page [index]: the page the layout
   * reports for its row ([pageAt], [slotAt]). [index] itself without facing
   * pages.
   */
  fun spreadStart(index: Int): Int = rowFirstPages[rowOfPage[index]]

  /**
   * The page leading the row after [index]'s in scroll direction (larger
   * offsets); out of range past the end: `pageCount`, or -1 when reversed.
   */
  fun nextInScrollOrder(index: Int): Int {
    val row = rowOfPage[index] + 1
    return if (row < rowCount) rowFirstPages[row] else if (isReversed) -1 else pageCount
  }

  /**
   * The page leading the row before [index]'s in scroll direction (smaller
   * offsets); out of range past the start: -1, or `pageCount` when reversed.
   */
  fun previousInScrollOrder(index: Int): Int {
    val row = rowOfPage[index] - 1
    return if (row >= 0) rowFirstPages[row] else if (isReversed) pageCount else -1
  }

  /** The first page of the row whose slot contains main-axis [position], clamped to the document. */
  fun slotAt(position: Float): Int {
    if (rowCount == 0) return -1
    var low = 0
    var high = rowCount - 1
    var result = 0
    while (low <= high) {
      val mid = (low + high) ushr 1
      if (slotStarts[mid] <= position) {
        result = mid
        low = mid + 1
      } else {
        high = mid - 1
      }
    }
    return rowFirstPages[result]
  }

  /** Multiply PDF points by this to get unzoomed content pixels for [index]. */
  fun pointsToPixels(index: Int): Float = pointsToPixels[index]

  /**
   * The first page of the row at main-axis position [position], or of the
   * nearest row when it falls into a gap or outside the document. -1 for an
   * empty document.
   */
  fun pageAt(position: Float): Int = if (rowCount == 0) -1 else rowFirstPages[rowAt(position)]

  /** The row whose pages span main-axis [position], or the nearest row; see [pageAt]. */
  private fun rowAt(position: Float): Int {
    // Last row whose pages start at or before position.
    var low = 0
    var high = rowCount - 1
    var result = 0
    while (low <= high) {
      val mid = (low + high) ushr 1
      if (rowMainStarts[mid] <= position) {
        result = mid
        low = mid + 1
      } else {
        high = mid - 1
      }
    }
    // In the gap after `result`? Pick whichever row is closer.
    if (result + 1 < rowCount && position > rowMainEnds[result]) {
      val distanceAfter = rowMainStarts[result + 1] - position
      val distanceBefore = position - rowMainEnds[result]
      if (distanceAfter < distanceBefore) return result + 1
    }
    return result
  }

  /** The page of [row] nearest to cross-axis [position] (the one containing it, if any). */
  private fun pageInRowNear(row: Int, position: Float): Int {
    val first = rowFirstPages[row]
    val last = rowLastPages[row]
    if (first == last) return first
    // Two facing pages (vertical layouts only): the right one starts at the middle.
    return if (position < lefts[last]) first else last
  }

  /**
   * The page under content-space ([x], [y]) and the point on it in PDF
   * points from the page's top-left corner; `null` in gaps and outside pages.
   */
  fun pagePointAt(x: Float, y: Float): PagePoint? {
    if (rowCount == 0) return null
    val horizontal = axis == Axis.HORIZONTAL
    val row = rowAt(if (horizontal) x else y)
    val page = pageInRowNear(row, if (horizontal) y else x)
    if (x < pageLeft(page) || x > pageRight(page) || y < pageTop(page) || y > pageBottom(page)) return null
    val scale = pointsToPixels(page)
    return PagePoint(page, (x - pageLeft(page)) / scale, (y - pageTop(page)) / scale)
  }

  /**
   * Content-space ([x], [y]) as a point of the page whose slot contains it
   * along the main axis (the nearest page; the later one at a gap's
   * midpoint; with facing pages, the one nearer along the cross axis), in
   * PDF points from its top-left corner — negative or past the page when
   * outside it. Inverse of [contentX] / [contentY]. Null when empty.
   */
  fun pagePointNear(x: Float, y: Float): PagePoint? {
    if (rowCount == 0) return null
    val horizontal = axis == Axis.HORIZONTAL
    // Scrolling to a slot start can stop up to a pixel short of it
    // (OverScroller works in whole pixels); that still belongs to the slot.
    val row = rowOfPage[slotAt((if (horizontal) x else y) + SLOT_EPSILON)]
    val page = pageInRowNear(row, if (horizontal) y else x)
    val scale = pointsToPixels(page)
    return PagePoint(page, (x - pageLeft(page)) / scale, (y - pageTop(page)) / scale)
  }

  /** Content-space x of [point] (see [pagePointNear]); its page must exist. */
  fun contentX(point: PagePoint): Float = pageLeft(point.page) + point.x * pointsToPixels(point.page)

  /** Content-space y of [point] (see [pagePointNear]); its page must exist. */
  fun contentY(point: PagePoint): Float = pageTop(point.page) + point.y * pointsToPixels(point.page)

  /**
   * Whether the far end of the last row (its bottom; in horizontal layouts
   * its right edge, or left in RTL) lies within the main-axis band
   * [start, end]. The half gap around a row belongs to no page, so an edge
   * hidden only by it counts (e.g. a page exactly as wide as the view,
   * reached with goToPage), plus half a pixel of rounding.
   */
  fun isEndVisible(start: Float, end: Float): Boolean {
    if (rowCount == 0) return false
    // Reversed, the last page leads the first row (the left end).
    val edge = if (isReversed) rowMainStarts[0] else rowMainEnds[rowCount - 1]
    val tolerance = spacing / 2f + EDGE_TOLERANCE
    return edge >= start - tolerance && edge <= end + tolerance
  }

  /** Pages intersecting the main-axis band [start, end], in page order; empty when none. */
  fun visibleRange(start: Float, end: Float): IntRange {
    if (rowCount == 0 || end < start) return IntRange.EMPTY
    var first = rowAt(start)
    if (rowMainEnds[first] < start && first + 1 < rowCount) first++
    var last = rowAt(end)
    if (rowMainStarts[last] > end && last > 0) last--
    if (last < first) return IntRange.EMPTY
    return if (isReversed) rowFirstPages[last]..rowFirstPages[first] else rowFirstPages[first]..rowLastPages[last]
  }

  private companion object {
    const val EDGE_TOLERANCE = 0.5f
    const val SLOT_EPSILON = 1f
  }
}
