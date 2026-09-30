package com.margelo.nitro.betterpdf

import android.graphics.pdf.PdfRenderer
import android.os.Build
import android.os.ext.SdkExtensions

/**
 * The page's links, internal (goto) links first so they win when both
 * overlap. Empty before Android 15 (API 35), which is where PdfRenderer
 * started exposing link annotations. Must be called on the thread that owns
 * the renderer, while the page is open.
 */
internal fun PdfRenderer.Page.readLinks(): List<PageLink> {
  // API 35 implies SDK extension 13; checked explicitly for lint.
  if (Build.VERSION.SDK_INT < 35 || SdkExtensions.getExtensionVersion(Build.VERSION_CODES.S) < 13) return emptyList()
  return gotoLinks.map { it.toPageLink() } + linkContents.map { it.toPageLink() }
}
