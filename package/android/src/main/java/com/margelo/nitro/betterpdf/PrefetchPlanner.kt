package com.margelo.nitro.betterpdf

import kotlin.math.max
import kotlin.math.min

/**
 * Decides which pages to render, in priority order.
 *
 * Visible pages come first, the one nearest the viewport center before the
 * others. Then pages ahead in the scroll direction, then pages behind it.
 * The look-ahead is asymmetric: when the user reverses direction the planner
 * reverses with them on the next frame, and anything queued for the old
 * direction is dropped before it starts rendering.
 */
object PrefetchPlanner {
  enum class Direction { FORWARD, BACKWARD }

  const val DEFAULT_AHEAD = 2
  /**
   * Two pages behind (rather than one) cut renders by 19% and cache misses
   * from 8.2% to 6.5% in a scroll pattern with direction changes, for ~6 MB
   * more cache (docs/performance/results.md).
   */
  const val DEFAULT_BEHIND = 2

  fun plan(
    visible: IntRange,
    centerPage: Int,
    pageCount: Int,
    direction: Direction,
    ahead: Int = DEFAULT_AHEAD,
    behind: Int = DEFAULT_BEHIND,
  ): List<Int> {
    if (visible.isEmpty() || pageCount <= 0) return emptyList()
    val result = ArrayList<Int>(visible.count() + ahead + behind)

    // Visible pages, nearest to the center first.
    val center = centerPage.coerceIn(visible.first, visible.last)
    result += center
    var offset = 1
    while (center - offset >= visible.first || center + offset <= visible.last) {
      if (direction == Direction.FORWARD) {
        if (center + offset <= visible.last) result += center + offset
        if (center - offset >= visible.first) result += center - offset
      } else {
        if (center - offset >= visible.first) result += center - offset
        if (center + offset <= visible.last) result += center + offset
      }
      offset++
    }

    val forward = (visible.last + 1..minOf(pageCount - 1, visible.last + if (direction == Direction.FORWARD) ahead else behind))
    val backward = (visible.first - 1 downTo maxOf(0, visible.first - if (direction == Direction.BACKWARD) ahead else behind))
    if (direction == Direction.FORWARD) {
      result.addAll(forward)
      result.addAll(backward)
    } else {
      result.addAll(backward)
      result.addAll(forward)
    }
    return result
  }

  /** How much of a plan the cache can hold without thrashing. */
  data class Fit(val keep: Int, val budgetBytes: Long)

  /**
   * The cache must be able to hold every planned page at once; otherwise each
   * new render evicts another planned page, which is then requested again
   * (measured: misses 11% → 55–70% and renders +60–90% when the plan was
   * ~1.3 pages larger than the budget).
   *
   * Returns the budget to use — at least [baseBudget], grown to cover the
   * plan up to [maxBudget] — and how many planned pages to keep. Pages are
   * dropped from the lowest-priority end, never below [minKeep] (the visible
   * pages).
   */
  fun fitToBudget(pageBytes: List<Long>, minKeep: Int, baseBudget: Long, maxBudget: Long): Fit {
    val cap = max(baseBudget, maxBudget)
    var total = 0L
    var keep = 0
    for (bytes in pageBytes) {
      if (keep >= minKeep && total + bytes > cap) break
      total += bytes
      keep++
    }
    return Fit(keep, max(baseBudget, min(total, cap)))
  }
}
