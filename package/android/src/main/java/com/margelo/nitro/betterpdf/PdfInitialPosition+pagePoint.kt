package com.margelo.nitro.betterpdf

/** The page point to put at the top-left corner; null without (finite) `x` and `y`. */
internal val PdfInitialPosition.pagePoint: PagePoint?
  get() {
    val x = x?.takeIf { it.isFinite() } ?: return null
    val y = y?.takeIf { it.isFinite() } ?: return null
    return PagePoint(page.toInt(), x.toFloat(), y.toFloat())
  }
