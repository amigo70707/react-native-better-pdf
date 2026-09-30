package com.margelo.nitro.betterpdf

import android.graphics.pdf.LoadParams
import android.graphics.pdf.PdfRendererPreV
import android.os.Build
import android.os.ParcelFileDescriptor
import androidx.annotation.RequiresExtension

/** [DocumentRenderer] over `PdfRendererPreV` (Android 12–14, SDK extension 13). */
@RequiresExtension(extension = Build.VERSION_CODES.S, version = 13)
internal class PreVDocumentRenderer private constructor(private val renderer: PdfRendererPreV) : DocumentRenderer {
  override val pageCount: Int get() = renderer.pageCount

  override fun openPage(index: Int): DocumentPage = PreVDocumentPage(renderer.openPage(index))

  override fun close() = renderer.close()

  companion object {
    fun open(descriptor: ParcelFileDescriptor, password: String?): PreVDocumentRenderer {
      val renderer = if (password.isNullOrEmpty()) {
        PdfRendererPreV(descriptor)
      } else {
        PdfRendererPreV(descriptor, LoadParams.Builder().setPassword(password).build())
      }
      return PreVDocumentRenderer(renderer)
    }
  }
}
