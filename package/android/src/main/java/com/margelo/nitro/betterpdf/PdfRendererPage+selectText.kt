package com.margelo.nitro.betterpdf

import android.graphics.pdf.PdfRenderer
import android.os.Build
import android.os.ext.SdkExtensions

/**
 * Resolves [query] on this page; null where it selects no text, and before
 * Android 15 (API 35), where PdfRenderer started exposing page text. Must be
 * called on the thread that owns the renderer, while the page is open.
 */
internal fun PdfRenderer.Page.selectText(query: TextSelectionQuery): PageTextSelection? {
  // API 35 implies SDK extension 13; checked explicitly for lint.
  if (Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM ||
    SdkExtensions.getExtensionVersion(Build.VERSION_CODES.S) < 13
  ) {
    return null
  }
  val selection = selectContent(query.start.toSelectionBoundary(), query.stop.toSelectionBoundary()) ?: return null
  return selection.toPageTextSelection(query.page)
}
