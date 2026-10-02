package com.margelo.nitro.betterpdf

/**
 * How [PdfDocumentView] lays out and scrolls pages. [isSinglePage] takes
 * precedence over direction and paging; [isRightToLeft] only affects
 * horizontal layouts, [spread] only vertical continuous ones.
 */
data class LayoutMode(
  val isHorizontal: Boolean = false,
  val isPaged: Boolean = false,
  val fit: PdfDocumentLayout.Fit = PdfDocumentLayout.Fit.WIDTH,
  val isSinglePage: Boolean = false,
  val isRightToLeft: Boolean = false,
  val spread: PdfDocumentLayout.Spread = PdfDocumentLayout.Spread.NONE,
)
