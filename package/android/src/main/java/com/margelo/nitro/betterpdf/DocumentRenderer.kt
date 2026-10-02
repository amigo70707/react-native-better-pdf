package com.margelo.nitro.betterpdf

import android.os.Build
import android.os.ParcelFileDescriptor
import android.os.ext.SdkExtensions

/**
 * An open document: `PdfRenderer`, or on Android 12–14 with the updated PDF
 * module (SDK extension 13) `PdfRendererPreV` — the same renderer offered
 * as an update for older releases, which adds passwords, links, text and
 * annotation flags there.
 */
internal interface DocumentRenderer : AutoCloseable {
  val pageCount: Int

  fun openPage(index: Int): DocumentPage

  /**
   * Writes the document to [destination] without its password protection,
   * e.g. for the print spooler, which opens documents without a password.
   * Only renderers that can open password-protected documents can (see
   * [supportsPasswords]). Does not close [destination].
   */
  fun writeUnprotected(destination: ParcelFileDescriptor)

  companion object {
    /** Whether this device can open password-protected documents. */
    val supportsPasswords: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM || hasPreV

    /** Android 12–14 with SDK extension 13; API 35+ uses `PdfRenderer` itself. */
    private val hasPreV: Boolean
      get() = Build.VERSION.SDK_INT in Build.VERSION_CODES.S until Build.VERSION_CODES.VANILLA_ICE_CREAM &&
        SdkExtensions.getExtensionVersion(Build.VERSION_CODES.S) >= PRE_V_EXTENSION_VERSION

    /**
     * Opens [descriptor] (taking ownership) with the best renderer available.
     *
     * @throws SecurityException when a password is required or wrong (or
     *   the document is encrypted and passwords are unsupported here).
     * @throws java.io.IOException when the file is not a PDF.
     */
    fun open(descriptor: ParcelFileDescriptor, password: String?): DocumentRenderer {
      if (hasPreV) return PreVDocumentRenderer.open(descriptor, password)
      return PlatformDocumentRenderer.open(descriptor, password)
    }

    private const val PRE_V_EXTENSION_VERSION = 13
  }
}
