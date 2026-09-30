package com.margelo.nitro.betterpdf

import android.graphics.RectF

/** What [PdfAccessibilityHelper] needs from the view it describes. */
internal interface PdfAccessibilityHost {
  val accessibilityPageCount: Int

  /** Pages currently on screen, in order. */
  fun accessibilityVisiblePages(): IntRange

  /** [page]'s rectangle in view coordinates, clipped to the view; empty when off screen. */
  fun accessibilityPageBounds(page: Int, out: RectF)

  /** The page under view point ([x], [y]), or -1. */
  fun accessibilityPageAt(x: Float, y: Float): Int

  /** "Page 3 of 12" (localisable through `pageAccessibilityLabel`). */
  fun accessibilityPageLabel(page: Int): String

  /** The page's text once read; null while unknown or where no text is available. */
  fun accessibilityPageText(page: Int): String?
}
