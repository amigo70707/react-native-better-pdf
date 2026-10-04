package com.margelo.nitro.betterpdf

import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.pdf.PdfRenderer
import android.os.Build

internal class PlatformDocumentPage(private val page: PdfRenderer.Page) : DocumentPage {
  override val width: Int get() = page.width
  override val height: Int get() = page.height

  override fun renderForDisplay(target: Bitmap, clip: Rect?, matrix: Matrix) = page.renderForDisplay(target, clip, matrix)

  override fun readLinks(): List<PageLink> = page.readLinks()

  override fun readText(): String? = page.readText()

  override fun searchText(query: String): List<List<PageRect>>? = page.findText(query)

  override fun selectText(query: TextSelectionQuery): PageTextSelection? = page.selectText(query)

  override fun formWidgets(): List<FormWidget> {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM) return emptyList()
    return page.formWidgetInfos.map { it.toFormWidget() }
  }

  override fun applyFormEdit(edit: FormEdit) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM) {
      throw UnsupportedOperationException("Filling forms requires Android 15")
    }
    page.applyEdit(edit.toRecord())
  }

  override fun close() = page.close()
}
