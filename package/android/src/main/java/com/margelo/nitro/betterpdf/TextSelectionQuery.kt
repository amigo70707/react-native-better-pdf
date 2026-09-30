package com.margelo.nitro.betterpdf

/**
 * A text selection on one page, resolved on the render thread
 * ([PdfDocumentSession.selectText]). Each end is a point on the page in PDF
 * points from its top-left, or an index into the page's text; the end that
 * stays put during a handle drag is sent as PdfRenderer resolved it. When
 * both ends are the same point, the word at that point is selected.
 *
 * [generation] increases with every query a view sends, so results that
 * arrive after a newer one was shown can be dropped.
 */
data class TextSelectionQuery(
  val generation: Int,
  val page: Int,
  val start: Boundary,
  val stop: Boundary,
) : DocumentWork {
  sealed interface Boundary {
    data class Index(val index: Int) : Boundary

    data class Point(val x: Float, val y: Float) : Boundary
  }
}
