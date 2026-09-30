package com.margelo.nitro.betterpdf

/**
 * A point on a page in PDF points (1/72") from the page's top-left corner.
 * Produced by [PdfDocumentLayout.pagePointAt]; independent of zoom and screen
 * density, and the same unit PdfRenderer uses for link bounds.
 */
data class PagePoint(val page: Int, val x: Float, val y: Float)
