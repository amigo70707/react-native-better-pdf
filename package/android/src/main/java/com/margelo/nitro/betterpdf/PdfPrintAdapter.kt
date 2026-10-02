package com.margelo.nitro.betterpdf

import android.os.Bundle
import android.os.CancellationSignal
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import java.util.concurrent.Executors

/**
 * Hands an already laid-out PDF to the print framework: the document does
 * not depend on the paper or margins the user picks, so [onLayout] reports
 * the same [pageCount] every time and [onWrite] writes the whole document,
 * letting the spooler cut page ranges. Writing runs off the main thread;
 * every callback, both ways, is on the main thread.
 */
internal class PdfPrintAdapter(
  private val documentName: String,
  private val pageCount: Int,
  private val write: (ParcelFileDescriptor) -> Unit,
  private val finished: () -> Unit,
) : PrintDocumentAdapter() {
  private var hasLaidOut = false
  private val mainHandler = Handler(Looper.getMainLooper())
  private val writer = Executors.newSingleThreadExecutor { runnable ->
    Thread(runnable, "BetterPdf-print").apply { isDaemon = true }
  }

  override fun onLayout(
    oldAttributes: PrintAttributes?,
    newAttributes: PrintAttributes,
    cancellationSignal: CancellationSignal,
    callback: LayoutResultCallback,
    extras: Bundle?,
  ) {
    if (cancellationSignal.isCanceled) {
      callback.onLayoutCancelled()
      return
    }
    val info = PrintDocumentInfo.Builder(documentName)
      .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
      .setPageCount(pageCount)
      .build()
    callback.onLayoutFinished(info, !hasLaidOut)
    hasLaidOut = true
  }

  override fun onWrite(
    pages: Array<out PageRange>,
    destination: ParcelFileDescriptor,
    cancellationSignal: CancellationSignal,
    callback: WriteResultCallback,
  ) {
    writer.execute {
      val failure = try {
        write(destination)
        null
      } catch (error: Exception) {
        error
      }
      mainHandler.post {
        when {
          failure != null -> callback.onWriteFailed(failure.message)
          cancellationSignal.isCanceled -> callback.onWriteCancelled()
          else -> callback.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
        }
      }
    }
  }

  override fun onFinish() {
    writer.shutdown()
    finished()
  }
}
