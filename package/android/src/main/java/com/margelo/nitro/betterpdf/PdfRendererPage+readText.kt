package com.margelo.nitro.betterpdf

import android.graphics.pdf.PdfRenderer
import android.os.Build
import android.os.ext.SdkExtensions

/**
 * The page's text in reading order, or null before Android 15 (API 35),
 * where PdfRenderer started exposing it. Must be called on the thread that
 * owns the renderer, while the page is open.
 */
internal fun PdfRenderer.Page.readText(): String? {
  // API 35 implies SDK extension 13; checked explicitly for lint.
  if (Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM ||
    SdkExtensions.getExtensionVersion(Build.VERSION_CODES.S) < 13
  ) {
    return null
  }
  return joinPageText(textContents.map { it.text })
}
