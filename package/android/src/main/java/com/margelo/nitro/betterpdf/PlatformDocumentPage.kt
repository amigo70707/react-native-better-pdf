package com.margelo.nitro.betterpdf

import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.pdf.PdfRenderer

internal class PlatformDocumentPage(private val page: PdfRenderer.Page) : DocumentPage {
  override val width: Int get() = page.width
  override val height: Int get() = page.height

  override fun renderForDisplay(target: Bitmap, clip: Rect?, matrix: Matrix) = page.renderForDisplay(target, clip, matrix)

  override fun readLinks(): List<PageLink> = page.readLinks()

  override fun selectText(query: TextSelectionQuery): PageTextSelection? = page.selectText(query)

  override fun close() = page.close()
}
