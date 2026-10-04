package com.margelo.nitro.betterpdf

import kotlin.math.abs

/**
 * Pairs the widgets the form's field tree places on a page with the
 * renderer's widgets of that page, which carry no field names: by
 * rectangle first, within [TOLERANCE] points on every edge (the renderer
 * rounds to whole points), then the rest in order. The renderer leaves
 * signature widgets out (measured); leave them out of `expected` too.
 */
internal object FormWidgetMatcher {
  /** For each of [expected], the index in [actual] of its widget, or -1. */
  fun match(expected: List<PageRect>, actual: List<PageRect>): IntArray {
    val result = IntArray(expected.size) { -1 }
    val used = BooleanArray(actual.size)
    expected.forEachIndexed { index, rect ->
      val found = actual.indices.firstOrNull { !used[it] && rect.isCloseTo(actual[it]) } ?: return@forEachIndexed
      result[index] = found
      used[found] = true
    }
    var next = 0
    for (index in result.indices) {
      if (result[index] != -1) continue
      while (next < actual.size && used[next]) next++
      if (next == actual.size) break
      result[index] = next
      used[next] = true
    }
    return result
  }

  private fun PageRect.isCloseTo(other: PageRect): Boolean =
    abs(left - other.left) <= TOLERANCE && abs(top - other.top) <= TOLERANCE &&
      abs(right - other.right) <= TOLERANCE && abs(bottom - other.bottom) <= TOLERANCE

  private const val TOLERANCE = 1.5f
}
