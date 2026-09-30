package com.margelo.nitro.betterpdf

import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.pdf.PdfRendererPreV
import android.graphics.pdf.RenderParams
import android.os.Build
import androidx.annotation.RequiresExtension

@RequiresExtension(extension = Build.VERSION_CODES.S, version = 13)
internal class PreVDocumentPage(private val page: PdfRendererPreV.Page) : DocumentPage {
  override val width: Int get() = page.width
  override val height: Int get() = page.height

  override fun renderForDisplay(target: Bitmap, clip: Rect?, matrix: Matrix) {
    // The same annotation flags as PdfRenderer on API 35–36.
    val flags = RenderParams.FLAG_RENDER_HIGHLIGHT_ANNOTATIONS or RenderParams.FLAG_RENDER_TEXT_ANNOTATIONS
    page.render(target, clip, matrix, RenderParams.Builder(RenderParams.RENDER_MODE_FOR_DISPLAY).setRenderFlags(flags).build())
  }

  override fun readLinks(): List<PageLink> =
    page.gotoLinks.map { it.toPageLink() } + page.linkContents.map { it.toPageLink() }

  override fun readText(): String = joinPageText(page.textContents.map { it.text })

  override fun searchText(query: String): List<List<PageRect>> =
    page.searchText(query).map { match -> match.bounds.map { it.toPageRect() } }

  override fun selectText(query: TextSelectionQuery): PageTextSelection? {
    val selection = page.selectContent(query.start.toSelectionBoundary(), query.stop.toSelectionBoundary()) ?: return null
    return selection.toPageTextSelection(query.page)
  }

  override fun close() = page.close()
}
