package com.margelo.nitro.betterpdf

import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.Rect

/**
 * One open page of a [DocumentRenderer]. Used only on the document's worker
 * thread, one page at a time, and closed after use.
 */
internal interface DocumentPage : AutoCloseable {
  /** Page size in PDF points. */
  val width: Int
  val height: Int

  /** Renders for the screen, with every annotation kind the renderer can draw. */
  fun renderForDisplay(target: Bitmap, clip: Rect?, matrix: Matrix)

  /** Links on the page, internal first; empty where the renderer exposes none. */
  fun readLinks(): List<PageLink>

  /** Resolves a text selection; null where it selects nothing or text is unavailable. */
  fun selectText(query: TextSelectionQuery): PageTextSelection?
}
