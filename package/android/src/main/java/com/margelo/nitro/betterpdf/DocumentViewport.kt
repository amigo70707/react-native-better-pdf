package com.margelo.nitro.betterpdf

import kotlin.math.abs

/**
 * Where the viewport is: the page point at its top-left corner ([anchor],
 * PDF points; negative or past the page outside it), the zoom relative to
 * the fitted page, the pages on screen, and whether the end of the last
 * page is on screen. Reported by [PdfDocumentView.Listener.onViewportSettled].
 */
data class DocumentViewport(
  val anchor: PagePoint,
  val scale: Float,
  val visiblePages: IntRange,
  val isAtEnd: Boolean,
) {
  /** Same position within rounding noise (0.5 pt, 0.001 zoom): not worth an event. */
  fun isCloseTo(other: DocumentViewport): Boolean =
    anchor.page == other.anchor.page &&
      abs(anchor.x - other.anchor.x) < POSITION_EPSILON &&
      abs(anchor.y - other.anchor.y) < POSITION_EPSILON &&
      abs(scale - other.scale) < SCALE_EPSILON &&
      visiblePages == other.visiblePages &&
      isAtEnd == other.isAtEnd

  private companion object {
    const val POSITION_EPSILON = 0.5f
    const val SCALE_EPSILON = 0.001f
  }
}
