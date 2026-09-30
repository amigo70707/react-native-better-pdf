package com.margelo.nitro.betterpdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Rect
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.os.Trace
import com.margelo.nitro.betterpdf.outline.FileChannelByteSource
import com.margelo.nitro.betterpdf.outline.OutlineEntry
import com.margelo.nitro.betterpdf.outline.PdfDocumentReader
import com.margelo.nitro.betterpdf.outline.PdfOutlineReader
import java.io.IOException
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * One open document: resolves the source, owns the [DocumentRenderer] and its file
 * descriptor, and renders pages (and resolves text selections) on a single
 * dedicated thread.
 *
 * Why a single thread: `PdfRenderer` allows only one open page at a time, so
 * parallel rendering of one document is impossible. Why a replaceable queue
 * instead of an executor: once `Page.render` starts it cannot be interrupted,
 * so the only effective cancellation is to drop work *before* it starts. The
 * UI thread re-submits the full wanted list on every viewport change and
 * anything no longer wanted is discarded.
 *
 * Lifecycle: every native resource (renderer, FD, open page) is created and
 * closed on the worker thread. [close] only signals; the worker finishes the
 * render in progress (bounded by viewport-sized bitmaps), then closes the
 * renderer, which closes the FD. Listener callbacks are delivered on the main
 * thread and never after [close].
 */
class PdfDocumentSession(
  context: Context,
  private val source: NativePdfSource,
  private val password: String?,
  private val bitmapPool: BitmapPool,
  private val listener: Listener,
) {
  interface Listener {
    /**
     * [pageSizes] holds the real size of page 0 and an estimate (page 0's
     * size) for every other page; see [onPageMeasured].
     */
    /** [filePath]: the local file being displayed, when there is one. */
    fun onOpened(pageSizes: List<PdfDocumentLayout.PageSize>, filePath: String?)

    /** A page was opened for the first time and its size differs from the estimate. */
    fun onPageMeasured(page: Int, size: PdfDocumentLayout.PageSize)

    /** A page was opened for the first time and has links. */
    fun onPageLinks(page: Int, links: List<PageLink>)

    fun onFailed(failure: PdfFailure)

    /** Download progress of a remote source, throttled by [ProgressThrottle]. */
    fun onLoadProgress(loadedBytes: Long, totalBytes: Long?)
    fun onRendered(request: RenderRequest, bitmap: Bitmap)
    fun onRenderFailed(request: RenderRequest, failure: PdfFailure)

    /** [selection] is null where [query] selects no text, or when the page could not be read. */
    fun onTextSelected(query: TextSelectionQuery, selection: PageTextSelection?)

    /** [text] is null where the renderer exposes no text (before Android 15) or it could not be read. */
    fun onPageText(page: Int, text: String?)
  }

  private val resolver = PdfSourceResolver(context.applicationContext)
  private val mainHandler = Handler(Looper.getMainLooper())
  private val queue = RenderQueue<DocumentWork>()
  private val closed: Boolean get() = queue.isClosed

  val rendersCancelled: Long get() = queue.cancelled
  val pagesRendered = AtomicLong()
  val rendererRecycles = AtomicLong()
  private val renderNanos = AtomicLong()

  val meanRenderMs: Double
    get() {
      val count = pagesRendered.get()
      return if (count == 0L) -1.0 else renderNanos.get() / count / 1_000_000.0
    }

  private val thread = Thread(::run, "BetterPdf-render").apply { isDaemon = true }

  fun start() = thread.start()

  /**
   * Replaces the queue with [requests] (highest priority first). Queued
   * requests that are no longer wanted are dropped and counted as cancelled.
   */
  fun submit(requests: List<DocumentWork>) = queue.submit(requests)

  /**
   * Resolves [query] before any queued render, replacing a query that has
   * not started (a handle drag sends one per move). The result arrives
   * through [Listener.onTextSelected].
   */
  fun selectText(query: TextSelectionQuery) = queue.submitUrgent(query)

  /**
   * The document outline, parsed from a duplicate of the file descriptor so
   * rendering is never blocked. Blocking: call off the main thread. Cached.
   *
   * @throws IllegalStateException when the document is not open.
   * @throws com.margelo.nitro.betterpdf.outline.PdfFormatException when the
   *   file cannot be parsed (or is encrypted).
   */
  fun readOutline(): List<OutlineEntry> {
    outline?.let { return it }
    // Duplicated under the lock so the worker cannot close (and the OS reuse)
    // the descriptor number in between; the duplicate is ours to close.
    val descriptor = synchronized(descriptorLock) { master?.dup() }
      ?: throw IllegalStateException("The document is not open")
    val entries = ParcelFileDescriptor.AutoCloseInputStream(descriptor).channel.use { channel ->
      PdfOutlineReader(PdfDocumentReader(FileChannelByteSource(channel))).read()
    }
    outline = entries
    return entries
  }

  fun close() {
    if (closed) return
    queue.close()
    resolver.cancel()
    mainHandler.removeCallbacksAndMessages(null)
  }

  // region Worker thread

  /*
   * Worker-thread state. PDFium keeps every object it parsed for a page (for
   * scanned documents: the compressed image data) for the lifetime of the
   * document, even after the page is closed. Measured: opening all pages of a
   * 395 MB, 1000-page scan retained 400 MB of native heap. Hence:
   * - page sizes are not measured up front; each page is measured when it is
   *   first opened for rendering, and
   * - the renderer is re-created from a duplicate of the original file
   *   descriptor every [RECYCLE_AFTER_PAGES] page opens, which releases
   *   everything PDFium cached. Creating a renderer is cheap (measured 3–27 ms,
   *   no retained memory) because it does not touch pages.
   */
  private var master: ParcelFileDescriptor? = null
  /** Guards [master] against [readOutline] duplicating it while it closes. */
  private val descriptorLock = Any()
  @Volatile private var outline: List<OutlineEntry>? = null
  private var renderer: DocumentRenderer? = null
  private var pagesOpenedByRenderer = 0
  private var filePath: String? = null
  private lateinit var knownSizes: Array<PdfDocumentLayout.PageSize>
  private lateinit var measured: BooleanArray
  private lateinit var linksRead: BooleanArray

  private fun run() {
    try {
      Trace.beginSection("BetterPdf#open")
      val firstPage = try {
        open()
      } finally {
        Trace.endSection()
      }
      if (closed) return
      val sizes = List(knownSizes.size) { firstPage }
      val path = filePath
      post { listener.onOpened(sizes, path) }
      renderLoop()
    } catch (_: InterruptedException) {
      // Closed while resolving the source.
    } catch (failure: PdfFailure) {
      post { listener.onFailed(failure) }
    } catch (error: OutOfMemoryError) {
      post { listener.onFailed(PdfFailure(PdfErrorCode.OUT_OF_MEMORY, "Out of memory while opening the PDF", cause = error)) }
    } catch (error: RuntimeException) {
      // Malformed documents can surface as arbitrary runtime exceptions from
      // the platform renderer; never let them crash the app.
      post { listener.onFailed(PdfFailure(PdfErrorCode.INVALID_PDF, "The PDF could not be opened", cause = error)) }
    } finally {
      closeRenderer()
      synchronized(descriptorLock) {
        master?.closeQuietly()
        master = null
      }
    }
  }

  /** Opens the document and returns the size of page 0. */
  private fun open(): PdfDocumentLayout.PageSize {
    val progressThrottle = ProgressThrottle()
    val resolved = resolver.open(source) { loadedBytes, totalBytes, isComplete ->
      if (progressThrottle.shouldEmit(SystemClock.uptimeMillis(), loadedBytes, isComplete)) {
        post { listener.onLoadProgress(loadedBytes, totalBytes) }
      }
    }
    val descriptor = resolved.descriptor
    filePath = resolved.file?.path
    synchronized(descriptorLock) { master = descriptor }
    if (closed) throw InterruptedException()
    val renderer = createRenderer(descriptor.dup())
    this.renderer = renderer
    val pageCount = renderer.pageCount
    if (pageCount <= 0) throw PdfFailure(PdfErrorCode.INVALID_PDF, "The PDF has no pages")
    val firstPage = renderer.openPage(0).use { page -> PdfDocumentLayout.PageSize(page.width.toFloat(), page.height.toFloat()) }
    pagesOpenedByRenderer = 1
    knownSizes = Array(pageCount) { firstPage }
    measured = BooleanArray(pageCount).also { it[0] = true }
    linksRead = BooleanArray(pageCount)
    return firstPage
  }

  private fun createRenderer(descriptor: ParcelFileDescriptor): DocumentRenderer {
    try {
      return DocumentRenderer.open(descriptor, password)
    } catch (error: SecurityException) {
      descriptor.closeQuietly()
      throw when {
        !DocumentRenderer.supportsPasswords ->
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

  /** The renderer to open the next page with, re-created when PDFium's cache has grown. */
  private fun rendererForNextPage(): DocumentRenderer {
    val current = renderer
    if (current != null && pagesOpenedByRenderer < RECYCLE_AFTER_PAGES) return current
    Trace.beginSection("BetterPdf#recycleRenderer")
    try {
      closeRenderer()
      val descriptor = master ?: throw IllegalStateException("Document closed")
      return createRenderer(descriptor.dup()).also {
        renderer = it
        pagesOpenedByRenderer = 0
        rendererRecycles.incrementAndGet()
      }
    } finally {
      Trace.endSection()
    }
  }

  private fun closeRenderer() {
    try {
      renderer?.close()
    } catch (_: RuntimeException) {
    }
    renderer = null
  }

  private fun renderLoop() {
    while (true) {
      val work = queue.take() ?: return
      try {
        when (work) {
          is RenderRequest -> render(work)
          is TextSelectionQuery -> resolveSelection(work)
          is PageTextQuery -> readPageText(work)
        }
      } finally {
        queue.finish()
      }
    }
  }

  private fun resolveSelection(query: TextSelectionQuery) {
    Trace.beginSection("BetterPdf#selectText")
    val selection = try {
      // Not counted towards RECYCLE_AFTER_PAGES: a handle drag re-opens the
      // same, already parsed page for every move (~100 per second).
      val page = rendererForNextPage().openPage(query.page)
      page.use {
        recordSize(query.page, PdfDocumentLayout.PageSize(page.width.toFloat(), page.height.toFloat()))
        recordLinks(query.page, page)
        page.selectText(query)
      }
    } catch (_: RuntimeException) {
      // A page whose text cannot be read has nothing to select.
      null
    } finally {
      Trace.endSection()
    }
    post { listener.onTextSelected(query, selection) }
  }

  private fun readPageText(query: PageTextQuery) {
    val text = try {
      rendererForNextPage().openPage(query.page).use { page ->
        recordSize(query.page, PdfDocumentLayout.PageSize(page.width.toFloat(), page.height.toFloat()))
        page.readText()
      }
    } catch (_: RuntimeException) {
      // A page whose text cannot be read is announced by its label only.
      null
    }
    post { listener.onPageText(query.page, text) }
  }

  private fun render(request: RenderRequest) {
    val start = SystemClock.elapsedRealtimeNanos()
    var bitmap: Bitmap? = null
    Trace.beginSection("BetterPdf#renderPage")
    try {
      val page = rendererForNextPage().openPage(request.page)
      pagesOpenedByRenderer++
      page.use {
        val size = PdfDocumentLayout.PageSize(page.width.toFloat(), page.height.toFloat())
        recordSize(request.page, size)
        recordLinks(request.page, page)
        val target = when (request) {
          is RenderRequest.Base -> {
            val (width, height) = baseBitmapSize(request, size)
            bitmapPool.acquire(width, height)
          }
          is RenderRequest.Tile -> bitmapPool.acquire(request.bitmapWidth, request.bitmapHeight)
        }
        bitmap = target
        target.eraseColor(Color.WHITE)
        when (request) {
          is RenderRequest.Base -> {
            val matrix = Matrix().apply { setScale(target.width / size.widthPoints, target.height / size.heightPoints) }
            page.renderForDisplay(target, null, matrix)
          }
          is RenderRequest.Tile -> {
            val matrix = Matrix().apply {
              setScale(request.scale, request.scale)
              postTranslate(request.dx, request.dy)
            }
            val clip = Rect(0, 0, request.usedWidth, request.usedHeight)
            page.renderForDisplay(target, clip, matrix)
          }
        }
        pagesRendered.incrementAndGet()
        renderNanos.addAndGet(SystemClock.elapsedRealtimeNanos() - start)
        bitmap = null
        post(onDropped = { bitmapPool.release(target) }) { listener.onRendered(request, target) }
      }
    } catch (error: OutOfMemoryError) {
      post { listener.onRenderFailed(request, PdfFailure(PdfErrorCode.OUT_OF_MEMORY, "Out of memory rendering page ${request.page}", page = request.page, cause = error)) }
    } catch (error: RuntimeException) {
      post { listener.onRenderFailed(request, PdfFailure(PdfErrorCode.PAGE_RENDER_FAILED, "Rendering page ${request.page} failed", page = request.page, cause = error)) }
    } finally {
      bitmap?.let(bitmapPool::release)
      Trace.endSection()
    }
  }

  private fun recordSize(page: Int, size: PdfDocumentLayout.PageSize) {
    if (measured[page]) return
    measured[page] = true
    if (knownSizes[page] == size) return
    knownSizes[page] = size
    post { listener.onPageMeasured(page, size) }
  }

  /**
   * Reads a page's links the first time it is opened, while it is open anyway
   * for rendering. A malformed link annotation must not cost the page its
   * pixels, so read failures leave the page without links (its taps are then
   * reported as page taps).
   */
  private fun recordLinks(page: Int, openPage: DocumentPage) {
    if (linksRead[page]) return
    linksRead[page] = true
    val links = try {
      openPage.readLinks()
    } catch (_: RuntimeException) {
      emptyList()
    }
    if (links.isNotEmpty()) post { listener.onPageLinks(page, links) }
  }

  /** Fit-width bitmap at the page's real aspect ratio, capped at the request's pixel budget. */
  private fun baseBitmapSize(request: RenderRequest.Base, size: PdfDocumentLayout.PageSize): Pair<Int, Int> {
    val safe = size.sanitized()
    var width = request.bitmapWidth.toDouble()
    var height = width * safe.heightPoints / safe.widthPoints
    val pixels = width * height
    if (pixels > request.maxPixels) {
      val factor = sqrt(request.maxPixels / pixels)
      width *= factor
      height *= factor
    }
    return max(1, width.roundToInt()) to max(1, height.roundToInt())
  }

  /** Runs [block] on the main thread unless the session was closed meanwhile. */
  private inline fun post(crossinline onDropped: () -> Unit = {}, crossinline block: () -> Unit) {
    mainHandler.post {
      if (closed) onDropped() else block()
    }
  }

  private fun ParcelFileDescriptor.closeQuietly() {
    try {
      close()
    } catch (_: IOException) {
    }
  }

  // endregion

  private companion object {
    /**
     * Page opens per renderer before it is re-created. Bounds PDFium's
     * per-document object cache to roughly this many pages' worth of parsed
     * objects. Not tuned: the value is a bound whose effect (flat memory over
     * a 5-minute scroll soak) is verified in docs/performance/results.md.
     */
    const val RECYCLE_AFTER_PAGES = 48
  }
}
