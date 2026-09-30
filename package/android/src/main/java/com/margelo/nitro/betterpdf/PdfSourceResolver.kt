package com.margelo.nitro.betterpdf

import android.content.Context
import android.net.Uri
import android.os.ParcelFileDescriptor
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap

/**
 * Turns a [NativePdfSource] into a seekable [ParcelFileDescriptor] (plus its
 * file, when there is one; see [ResolvedSource]), which is
 * what [android.graphics.pdf.PdfRenderer] requires. Runs on the document's
 * worker thread; every method here may block.
 *
 * Remote documents are downloaded into `cacheDir/BetterPdf/<sha256>.pdf` and
 * reused for the rest of the process lifetime.
 */
class PdfSourceResolver(private val context: Context) {
  @Volatile
  private var connection: HttpURLConnection? = null

  @Volatile
  private var cancelled = false

  /** Aborts an in-flight download. Safe from any thread. */
  fun cancel() {
    cancelled = true
    connection?.disconnect()
  }

  /**
   * Opens [source]. For remote sources that are downloaded (not cached),
   * [onProgress] receives every chunk on the calling thread; callers throttle.
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
      "http", "https" -> openFile(download(uri, source.headers.orEmpty(), onProgress))
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

  private fun download(
    url: String,
    headers: Map<String, String>,
    onProgress: (loadedBytes: Long, totalBytes: Long?, isComplete: Boolean) -> Unit,
  ): File {
    val key = sha256(url + headers.toSortedMap().entries.joinToString("") { "\n${it.key}:${it.value}" })
    downloaded[key]?.let { if (it.isFile) return it }

    val target = File(cacheDirectory(), "$key.pdf")
    val partial = File(cacheDirectory(), "$key.${System.nanoTime()}.part")
    try {
      val connection = (URL(url).openConnection() as HttpURLConnection).apply {
        connectTimeout = 30_000
        readTimeout = 30_000
        instanceFollowRedirects = true
        headers.forEach { (name, value) -> setRequestProperty(name, value) }
      }
      this.connection = connection
      if (cancelled) throw InterruptedException()
      try {
        val status = connection.responseCode
        if (status !in 200..299) {
          throw PdfFailure(
            PdfErrorCode.HTTP_ERROR,
            "Downloading $url failed with HTTP $status",
            httpStatus = status,
          )
        }
        // -1 when unknown; also cleared when the response is transparently
        // decompressed, so the byte count never exceeds a reported total.
        val totalBytes = connection.contentLengthLong.takeIf { it >= 0 }
        connection.inputStream.use { input ->
          partial.outputStream().use { output ->
            val buffer = ByteArray(DOWNLOAD_BUFFER_BYTES)
            var loadedBytes = 0L
            while (true) {
              if (cancelled) throw InterruptedException()
              val read = input.read(buffer)
              if (read < 0) break
              output.write(buffer, 0, read)
              loadedBytes += read
              onProgress(loadedBytes, totalBytes?.takeIf { it >= loadedBytes }, false)
            }
            onProgress(loadedBytes, totalBytes?.takeIf { it >= loadedBytes }, true)
          }
        }
      } finally {
        connection.disconnect()
        this.connection = null
      }
      if (!partial.renameTo(target)) throw IOException("Cannot move ${partial.path}")
      downloaded[key] = target
      return target
    } catch (e: PdfFailure) {
      partial.delete()
      throw e
    } catch (e: InterruptedException) {
      // Cancelled mid-download: never leave a partial file in the cache.
      partial.delete()
      throw e
    } catch (e: IOException) {
      partial.delete()
      if (cancelled) throw InterruptedException()
      throw PdfFailure(PdfErrorCode.NETWORK_ERROR, "Downloading $url failed", cause = e)
    }
  }

  private fun cacheDirectory(): File =
    File(context.cacheDir, "BetterPdf").apply { mkdirs() }

  /** Invalidates copied raw resources when the app is updated. */
  private fun appVersionStamp(): Long =
    context.packageManager.getPackageInfo(context.packageName, 0).lastUpdateTime

  private fun invalidSource(uri: String) =
    PdfFailure(PdfErrorCode.INVALID_SOURCE, "Unsupported PDF source: \"$uri\"")

  companion object {
    private const val DOWNLOAD_BUFFER_BYTES = 64 * 1024

    /** Downloads completed in this process, by cache key. */
    private val downloaded = ConcurrentHashMap<String, File>()

    private fun sha256(value: String): String =
      MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray())
        .joinToString("") { "%02x".format(it) }
  }
}
