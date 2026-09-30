package com.margelo.nitro.betterpdf

internal fun PdfFitPolicy.toLayoutFit(): PdfDocumentLayout.Fit = when (this) {
  PdfFitPolicy.WIDTH -> PdfDocumentLayout.Fit.WIDTH
  PdfFitPolicy.HEIGHT -> PdfDocumentLayout.Fit.HEIGHT
  PdfFitPolicy.PAGE -> PdfDocumentLayout.Fit.PAGE
}
