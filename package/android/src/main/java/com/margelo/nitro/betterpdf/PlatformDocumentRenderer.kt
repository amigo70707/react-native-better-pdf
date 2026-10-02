package com.margelo.nitro.betterpdf

import android.graphics.pdf.LoadParams
import android.graphics.pdf.PdfRenderer
import android.os.Build
import android.os.ParcelFileDescriptor

/** [DocumentRenderer] over the platform `PdfRenderer` (passwords from API 35). */
internal class PlatformDocumentRenderer private constructor(private val renderer: PdfRenderer) : DocumentRenderer {
  override val pageCount: Int get() = renderer.pageCount

  override fun openPage(index: Int): DocumentPage = PlatformDocumentPage(renderer.openPage(index))

  override fun writeUnprotected(destination: ParcelFileDescriptor) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM) {
      throw UnsupportedOperationException("Writing a document requires Android 15")
    }
    renderer.write(destination, true)
  }

  override fun close() = renderer.close()

  companion object {
    fun open(descriptor: ParcelFileDescriptor, password: String?): PlatformDocumentRenderer {
      val renderer = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM && !password.isNullOrEmpty()) {
        PdfRenderer(descriptor, LoadParams.Builder().setPassword(password).build())
      } else {
        PdfRenderer(descriptor)
      }
      return PlatformDocumentRenderer(renderer)
    }
  }
}
