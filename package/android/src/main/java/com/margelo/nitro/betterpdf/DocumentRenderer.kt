package com.margelo.nitro.betterpdf

import android.os.Build
import android.os.ParcelFileDescriptor
import android.os.ext.SdkExtensions
import java.io.IOException

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

  /**
   * Whether the document has a form this renderer can fill in (Android 15+,
   * or 12–14 with SDK extension 13).
   */
  val canFillForm: Boolean

  /**
   * Writes the document, with the form as filled in on this renderer and
   * its password protection kept, to [destination]. Same renderers as
   * [writeUnprotected]. Does not close [destination].
   */
  fun writeProtected(destination: ParcelFileDescriptor)

  companion object {
    /** Whether this device can open password-protected documents. */
    val supportsPasswords: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM || hasPreV

    /** Whether this device can fill forms in and write documents: the same renderers. */
    val supportsForms: Boolean get() = supportsPasswords

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

    /**
     * Opens a document as the view and `PdfDocument` report it: like [open],
     * but failures are [PdfFailure]s with the codes apps match on, and
     * [descriptor] is closed on failure.
     */
    fun openOrFail(descriptor: ParcelFileDescriptor, password: String?): DocumentRenderer {
      try {
        return open(descriptor, password)
      } catch (error: SecurityException) {
        descriptor.closeQuietly()
        throw when {
          !supportsPasswords ->
            PdfFailure(
              PdfErrorCode.UNSUPPORTED_PDF,
              "Password-protected PDFs require Android 15, or Android 12–14 with the updated PDF module (SDK extension 13)",
              cause = error,
            )
          password.isNullOrEmpty() ->
            PdfFailure(PdfErrorCode.PASSWORD_REQUIRED, "This PDF is password protected", cause = error)
          else ->
            PdfFailure(PdfErrorCode.INVALID_PASSWORD, "The password is incorrect", cause = error)
        }
      } catch (error: IOException) {
        descriptor.closeQuietly()
        throw PdfFailure(PdfErrorCode.INVALID_PDF, "The file is not a readable PDF document", cause = error)
      } catch (error: IllegalArgumentException) {
        descriptor.closeQuietly()
        throw PdfFailure(PdfErrorCode.INVALID_SOURCE, "The PDF source is not seekable", cause = error)
      }
    }

    private const val PRE_V_EXTENSION_VERSION = 13
  }
}
