package com.margelo.nitro.betterpdf

internal fun DocumentViewport.toPdfViewportChangeEvent(): PdfViewportChangeEvent =
  PdfViewportChangeEvent(
    viewport = PdfViewport(
      page = anchor.page.toDouble(),
      x = anchor.x.toDouble(),
      y = anchor.y.toDouble(),
      scale = scale.toDouble(),
    ),
    firstVisiblePage = visiblePages.first.toDouble(),
    lastVisiblePage = visiblePages.last.toDouble(),
    isAtEnd = isAtEnd,
  )
