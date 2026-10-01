package com.margelo.nitro.betterpdf

import android.graphics.Bitmap
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.view.View
import androidx.annotation.Keep
import com.facebook.proguard.annotations.DoNotStrip
import com.facebook.react.uimanager.ThemedReactContext
import com.margelo.nitro.betterpdf.outline.PdfFormatException
import com.margelo.nitro.core.Promise
import kotlin.math.roundToInt

/**
 * Android implementation of `BetterPdfView`.
 *
 * Threading: props, `afterUpdate` and `onDropView` arrive on the UI thread.
 * Methods called through `hybridRef` arrive on the JS thread and are posted to
 * the UI thread. Nitro dispatches callback invocations to the JS thread.
 */
@Keep
@DoNotStrip
class HybridBetterPdfView(
  val context: ThemedReactContext,
) : HybridBetterPdfViewSpec(),
  PdfDocumentSession.Listener,
  PdfDocumentView.Listener {
  private val documentView = PdfDocumentView(context).also { it.listener = this }
  private val searchController = SearchController(documentView) { event -> onSearchChange?.invoke(event) }
  override val view: View = documentView

  private val mainHandler = Handler(Looper.getMainLooper())
  private var session: PdfDocumentSession? = null
  /** The session once its document has loaded; read from the JS thread by [getOutline]. */
  @Volatile private var loadedSession: PdfDocumentSession? = null
  private var needsReload = false
  private var needsScaleUpdate = false
  private var needsLayoutModeUpdate = false

  // region Props

  override var source: NativePdfSource? = null
    set(value) {
      if (!value.isSameSource(field)) needsReload = true
      field = value
    }
  override var password: String? = null
    set(value) {
      if (value != field) needsReload = true
      field = value
    }
  override var initialPosition: PdfInitialPosition? = null
  override var minScale: Double? = null
    set(value) {
      field = value
      needsScaleUpdate = true
    }
  override var maxScale: Double? = null
    set(value) {
      field = value
      needsScaleUpdate = true
    }
  override var pageSpacing: Double? = null
    set(value) {
      field = value
      documentView.setPageSpacing(value)
    }
  override var horizontal: Boolean? = null
    set(value) {
      field = value
      needsLayoutModeUpdate = true
    }
  override var enablePaging: Boolean? = null
    set(value) {
      field = value
      needsLayoutModeUpdate = true
    }
  override var enableRTL: Boolean? = null
    set(value) {
      field = value
      needsLayoutModeUpdate = true
    }
  override var singlePage: Boolean? = null
    set(value) {
      field = value
      needsLayoutModeUpdate = true
    }
  override var fitPolicy: PdfFitPolicy? = null
    set(value) {
      field = value
      needsLayoutModeUpdate = true
    }
  override var enableDoubleTapZoom: Boolean? = null
    set(value) {
      field = value
      documentView.isDoubleTapZoomEnabled = value ?: true
    }
  override var scrollEnabled: Boolean? = null
    set(value) {
      field = value
      documentView.isUserScrollEnabled = value ?: true
    }
  override var pageAccessibilityLabel: String? = null
    set(value) {
      field = value
      documentView.pageAccessibilityLabel = value ?: "Page {page} of {pageCount}"
    }
  // processColor output: signed on Android, unsigned on iOS; either way the
  // low 32 bits are the ARGB value (Double.toInt() would clamp instead).
  override var searchHighlightColor: Double? = null
    set(value) {
      field = value
      documentView.searchMatchColor = value?.toLong()?.toInt()
    }
  override var searchCurrentHighlightColor: Double? = null
    set(value) {
      field = value
      documentView.searchCurrentColor = value?.toLong()?.toInt()
    }
  override var showsVerticalScrollIndicator: Boolean? = null
    set(value) {
      field = value
      documentView.isVerticalScrollBarEnabled = value ?: true
    }
  override var showsHorizontalScrollIndicator: Boolean? = null
    set(value) {
      field = value
      documentView.isHorizontalScrollBarEnabled = value ?: true
    }
  override var enableTextSelection: Boolean? = null
    set(value) {
      field = value
      documentView.isTextSelectionEnabled = value ?: true
    }
  override var onLoad: ((event: PdfLoadEvent) -> Unit)? = null
  override var onLoadProgress: ((event: PdfLoadProgressEvent) -> Unit)? = null
  override var onError: ((error: PdfError) -> Unit)? = null
  override var onPageChange: ((event: PdfPageChangeEvent) -> Unit)? = null
  override var onLinkPress: ((event: PdfLinkPressEvent) -> Unit)? = null
  override var onPageTap: ((event: PdfPageTapEvent) -> Unit)? = null
  override var onScaleChange: ((event: PdfScaleChangeEvent) -> Unit)? = null
  override var onViewportChange: ((event: PdfViewportChangeEvent) -> Unit)? = null
  override var onSearchChange: ((event: PdfSearchChangeEvent) -> Unit)? = null
  override var onTextSelectionChange: ((event: PdfTextSelectionChangeEvent) -> Unit)? = null

  // endregion

  override fun afterUpdate() {
    if (needsLayoutModeUpdate) {
      needsLayoutModeUpdate = false
      val horizontal = horizontal ?: false
      val paged = enablePaging ?: false
      val singlePage = singlePage ?: false
      // Default: fit width when scrolling vertically through pages, else whole pages.
      val fit = fitPolicy?.toLayoutFit()
        ?: if (horizontal || paged || singlePage) PdfDocumentLayout.Fit.PAGE else PdfDocumentLayout.Fit.WIDTH
      documentView.setLayoutMode(
        LayoutMode(horizontal, paged, fit, singlePage, isRightToLeft = enableRTL ?: false),
      )
    }
    if (needsScaleUpdate) {
      needsScaleUpdate = false
      documentView.setScaleLimits(minScale, maxScale)
    }
    if (needsReload) {
      needsReload = false
      reload()
    }
  }

  override fun onDropView() {
    // No events into JS for a dropped view (clearing the document would
    // report a cleared text selection), as on iOS.
    documentView.listener = null
    closeSession()
    // JS may keep this hybrid object alive (through `hybridRef`) until its GC
    // runs; hold no pixels in the meantime. (Measured on iOS: ~7.5 MB per
    // closed viewer when only the document was released.)
    documentView.bitmapPool.clear()
  }

  // region Methods (JS thread)

  override fun goToPage(page: Double, animated: Boolean) {
    if (!page.isFinite()) return
    mainHandler.post { documentView.goToPage(page.toInt(), animated) }
  }

  override fun zoomTo(scale: Double, animated: Boolean) {
    if (!scale.isFinite()) return
    mainHandler.post { documentView.zoomTo(scale.toFloat(), animated) }
  }

  override fun goToViewport(viewport: PdfViewport) {
    val values = listOf(viewport.page, viewport.x, viewport.y, viewport.scale)
    if (!values.all { it.isFinite() } || viewport.scale <= 0) return
    val point = PagePoint(viewport.page.toInt(), viewport.x.toFloat(), viewport.y.toFloat())
    mainHandler.post { documentView.goToViewport(point, viewport.scale.toFloat()) }
  }

  override fun search(query: String) {
    mainHandler.post { searchController.search(query) }
  }

  override fun searchNext() {
    mainHandler.post { searchController.next() }
  }

  override fun searchPrevious() {
    mainHandler.post { searchController.previous() }
  }

  override fun clearSearch() {
    mainHandler.post { searchController.clear() }
  }

  override fun getOutline(): Promise<Array<PdfOutlineEntry>> {
    val session = loadedSession ?: return Promise.rejected(IllegalStateException("No document is loaded"))
    return Promise.parallel {
      try {
        session.readOutline().map { it.toPdfOutlineEntry() }.toTypedArray()
      } catch (error: PdfFormatException) {
        throw IllegalStateException("The outline could not be read: ${error.message}", error)
      }
    }
  }

  override fun getPageSizes(): Promise<Array<PdfPageSize>> {
    val session = loadedSession ?: return Promise.rejected(IllegalStateException("No document is loaded"))
    val pageCount = documentView.pageCount
    return Promise.parallel {
      session.readPageSizes(pageCount)
        .map { PdfPageSize(it.widthPoints.toDouble(), it.heightPoints.toDouble()) }
        .toTypedArray()
    }
  }

  override fun renderPage(page: Double, options: PdfRenderPageOptions): Promise<PdfPageImage> {
    val session = loadedSession ?: return Promise.rejected(IllegalStateException("No document is loaded"))
    val pageCount = documentView.pageCount
    if (!page.isFinite() || page != Math.floor(page) || page < 0 || page >= pageCount) {
      return Promise.rejected(IllegalArgumentException("Page $page is outside the document (0–${pageCount - 1})"))
    }
    val width = options.width
    if (!width.isFinite() || width < 1 || width > MAX_RENDER_WIDTH) {
      return Promise.rejected(IllegalArgumentException("width must be 1–$MAX_RENDER_WIDTH pixels, got $width"))
    }
    val format = options.format ?: PdfImageFormat.PNG
    val quality = options.quality?.takeIf { it.isFinite() }?.coerceIn(0.0, 1.0) ?: DEFAULT_JPEG_QUALITY
    return Promise.parallel {
      val bitmap = session.renderPageImage(page.toInt(), width.roundToInt())
      try {
        val file = bitmap.writeToCache(context, format, quality)
        PdfPageImage(Uri.fromFile(file).toString(), bitmap.width.toDouble(), bitmap.height.toDouble())
      } finally {
        bitmap.recycle()
      }
    }
  }

  override fun getStats(): PdfViewStats {
    val session = session
    return PdfViewStats(
      pageCount = documentView.pageCount.toDouble(),
      residentPages = documentView.statResidentPages.toDouble(),
      cacheBytes = documentView.statCacheBytes.toDouble(),
      cacheBudgetBytes = documentView.cacheBudgetBytes.toDouble(),
      cacheHits = documentView.statHits.toDouble(),
      cacheMisses = documentView.statMisses.toDouble(),
      bitmapAllocations = documentView.bitmapPool.allocations.toDouble(),
      pagesRendered = (session?.pagesRendered?.get() ?: 0L).toDouble(),
      rendersCancelled = (session?.rendersCancelled ?: 0L).toDouble(),
      meanRenderMs = session?.meanRenderMs ?: -1.0,
    )
  }

  // endregion

  // region Loading

  private fun reload() {
    closeSession()
    val source = source ?: return
    session = PdfDocumentSession(context, source, password, documentView.bitmapPool, this).also { it.start() }
  }

  private fun closeSession() {
    searchController.onDocumentChanged(null)
    loadedSession = null
    session?.close()
    session = null
    documentView.clearDocument()
  }

  // endregion

  // region PdfDocumentSession.Listener (UI thread)

  override fun onOpened(pageSizes: List<PdfDocumentLayout.PageSize>, filePath: String?) {
    val session = session ?: return
    val position = initialPosition
    val initial = position?.page?.takeIf { it.isFinite() }?.toInt() ?: 0
    val scale = position?.scale?.takeIf { it.isFinite() && it > 0 }?.toFloat() ?: 1f
    documentView.setDocument(session, pageSizes, initial, scale, position?.pagePoint)
    loadedSession = session
    searchController.onDocumentChanged(session)
    val first = pageSizes.first()
    onLoad?.invoke(
      PdfLoadEvent(
        pageCount = pageSizes.size.toDouble(),
        firstPageWidth = first.widthPoints.toDouble(),
        firstPageHeight = first.heightPoints.toDouble(),
        filePath = filePath,
      ),
    )
  }

  override fun onLoadProgress(loadedBytes: Long, totalBytes: Long?) {
    onLoadProgress?.invoke(PdfLoadProgressEvent(loadedBytes.toDouble(), totalBytes?.toDouble()))
  }

  override fun onFailed(failure: PdfFailure) {
    onError?.invoke(failure.toPdfError())
  }

  override fun onPageMeasured(page: Int, size: PdfDocumentLayout.PageSize) {
    documentView.onPageMeasured(page, size)
  }

  override fun onPageText(page: Int, text: String?) {
    documentView.onPageText(page, text)
  }

  override fun onPageLinks(page: Int, links: List<PageLink>) {
    documentView.onPageLinks(page, links)
  }

  override fun onRendered(request: RenderRequest, bitmap: Bitmap) {
    documentView.onRendered(request, bitmap)
  }

  override fun onRenderFailed(request: RenderRequest, failure: PdfFailure) {
    documentView.onRenderFailed(request, failure)
  }

  override fun onTextSelected(query: TextSelectionQuery, selection: PageTextSelection?) {
    documentView.onTextSelected(query, selection)
  }

  // endregion

  // region PdfDocumentView.Listener (UI thread)

  override fun onPageChanged(page: Int, pageCount: Int) {
    onPageChange?.invoke(PdfPageChangeEvent(page.toDouble(), pageCount.toDouble()))
  }

  override fun onRenderFailed(failure: PdfFailure) {
    onError?.invoke(failure.toPdfError())
  }

  override fun onLinkPressed(url: String) {
    onLinkPress?.invoke(PdfLinkPressEvent(url))
  }

  override fun onScaleSettled(scale: Float) {
    onScaleChange?.invoke(PdfScaleChangeEvent(scale.toDouble()))
  }

  override fun onViewportSettled(viewport: DocumentViewport) {
    onViewportChange?.invoke(viewport.toPdfViewportChangeEvent())
  }

  override fun onTextSelectionChanged(text: String) {
    onTextSelectionChange?.invoke(PdfTextSelectionChangeEvent(text))
  }

  override fun onPageTapped(point: PagePoint) {
    onPageTap?.invoke(PdfPageTapEvent(point.page.toDouble(), point.x.toDouble(), point.y.toDouble()))
  }

  // endregion

  private companion object {
    const val MAX_RENDER_WIDTH = 4096.0
    const val DEFAULT_JPEG_QUALITY = 0.9
  }
}
