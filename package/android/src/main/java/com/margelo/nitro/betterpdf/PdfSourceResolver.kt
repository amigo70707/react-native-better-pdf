package com.margelo.nitro.betterpdf

import android.content.Context
import android.net.Uri
import android.os.ParcelFileDescriptor
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.util.concurrent.CountDownLatch

/**
 * Turns a [NativePdfSource] into a seekable [ParcelFileDescriptor] (plus its
 * file, when there is one; see [ResolvedSource]), which is
 * what [android.graphics.pdf.PdfRenderer] requires. Runs on the document's
 * worker thread; every method here may block.
 *
 * Remote documents come from the process-wide [DocumentCache].
 */
class PdfSourceResolver(private val context: Context) {
  @Volatile
  private var subscription: DocumentCache.Subscription? = null

  @Volatile
  private var cancelled = false
  private val remoteDone = CountDownLatch(1)

  /** Aborts an in-flight download; [open] then throws [InterruptedException]. Safe from any thread. */
  fun cancel() {
    cancelled = true
    subscription?.cancel()
    remoteDone.countDown()
  }

  /**
   * Opens [source]. For remote sources that are downloaded (not cached),
   * [onProgress] receives every chunk on a download thread; callers throttle.
   */
  fun open(
    source: NativePdfSource,
    onProgress: (loadedBytes: Long, totalBytes: Long?, isComplete: Boolean) -> Unit,
  ): ResolvedSource {
    val uri = source.uri
    if (uri.isBlank()) throw invalidSource(uri)
    if (uri.startsWith("/")) return openFile(File(uri))

    val parsed = Uri.parse(uri)
    return when (parsed.scheme?.lowercase()) {
      "file" -> openFile(File(parsed.path ?: throw invalidSource(uri)))
      "http", "https" -> openRemote(source, onProgress)
      "content" -> ResolvedSource(openContent(parsed), file = null)
      // Release builds resolve `require('./x.pdf')` to a bare resource name.
      null -> openRawResource(uri)
      else -> throw invalidSource(uri)
    }
  }

  private fun openFile(file: File): ResolvedSource {
    if (!file.isFile) {
      throw PdfFailure(PdfErrorCode.FILE_NOT_FOUND, "No file at ${file.path}")
    }
    try {
      return ResolvedSource(ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY), file.absoluteFile)
    } catch (e: FileNotFoundException) {
      throw PdfFailure(PdfErrorCode.FILE_NOT_FOUND, "Cannot open ${file.path}", cause = e)
    }
  }

  private fun openContent(uri: Uri): ParcelFileDescriptor {
    try {
      return context.contentResolver.openFileDescriptor(uri, "r")
        ?: throw PdfFailure(PdfErrorCode.FILE_NOT_FOUND, "No content at $uri")
    } catch (e: FileNotFoundException) {
      throw PdfFailure(PdfErrorCode.FILE_NOT_FOUND, "No content at $uri", cause = e)
    } catch (e: SecurityException) {
      throw PdfFailure(PdfErrorCode.FILE_NOT_FOUND, "No permission to read $uri", cause = e)
    }
  }

  private fun openRawResource(name: String): ResolvedSource {
    val resourceName = name.substringBeforeLast('.').replace(Regex("[^a-zA-Z0-9_]"), "_").lowercase()
    @Suppress("DiscouragedApi") // Resource names are only known at runtime here.
    val id = context.resources.getIdentifier(resourceName, "raw", context.packageName)
    if (id == 0) throw invalidSource(name)
    // Raw resources may be compressed inside the APK and therefore not
    // seekable, so copy once into the cache.
    val target = File(cacheDirectory(), "raw-$resourceName-${appVersionStamp()}.pdf")
    if (!target.isFile) {
      val partial = File(target.path + ".${System.nanoTime()}.part")
      context.resources.openRawResource(id).use { input ->
        partial.outputStream().use { output -> input.copyTo(output) }
      }
      if (!partial.renameTo(target)) throw IOException("Cannot move ${partial.path}")
    }
    return openFile(target)
  }

  /**
   * The copy of a remote [source], downloaded or revalidated when needed, or
   * an existing copy (marked stale) when the server cannot be reached.
   */
  private fun openRemote(
    source: NativePdfSource,
    onProgress: (loadedBytes: Long, totalBytes: Long?, isComplete: Boolean) -> Unit,
  ): ResolvedSource {
    // Written on a download thread before the latch opens; read after.
    var delivered: CachedCopy? = null
    var deliveredStale = false
    var deliveredFailure: PdfFailure? = null
    subscription = DocumentCache.shared(context).fetch(
      source,
      allowStale = true,
      object : DocumentCache.Listener {
        override fun onProgress(loadedBytes: Long, totalBytes: Long?, isComplete: Boolean) =
          onProgress(loadedBytes, totalBytes, isComplete)

        override fun onReady(copy: CachedCopy, isStale: Boolean) {
          delivered = copy
          deliveredStale = isStale
          remoteDone.countDown()
        }

        override fun onFailed(failure: PdfFailure) {
          deliveredFailure = failure
          remoteDone.countDown()
        }
      },
    )
    // cancel() may have run before the subscription existed.
    if (cancelled) subscription?.cancel()
    remoteDone.await()
    if (cancelled) throw InterruptedException()
    deliveredFailure?.let { throw it }
    val copy = delivered ?: throw IllegalStateException("The cache delivered no copy")
    return openFile(copy.file).copy(isStale = deliveredStale)
  }

  private fun cacheDirectory(): File =
    File(context.cacheDir, "BetterPdf").apply { mkdirs() }

  /** Invalidates copied raw resources when the app is updated. */
  private fun appVersionStamp(): Long =
    context.packageManager.getPackageInfo(context.packageName, 0).lastUpdateTime

  private fun invalidSource(uri: String) =
    PdfFailure(PdfErrorCode.INVALID_SOURCE, "Unsupported PDF source: \"$uri\"")
}
