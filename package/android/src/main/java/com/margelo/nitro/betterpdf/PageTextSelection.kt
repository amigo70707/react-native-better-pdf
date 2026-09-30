package com.margelo.nitro.betterpdf

import com.margelo.nitro.betterpdf.TextSelectionQuery.Boundary
import kotlin.math.abs

/**
 * Text selected on one page: the text, where it starts and stops (as
 * PdfRenderer resolved them, in reading order) and the areas it covers, one
 * or more per line, in PDF points from the page's top-left. Plain Kotlin so
 * the handle geometry is unit tested on the JVM.
 *
 * The ends are points in practice: PdfRenderer's public selection model
 * keeps a boundary's index only when it has no point (measured on API 35:
 * every resolved boundary came back as a point on the baseline with index
 * -1). Sent back unchanged, they resolve to the same characters.
 */
data class PageTextSelection(
  val page: Int,
  val start: Boundary,
  val stop: Boundary,
  val text: String,
  val bounds: List<PageRect>,
) {
  init {
    require(bounds.isNotEmpty()) { "A selection covers at least one area" }
  }

  enum class End { START, STOP }

  /** Where [end]'s handle points: below the first area's left edge, or the last area's right edge. */
  fun anchor(end: End): PagePoint = when (end) {
    End.START -> bounds.first().let { PagePoint(page, it.left, it.bottom) }
    End.STOP -> bounds.last().let { PagePoint(page, it.right, it.bottom) }
  }

  /** Height of the line [end] is on; a dragged handle aims at the line's middle. */
  fun lineHeight(end: End): Float = (if (end == End.START) bounds.first() else bounds.last()).let { it.bottom - it.top }

  /** The smallest rectangle around every area. */
  val union: PageRect
    get() = PageRect(bounds.minOf { it.left }, bounds.minOf { it.top }, bounds.maxOf { it.right }, bounds.maxOf { it.bottom })

  /** Starts dragging [end]'s handle; the other end stays where it is. */
  fun drag(end: End): HandleDrag = HandleDrag(page, end, fixed = if (end == End.START) stop else start)

  /**
   * A handle being dragged. The other end is [fixed] for the whole drag, so
   * the selection stays anchored when the handles cross.
   */
  data class HandleDrag(val page: Int, val end: End, val fixed: Boundary) {
    /** Moves the dragged end to ([x], [y]) in PDF points. */
    fun query(x: Float, y: Float, generation: Int): TextSelectionQuery {
      val point = Boundary.Point(x, y)
      return when (end) {
        End.START -> TextSelectionQuery(generation, page, point, fixed)
        End.STOP -> TextSelectionQuery(generation, page, fixed, point)
      }
    }
  }

  companion object {
    /** Points less than this far apart vertically (PDF points) are on one line. */
    private const val LINE_TOLERANCE = 2f

    /**
     * A selection from PdfRenderer's result, or null when it selected no
     * text. A handle dragged past the other end comes back reversed; the
     * ends are put back in reading order.
     */
    fun of(page: Int, start: Boundary, stop: Boundary, text: String, bounds: List<PageRect>): PageTextSelection? {
      if (text.isEmpty() || bounds.isEmpty()) return null
      return if (isAfter(start, stop)) {
        PageTextSelection(page, stop, start, text, bounds)
      } else {
        PageTextSelection(page, start, stop, text, bounds)
      }
    }

    /** Whether [a] comes after [b] in reading order: lower on the page, or further right on the same line. */
    internal fun isAfter(a: Boundary, b: Boundary): Boolean = when {
      a is Boundary.Index && b is Boundary.Index -> a.index > b.index
      a is Boundary.Point && b is Boundary.Point ->
        if (abs(a.y - b.y) > LINE_TOLERANCE) a.y > b.y else a.x > b.x
      else -> false
    }
  }
}
