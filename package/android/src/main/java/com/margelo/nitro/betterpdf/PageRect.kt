package com.margelo.nitro.betterpdf

/**
 * A rectangle on a page in PDF points from the page's top-left corner.
 * Plain Kotlin (unlike `android.graphics.RectF`) so hit testing is unit
 * testable on the JVM.
 */
data class PageRect(val left: Float, val top: Float, val right: Float, val bottom: Float) {
  /** Edges are inclusive: a tap on a link's border still hits the link. */
  fun contains(x: Float, y: Float): Boolean = x in left..right && y in top..bottom
}
