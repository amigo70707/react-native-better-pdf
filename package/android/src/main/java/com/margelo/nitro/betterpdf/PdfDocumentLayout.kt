package com.margelo.nitro.betterpdf

/**
 * Placement of every page in unzoomed content pixels.
 *
 * Pages follow each other along the main [axis] (vertical by default,
 * horizontal when requested). Every page is scaled individually by [fit]:
 * to the viewport width, height, or entirely into the viewport. Pages
 * narrower than the widest page are centred on the cross axis; the cross
 * extent is at least the viewport.
 *
 * Continuous layouts separate pages by [spacing], with half of it before the
 * first and after the last page (matching PDFKit's page break margins on iOS).
 * Paged layouts give every page a slot of at least one viewport with the page
 * centred in it, so a scroll offset of `slotStart(i)` shows the start of
 * page `i`; a page longer than the viewport gets a slot of its own length.
 *
 * A right-to-left horizontal layout ([isRightToLeft]) places the first page
 * at the right end. Page indices stay in document order everywhere in this
 * API; only positions are mirrored, so a larger scroll offset can mean a
 * smaller page index.
 *
 * Pure and immutable: rebuilt when the viewport, spacing, axis, paging, fit
 * or direction change. Lookups are O(log n) so they stay cheap on every
 * scroll frame even for documents with thousands of pages.
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

  val pageCount: Int = pageSizes.size

  /** Whether page order runs against the scroll offset (RTL, horizontal only). */
  val isReversed: Boolean = isRightToLeft && axis == Axis.HORIZONTAL

  // All arrays are indexed by *visual* slot along the main axis (see
  // [visual]); public functions take and return document page indices.
  private val slotStarts = FloatArray(pageCount)
  private val slotEnds = FloatArray(pageCount)
  private val lefts = FloatArray(pageCount)
  private val tops = FloatArray(pageCount)
  private val widths = FloatArray(pageCount)
  private val heights = FloatArray(pageCount)
  private val pointsToPixels = FloatArray(pageCount)

  /** Content size at zoom 1. */
  val contentWidth: Float
  val contentHeight: Float

  init {
    val horizontal = axis == Axis.HORIZONTAL
    val mainViewport = if (horizontal) viewportWidth else viewportHeight
    val crossViewport = if (horizontal) viewportHeight else viewportWidth
    for (slot in 0 until pageCount) {
      val size = pageSizes[visual(slot)].sanitized()
      val scale = when (fit) {
        Fit.WIDTH -> viewportWidth / size.widthPoints
        Fit.HEIGHT -> viewportHeight / size.heightPoints
        Fit.PAGE -> minOf(viewportWidth / size.widthPoints, viewportHeight / size.heightPoints)
      }
      widths[slot] = size.widthPoints * scale
      heights[slot] = size.heightPoints * scale
      pointsToPixels[slot] = scale
    }
    val crossLengths = if (horizontal) heights else widths
    val crossExtent = maxOf(crossViewport, crossLengths.maxOrNull() ?: 0f)

    var cursor = if (isPaged) 0f else spacing / 2f
    for (slot in 0 until pageCount) {
      val mainLength = if (horizontal) widths[slot] else heights[slot]
      val slotLength = if (isPaged) maxOf(mainViewport, mainLength) else mainLength
      val mainStart = cursor + (slotLength - mainLength) / 2f
      val crossStart = (crossExtent - crossLengths[slot]) / 2f

      slotStarts[slot] = if (isPaged) cursor else cursor - spacing / 2f
      slotEnds[slot] = if (isPaged) cursor + slotLength else cursor + slotLength + spacing / 2f
      lefts[slot] = if (horizontal) mainStart else crossStart
      tops[slot] = if (horizontal) crossStart else mainStart
      cursor += slotLength + if (isPaged) 0f else spacing
    }
    val mainLength = when {
      pageCount == 0 -> 0f
      isPaged -> cursor
      else -> cursor - spacing / 2f
    }
    contentWidth = if (horizontal) mainLength else crossExtent
    contentHeight = if (horizontal) crossExtent else mainLength
  }

  /** Maps a page index to its visual slot and back (the mapping is its own inverse). */
  private fun visual(index: Int): Int = if (isReversed) pageCount - 1 - index else index

  fun pageLeft(index: Int): Float = lefts[visual(index)]

  fun pageTop(index: Int): Float = tops[visual(index)]

  fun pageRight(index: Int): Float = pageLeft(index) + pageWidth(index)

  fun pageBottom(index: Int): Float = pageTop(index) + pageHeight(index)

  fun pageWidth(index: Int): Float = widths[visual(index)]

  fun pageHeight(index: Int): Float = heights[visual(index)]

  /** Main-axis position to scroll to so page [index] starts the viewport. */
  fun slotStart(index: Int): Float = slotStarts[visual(index)]

  fun slotEnd(index: Int): Float = slotEnds[visual(index)]

  /** The page after [index] in scroll direction (larger offsets); may be out of range. */
  fun nextInScrollOrder(index: Int): Int = if (isReversed) index - 1 else index + 1

  /** The page before [index] in scroll direction (smaller offsets); may be out of range. */
  fun previousInScrollOrder(index: Int): Int = if (isReversed) index + 1 else index - 1

  /** Index of the page whose slot contains main-axis [position], clamped to the document. */
  fun slotAt(position: Float): Int {
    if (pageCount == 0) return -1
    var low = 0
    var high = pageCount - 1
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
    return visual(result)
  }

  private fun mainStartOfSlot(slot: Int): Float = if (axis == Axis.HORIZONTAL) lefts[slot] else tops[slot]

  private fun mainEndOfSlot(slot: Int): Float =
    if (axis == Axis.HORIZONTAL) lefts[slot] + widths[slot] else tops[slot] + heights[slot]

  /** Multiply PDF points by this to get unzoomed content pixels for [index]. */
  fun pointsToPixels(index: Int): Float = pointsToPixels[visual(index)]

  /**
   * Index of the page at main-axis position [position], or the nearest page
   * when it falls into a gap or outside the document. -1 for an empty
   * document.
   */
  fun pageAt(position: Float): Int = if (pageCount == 0) -1 else visual(slotOfPageAt(position))

  /** Visual slot of the page at [position]; see [pageAt]. */
  private fun slotOfPageAt(position: Float): Int {
    // Last slot whose page starts at or before position.
    var low = 0
    var high = pageCount - 1
    var result = 0
    while (low <= high) {
      val mid = (low + high) ushr 1
      if (mainStartOfSlot(mid) <= position) {
        result = mid
        low = mid + 1
      } else {
        high = mid - 1
      }
    }
    // In the gap after `result`? Pick whichever page is closer.
    if (result + 1 < pageCount && position > mainEndOfSlot(result)) {
      val distanceAfter = mainStartOfSlot(result + 1) - position
      val distanceBefore = position - mainEndOfSlot(result)
      if (distanceAfter < distanceBefore) return result + 1
    }
    return result
  }

  /**
   * The page under content-space ([x], [y]) and the point on it in PDF
   * points from the page's top-left corner; `null` in gaps and outside pages.
   */
  fun pagePointAt(x: Float, y: Float): PagePoint? {
    if (pageCount == 0) return null
    val page = pageAt(if (axis == Axis.HORIZONTAL) x else y)
    if (x < pageLeft(page) || x > pageRight(page) || y < pageTop(page) || y > pageBottom(page)) return null
    val scale = pointsToPixels(page)
    return PagePoint(page, (x - pageLeft(page)) / scale, (y - pageTop(page)) / scale)
  }

  /**
   * Content-space ([x], [y]) as a point of the page whose slot contains it
   * along the main axis (the nearest page; the later one at a gap's
   * midpoint), in PDF points from its top-left corner — negative or past the
   * page when outside it. Inverse of [contentX] / [contentY]. Null when empty.
   */
  fun pagePointNear(x: Float, y: Float): PagePoint? {
    if (pageCount == 0) return null
    // Scrolling to a slot start can stop up to a pixel short of it
    // (OverScroller works in whole pixels); that still belongs to the slot.
    val page = slotAt((if (axis == Axis.HORIZONTAL) x else y) + SLOT_EPSILON)
    val scale = pointsToPixels(page)
    return PagePoint(page, (x - pageLeft(page)) / scale, (y - pageTop(page)) / scale)
  }

  /** Content-space x of [point] (see [pagePointNear]); its page must exist. */
  fun contentX(point: PagePoint): Float = pageLeft(point.page) + point.x * pointsToPixels(point.page)

  /** Content-space y of [point] (see [pagePointNear]); its page must exist. */
  fun contentY(point: PagePoint): Float = pageTop(point.page) + point.y * pointsToPixels(point.page)

  /**
   * Whether the far end of the last page (its bottom; in horizontal layouts
   * its right edge, or left in RTL) lies within the main-axis band
   * [start, end]. The half gap around a page belongs to no page, so an edge
   * hidden only by it counts (e.g. a page exactly as wide as the view,
   * reached with goToPage), plus half a pixel of rounding.
   */
  fun isEndVisible(start: Float, end: Float): Boolean {
    if (pageCount == 0) return false
    val last = pageCount - 1
    val edge = when {
      axis == Axis.VERTICAL -> pageBottom(last)
      isReversed -> pageLeft(last)
      else -> pageRight(last)
    }
    val tolerance = spacing / 2f + EDGE_TOLERANCE
    return edge >= start - tolerance && edge <= end + tolerance
  }

  /** Pages intersecting the main-axis band [start, end], in page order; empty when none. */
  fun visibleRange(start: Float, end: Float): IntRange {
    if (pageCount == 0 || end < start) return IntRange.EMPTY
    var first = slotOfPageAt(start)
    if (mainEndOfSlot(first) < start && first + 1 < pageCount) first++
    var last = slotOfPageAt(end)
    if (mainStartOfSlot(last) > end && last > 0) last--
    if (last < first) return IntRange.EMPTY
    return if (isReversed) visual(last)..visual(first) else first..last
  }

  private companion object {
    const val EDGE_TOLERANCE = 0.5f
    const val SLOT_EPSILON = 1f
  }
}
