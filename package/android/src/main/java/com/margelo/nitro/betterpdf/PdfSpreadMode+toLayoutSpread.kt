package com.margelo.nitro.betterpdf

internal fun PdfSpreadMode.toLayoutSpread(): PdfDocumentLayout.Spread = when (this) {
  PdfSpreadMode.NONE -> PdfDocumentLayout.Spread.NONE
  PdfSpreadMode.PAIRS -> PdfDocumentLayout.Spread.PAIRS
  PdfSpreadMode.BOOK -> PdfDocumentLayout.Spread.BOOK
}
