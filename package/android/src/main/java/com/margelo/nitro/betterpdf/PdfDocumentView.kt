package com.margelo.nitro.betterpdf

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.ComponentCallbacks2
import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.os.Build
import android.os.SystemClock
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.ViewConfiguration
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.widget.OverScroller
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * The Android PDF viewer: one `View` that draws the visible pages of a
 * document directly onto its canvas.
 *
 * - **Virtualization**: there are no child views. Each frame draws only the
 *   pages intersecting the viewport ([PdfDocumentLayout.visibleRange]).
 * - **Base layer**: every page is rendered once at fit-width resolution and
 *   kept in a byte-budgeted LRU ([LruByteCache]); evicted bitmaps go back to a
 *   [BitmapPool] for reuse.
 * - **Zoom tiles**: when zoomed beyond the base resolution, each visible page
 *   gets one viewport-sized, full-resolution crop rendered after the gesture
 *   settles. Memory stays O(viewport) no matter the zoom level.
 * - **Prefetch**: [PrefetchPlanner] orders visible pages, then pages ahead in
 *   the scroll direction, then behind. The queue is replaced on every change.
 * - **Gestures**: scroll, fling, pinch and double-tap are handled here, on the
 *   UI thread, with no JS involvement. Long press and selection handles
 *   belong to [TextSelectionController].
 * - **Scroll indicators**: the platform's own fading scroll bars, fed from the
 *   viewport state via `computeVertical/HorizontalScroll*`. The ScrollView
 *   style is the only public way to have `View` initialize its scroll bar
 *   drawables for a view created in code (`initializeScrollbars` was removed
 *   from the SDK).
 *
 * Threading: everything in this class runs on the UI thread except the
 * `@Volatile` stats fields, which the JS thread reads.
 */
@SuppressLint("ViewConstructor")
class PdfDocumentView(context: Context) : View(context, null, android.R.attr.scrollViewStyle) {
  interface Listener {
    fun onPageChanged(page: Int, pageCount: Int)
    fun onRenderFailed(failure: PdfFailure)
    fun onLinkPressed(url: String)
    fun onPageTapped(point: PagePoint)

    /** The zoom settled at a new [scale] (relative to the fitted page). */
    fun onScaleSettled(scale: Float)

    /** The text selection settled at new [text]; empty when cleared. */
    fun onTextSelectionChanged(text: String)
  }

  var listener: Listener? = null

  // region Document state

  private var session: PdfDocumentSession? = null
  private var pageSizes: MutableList<PdfDocumentLayout.PageSize> = ArrayList()
  /** A measured page size arrived while the viewport was moving. */
  private var relayoutPending = false
  private var layout: PdfDocumentLayout? = null
  private var pendingInitialPage: Int? = null
  private val failedPages = HashSet<Int>()
  /** Links of pages rendered so far that have any (see [PdfDocumentSession.Listener.onPageLinks]). */
  private val pageLinks = HashMap<Int, List<PageLink>>()
  private var reportedOutOfMemory = false

  val pageCount: Int get() = pageSizes.size

  // endregion

  // region Viewport state (zoomed content pixels)

  private var scale = 1f
  private var offsetX = 0f
  private var offsetY = 0f
  private var minScale = 1f
  private var maxScale = 4f
  private var spacingPx = 8f * resources.displayMetrics.density
  private var direction = PrefetchPlanner.Direction.FORWARD
  private var axis = PdfDocumentLayout.Axis.VERTICAL
  private var isPaged = false
  private var fit = PdfDocumentLayout.Fit.WIDTH
  /** Single-page mode: only [shownPage] is laid out for scrolling and rendered; touches pass through. */
  private var isSinglePage = false
  private var isRightToLeft = false
  /** Last zoom sent through [Listener.onScaleSettled]. */
  private var lastReportedScale = 1f

  /**
   * Whether dragging and flinging scroll the document. When false the drag
   * is left to the parents (a surrounding ScrollView scrolls instead); pinch,
   * double-tap, taps and programmatic navigation keep working.
   */
  var isUserScrollEnabled = true

  /**
   * Whether a double tap toggles zoom. When false, double taps are not
   * detected at all: every tap is handled on finger up, without waiting for
   * the double-tap timeout.
   */
  var isDoubleTapZoomEnabled = true
    set(value) {
      field = value
      gestureDetector.setOnDoubleTapListener(if (value) gestureListener else null)
    }

  /**
   * Whether a long press selects text. PdfRenderer exposes page text from
   * Android 15 (API 35); earlier versions keep long presses inert. The
   * detector's long press stays on either way: turning it off would make a
   * long press end as a tap.
   */
  var isTextSelectionEnabled = true
    set(value) {
      field = value
      textSelection.isEnabled = value && Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM
    }
  private var shownPage = 0
  /** Set when a fling in paged mode was turned into a page snap. */
  private var pagingFlingHandled = false
  private var currentPage = -1
  private var previousVisible: IntRange = IntRange.EMPTY
  private var lastSubmitted: List<RenderRequest> = emptyList()

  // endregion

  // region Memory

  val bitmapPool = BitmapPool(maxBytes = MIN_POOL_BYTES)
  private val cache = LruByteCache<Int, Bitmap>(
    budgetBytes = defaultCacheBudget(),
    sizeOf = { it.allocationByteCount.toLong() },
    onEvict = { _, bitmap -> releaseAfterFrame(bitmap) },
  )

  private class Tile(val bitmap: Bitmap, val contentRect: RectF, val usedWidth: Int, val usedHeight: Int)

  private val tiles = HashMap<Int, Tile>()
  /** After memory pressure, render only visible pages until this uptime. */
  private var constrainedUntilMs = 0L
  private var tileGeneration = 0

  // endregion

  // region Stats (written on UI thread, read from JS thread)

  @Volatile var statResidentPages = 0
    private set
  @Volatile var statCacheBytes = 0L
    private set
  @Volatile var statHits = 0L
    private set
  @Volatile var statMisses = 0L
    private set
  val cacheBudgetBytes: Long get() = cache.budgetBytes

  // endregion

  private val bitmapPaint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
  private val placeholderPaint = Paint().apply { color = Color.WHITE }
  private val drawRect = RectF()
  private val tileSource = Rect()

  private val scroller = OverScroller(context)
  private var isTouching = false
  private var zoomAnimator: ValueAnimator? = null
  private val settleRunnable = Runnable { onSettled() }

  init {
    // Set by the ScrollView style; a PDF surface never takes key focus from touch.
    isFocusableInTouchMode = false
    isVerticalScrollBarEnabled = true
    isHorizontalScrollBarEnabled = true
  }

  private val gestureListener = object : GestureDetector.SimpleOnGestureListener() {
    override fun onDown(e: MotionEvent): Boolean = true

    override fun onScroll(e1: MotionEvent?, e2: MotionEvent, distanceX: Float, distanceY: Float): Boolean {
      if (scaleDetector.isInProgress) return true
      if (!isUserScrollEnabled) return false
      scrollByContent(distanceX, distanceY)
      return true
    }

    override fun onFling(e1: MotionEvent?, e2: MotionEvent, velocityX: Float, velocityY: Float): Boolean {
      if (scaleDetector.isInProgress) return true
      if (!isUserScrollEnabled) return false
      val mainVelocity = if (isHorizontal) velocityX else velocityY
      val bounds = scrollBounds()
      if (isPaged && !isZoomedIn) {
        pagingFlingHandled = true
        val page = nearestPage()
        val position = mainOffset / scale
        val scrollsWithinPage = if (mainVelocity < 0) {
          position < pageScrollEnd(page) - 1f
        } else {
          position > pageScrollStart(page) + 1f
        }
        if (!scrollsWithinPage) {
          val target = pageForFling(mainVelocity)
          // Going back in scroll order lands on the end of the previous page.
          snapToPage(target, atEnd = layout?.let { it.slotStart(target) < it.slotStart(page) } ?: false)
          return true
        }
        // A page longer than the view scrolls freely up to its edges first.
        val start = pageScrollStart(page) * scale
        val end = pageScrollEnd(page) * scale
        if (isHorizontal) bounds.set(start, bounds.top, end, bounds.bottom) else bounds.set(bounds.left, start, bounds.right, end)
      }
      scroller.fling(
        offsetX.roundToInt(), offsetY.roundToInt(),
        -velocityX.roundToInt(), -velocityY.roundToInt(),
        bounds.left.roundToInt(), bounds.right.roundToInt(),
        bounds.top.roundToInt(), bounds.bottom.roundToInt(),
      )
      if (mainVelocity != 0f) {
        direction = directionForOffsetChange(-mainVelocity)
      }
      postInvalidateOnAnimation()
      return true
    }

    // Confirmed only after the double-tap window, so a double tap zooms
    // without also reporting a page tap.
    override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
      if (isDoubleTapZoomEnabled) handleTap(e.x, e.y)
      return true
    }

    // Nothing to wait for without double-tap zoom.
    override fun onSingleTapUp(e: MotionEvent): Boolean {
      if (!isDoubleTapZoomEnabled) handleTap(e.x, e.y)
      return true
    }

    override fun onLongPress(e: MotionEvent) {
      if (!scaleDetector.isInProgress) textSelection.onLongPress(e.x, e.y)
    }

    override fun onDoubleTap(e: MotionEvent): Boolean {
      val target = if (scale > minScale * 1.05f) minScale else min(maxScale, max(minScale * 2f, 2f))
      animateZoom(target, e.x, e.y)
      return true
    }
  }

  private val gestureDetector = GestureDetector(context, gestureListener)

  private val scaleDetector = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
    override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
      // Pinches are ours even when drags go to the parents.
      parent?.requestDisallowInterceptTouchEvent(true)
      return true
    }

    override fun onScale(detector: ScaleGestureDetector): Boolean {
      zoomAround(scale * detector.scaleFactor, detector.focusX, detector.focusY)
      return true
    }
  }).apply {
    // Double-tap-and-drag would conflict with double-tap zoom.
    isQuickScaleEnabled = false
  }

  private val textSelection = TextSelectionController(
    this,
    object : TextSelectionController.Host {
      override fun mapToView(page: Int, left: Float, top: Float, right: Float, bottom: Float, out: RectF): Boolean {
        val layout = layout ?: return false
        if (page >= layout.pageCount) return false
        val pixels = layout.pointsToPixels(page)
        val pageLeft = layout.pageLeft(page)
        val pageTop = layout.pageTop(page)
        out.set(
          (pageLeft + left * pixels) * scale - offsetX,
          (pageTop + top * pixels) * scale - offsetY,
          (pageLeft + right * pixels) * scale - offsetX,
          (pageTop + bottom * pixels) * scale - offsetY,
        )
        return true
      }

      override fun pagePointAt(viewX: Float, viewY: Float): PagePoint? =
        layout?.pagePointAt((offsetX + viewX) / scale, (offsetY + viewY) / scale)

      override fun pagePointOn(page: Int, viewX: Float, viewY: Float): PagePoint? {
        val layout = layout ?: return null
        if (page >= layout.pageCount) return null
        val pixels = layout.pointsToPixels(page)
        val x = ((offsetX + viewX) / scale - layout.pageLeft(page)) / pixels
        val y = ((offsetY + viewY) / scale - layout.pageTop(page)) / pixels
        return PagePoint(page, x.coerceIn(0f, layout.pageWidth(page) / pixels), y.coerceIn(0f, layout.pageHeight(page) / pixels))
      }

      override fun pageSize(page: Int): PdfDocumentLayout.PageSize? = pageSizes.getOrNull(page)

      override fun submit(query: TextSelectionQuery) {
        session?.selectText(query)
      }

      override fun onSelectionSettled(text: String) {
        listener?.onTextSelectionChanged(text)
      }
    },
  )

  init {
    // Applies the platform check.
    isTextSelectionEnabled = true
  }

  private val memoryCallbacks = object : ComponentCallbacks2 {
    @Suppress("DEPRECATION")
    override fun onTrimMemory(level: Int) {
      if (level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW) trimToVisible()
    }

    override fun onConfigurationChanged(newConfig: Configuration) = Unit

    @Deprecated("Deprecated in Java")
    override fun onLowMemory() = trimToVisible()
  }

  // region Public API (UI thread)

  /** Starts showing [session]'s document; called once its page sizes are known. */
  fun setDocument(
    session: PdfDocumentSession,
    pageSizes: List<PdfDocumentLayout.PageSize>,
    initialPage: Int,
    initialScale: Float,
  ) {
    clearDocument()
    this.session = session
    this.pageSizes = pageSizes.toMutableList()
    pendingInitialPage = initialPage.coerceIn(0, pageSizes.size - 1)
    // Applied by rebuildLayout's initial-page branch (clamped, cross-axis centred).
    scale = initialScale
    rebuildLayout()
    // The initial zoom is not a change.
    lastReportedScale = scale
    // Hint at the document's length, as scrolling containers do on first show.
    awakenScrollBars()
  }

  /** Drops the document and every bitmap it produced. Does not close the session. */
  fun clearDocument() {
    scroller.forceFinished(true)
    zoomAnimator?.cancel()
    removeCallbacks(settleRunnable)
    textSelection.clear()
    session = null
    pageSizes = ArrayList()
    relayoutPending = false
    layout = null
    pendingInitialPage = null
    failedPages.clear()
    pageLinks.clear()
    reportedOutOfMemory = false
    cache.clear()
    clearTiles()
    scale = minScale
    offsetX = 0f
    offsetY = 0f
    currentPage = -1
    previousVisible = IntRange.EMPTY
    lastSubmitted = emptyList()
    statHits = 0
    statMisses = 0
    updateCacheStats()
    invalidate()
  }

  fun setScaleLimits(min: Double?, max: Double?) {
    val newMin = min?.toFloat()?.takeIf { it.isFinite() && it > 0f } ?: 1f
    val newMax = (max?.toFloat()?.takeIf { it.isFinite() } ?: 4f).coerceAtLeast(newMin)
    if (newMin == minScale && newMax == maxScale) return
    minScale = newMin
    maxScale = newMax
    zoomAround(scale, width / 2f, height / 2f)
  }

  fun setPageSpacing(spacingDp: Double?) {
    val px = (spacingDp?.toFloat()?.takeIf { it.isFinite() && it >= 0f } ?: 8f) * resources.displayMetrics.density
    if (px == spacingPx) return
    spacingPx = px
    rebuildLayout()
  }

  fun goToPage(page: Int, animated: Boolean) {
    val layout = layout ?: run {
      pendingInitialPage = page
      return
    }
    val target = page.coerceIn(0, layout.pageCount - 1)
    if (isSinglePage) {
      // Nothing to animate through: the other pages are not shown.
      scroller.forceFinished(true)
      shownPage = target
      setMainOffset(layout.slotStart(target) * scale)
      scheduleSettle()
      return
    }
    scrollToMain(layout.slotStart(target), animated)
  }

  /**
   * Switches between vertical and horizontal layout, continuous or paged
   * scrolling, a single static page, and how pages fit the view. Keeps the
   * current page and resets zoom. A single page uses the paged layout (page
   * centred in a viewport-sized slot) restricted to that slot.
   */
  fun setLayoutMode(mode: LayoutMode) {
    val newAxis = if (mode.isHorizontal && !mode.isSinglePage) PdfDocumentLayout.Axis.HORIZONTAL else PdfDocumentLayout.Axis.VERTICAL
    val newPaged = mode.isPaged || mode.isSinglePage
    if (
      newAxis == axis && newPaged == isPaged && mode.fit == fit &&
      mode.isSinglePage == isSinglePage && mode.isRightToLeft == isRightToLeft
    ) {
      return
    }
    axis = newAxis
    isPaged = newPaged
    isSinglePage = mode.isSinglePage
    isRightToLeft = mode.isRightToLeft
    fit = mode.fit
    scroller.forceFinished(true)
    zoomAnimator?.cancel()
    textSelection.clear()
    if (layout != null) pendingInitialPage = currentPage.coerceAtLeast(0)
    scale = minScale
    offsetX = 0f
    offsetY = 0f
    rebuildLayout()
  }

  fun onPageLinks(page: Int, links: List<PageLink>) {
    pageLinks[page] = links
  }

  fun onTextSelected(query: TextSelectionQuery, selection: PageTextSelection?) {
    textSelection.onResolved(query, selection)
  }

  /**
   * A confirmed single tap: follow an internal link natively, report a URL
   * link, or report a page tap. Taps in gaps between pages are ignored.
   */
  private fun handleTap(viewX: Float, viewY: Float) {
    // As on iOS, a tap drops the selection and still counts as a tap.
    textSelection.clear()
    val layout = layout ?: return
    val point = layout.pagePointAt((offsetX + viewX) / scale, (offsetY + viewY) / scale) ?: return
    when (val link = pageLinks[point.page]?.firstOrNull { it.contains(point.x, point.y) }) {
      is PageLink.Goto -> goToPage(link.page, animated = true)
      is PageLink.Url -> listener?.onLinkPressed(link.url)
      null -> listener?.onPageTapped(point)
    }
  }

  /** Scrolls so unzoomed main-axis [contentPosition] starts the viewport. */
  private fun scrollToMain(contentPosition: Float, animated: Boolean, durationMs: Int = 300) {
    scroller.forceFinished(true)
    val target = clampMain(contentPosition * scale)
    if (!animated) {
      setMainOffset(target)
      scheduleSettle()
      return
    }
    // Animating across hundreds of pages would render every page on the way.
    // Jump to one viewport before the target and animate the rest.
    val distance = target - mainOffset
    if (abs(distance) > mainViewportLength * 3) {
      setMainOffset(target - mainViewportLength * Math.signum(distance))
    }
    val delta = (target - mainOffset).roundToInt()
    if (delta == 0) {
      scheduleSettle()
      return
    }
    direction = directionForOffsetChange(delta.toFloat())
    scroller.startScroll(
      offsetX.roundToInt(), offsetY.roundToInt(),
      if (isHorizontal) delta else 0, if (isHorizontal) 0 else delta,
      durationMs,
    )
    postInvalidateOnAnimation()
  }

  /** Paged mode: the page the viewport should settle on after a fling. */
  private fun pageForFling(mainVelocity: Float): Int {
    val layout = layout ?: return 0
    val position = mainOffset / scale
    val start = layout.slotAt(position)
    val alignedWithStart = abs(position - layout.slotStart(start)) < 1f
    val target = when {
      abs(mainVelocity) < minimumPagingFlingVelocity -> layout.slotAt(position + mainViewportLength / scale / 2f)
      mainVelocity < 0 -> layout.nextInScrollOrder(start)
      alignedWithStart -> layout.previousInScrollOrder(start)
      else -> start
    }
    return target.coerceIn(0, layout.pageCount - 1)
  }

  /** Paged mode: scrolls to the start (or, going back, the end) of [page]. */
  private fun snapToPage(page: Int, atEnd: Boolean = false) {
    val target = if (atEnd) pageScrollEnd(page) else pageScrollStart(page)
    scrollToMain(target, animated = true, durationMs = PAGE_SNAP_DURATION_MS)
  }

  /**
   * Paged mode: scrolls back inside the nearest page's scroll range when
   * outside it. Returns whether it started scrolling.
   */
  private fun settleOnPage(): Boolean {
    if (layout == null) return false
    val page = nearestPage()
    val position = mainOffset / scale
    val target = position.coerceIn(pageScrollStart(page), pageScrollEnd(page))
    if (abs(target - position) * scale <= 1f) return false
    scrollToMain(target, animated = true, durationMs = PAGE_SNAP_DURATION_MS)
    return true
  }

  /** Paged mode: first main-axis content position showing [page]. */
  private fun pageScrollStart(page: Int): Float = layout?.slotStart(page) ?: 0f

  /** Paged mode: last main-axis content position still showing only [page]. */
  private fun pageScrollEnd(page: Int): Float {
    val layout = layout ?: return 0f
    return max(layout.slotStart(page), layout.slotEnd(page) - mainViewportLength / scale)
  }

  /** Paged mode: the page whose slot holds the viewport centre. */
  private fun nearestPage(): Int {
    val layout = layout ?: return 0
    return layout.slotAt((mainOffset + mainViewportLength / 2f) / scale)
  }

  fun zoomTo(target: Float, animated: Boolean) {
    if (animated) {
      animateZoom(target, width / 2f, height / 2f)
    } else {
      zoomAround(target, width / 2f, height / 2f)
      scheduleSettle()
    }
  }

  // endregion

  // region Session callbacks (UI thread)

  fun onRendered(request: RenderRequest, bitmap: Bitmap) {
    val layout = layout
    if (layout == null || request.page >= layout.pageCount) {
      bitmapPool.release(bitmap)
      return
    }
    when (request) {
      is RenderRequest.Base -> {
        cache.put(request.page, bitmap, protectedKeys = visiblePages().toSet())
        updateCacheStats()
      }
      is RenderRequest.Tile -> {
        if (request.generation != tileGeneration || request.page !in visiblePages()) {
          bitmapPool.release(bitmap)
          return
        }
        val contentRect = RectF(
          request.contentLeft,
          request.contentTop,
          request.contentLeft + request.usedWidth / request.viewScale,
          request.contentTop + request.usedHeight / request.viewScale,
        )
        tiles.put(request.page, Tile(bitmap, contentRect, request.usedWidth, request.usedHeight))
          ?.let { releaseAfterFrame(it.bitmap) }
      }
    }
    invalidate()
    requestRenders()
  }

  fun onRenderFailed(request: RenderRequest, failure: PdfFailure) {
    if (failure.code == PdfErrorCode.OUT_OF_MEMORY) {
      trimToVisible()
      if (!reportedOutOfMemory) {
        reportedOutOfMemory = true
        listener?.onRenderFailed(failure)
      }
      return
    }
    // Never retry a page the platform renderer refused; it would loop forever.
    if (failedPages.add(request.page)) listener?.onRenderFailed(failure)
  }

  /**
   * Page sizes start as estimates (page 0's size) and are corrected as pages
   * are first rendered. Relayout keeps the top-most visible page anchored;
   * while a fling or zoom animation runs it waits for the viewport to settle
   * so the scroller's coordinates are not moved underneath it.
   */
  fun onPageMeasured(page: Int, size: PdfDocumentLayout.PageSize) {
    if (page >= pageSizes.size) return
    pageSizes[page] = size
    if (isTouching || !scroller.isFinished || zoomAnimator != null) {
      relayoutPending = true
    } else {
      rebuildLayout()
    }
  }

  // endregion

  // region View lifecycle

  override fun onAttachedToWindow() {
    super.onAttachedToWindow()
    context.applicationContext.registerComponentCallbacks(memoryCallbacks)
    bitmapPool.setMaxBytes(poolBudget())
    requestRenders(force = true)
  }

  override fun onDetachedFromWindow() {
    // Hidden screens (e.g. under a native stack) are detached; hold no pixels
    // while invisible. The document stays open and re-renders on attach.
    context.applicationContext.unregisterComponentCallbacks(memoryCallbacks)
    scroller.forceFinished(true)
    zoomAnimator?.cancel()
    removeCallbacks(settleRunnable)
    // Also ends the selection toolbar, which must not outlive the window.
    textSelection.clear()
    session?.submit(emptyList())
    lastSubmitted = emptyList()
    // Pool budget 0 first: evicted bitmaps (including releases still queued
    // for the next frame) are dropped instead of being pooled while hidden.
    bitmapPool.clear()
    cache.clear()
    clearTiles()
    updateCacheStats()
    super.onDetachedFromWindow()
  }

  override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
    super.onSizeChanged(w, h, oldw, oldh)
    cache.updateBudget(defaultCacheBudget(), visiblePages().toSet())
    if (isAttachedToWindow) bitmapPool.setMaxBytes(poolBudget())
    if (w != oldw || (layoutDependsOnHeight && h != oldh)) rebuildLayout() else clampAndRefresh()
  }

  override fun computeVerticalScrollRange(): Int =
    if (isSinglePage) height else max(height, ((layout?.contentHeight ?: 0f) * scale).roundToInt())

  override fun computeVerticalScrollOffset(): Int = if (isSinglePage) 0 else offsetY.roundToInt()

  override fun computeVerticalScrollExtent(): Int = height

  override fun computeHorizontalScrollRange(): Int =
    max(width, ((layout?.contentWidth ?: 0f) * scale).roundToInt())

  /** Negative while a zoomed-out page is centred; the bar is hidden then anyway. */
  override fun computeHorizontalScrollOffset(): Int = max(0, offsetX.roundToInt())

  override fun computeHorizontalScrollExtent(): Int = width

  override fun onDraw(canvas: Canvas) {
    val layout = layout ?: return
    val visible = visiblePages()
    if (visible.isEmpty()) return
    canvas.save()
    // React Native parents disable clipChildren; never paint outside our bounds.
    canvas.clipRect(0, 0, width, height)
    canvas.save()
    canvas.translate(-offsetX, -offsetY)
    canvas.scale(scale, scale)
    for (page in visible) {
      drawRect.set(layout.pageLeft(page), layout.pageTop(page), layout.pageRight(page), layout.pageBottom(page))
      val bitmap = cache.get(page)
      if (bitmap != null) {
        canvas.drawBitmap(bitmap, null, drawRect, bitmapPaint)
      } else {
        canvas.drawRect(drawRect, placeholderPaint)
      }
      tiles[page]?.let { tile ->
        tileSource.set(0, 0, tile.usedWidth, tile.usedHeight)
        canvas.drawBitmap(tile.bitmap, tileSource, tile.contentRect, bitmapPaint)
      }
    }
    canvas.restore()
    textSelection.draw(canvas)
    canvas.restore()
  }

  override fun computeScroll() {
    if (scroller.computeScrollOffset()) {
      setOffsets(scroller.currX.toFloat(), scroller.currY.toFloat())
      if (scroller.isFinished) scheduleSettle() else postInvalidateOnAnimation()
    }
  }

  @SuppressLint("ClickableViewAccessibility") // No click semantics; this is a scroll surface.
  override fun onTouchEvent(event: MotionEvent): Boolean {
    // A single page is static (a thumbnail): touches go to the parents.
    if (layout == null || isSinglePage) return false
    when (event.actionMasked) {
      MotionEvent.ACTION_DOWN -> {
        // Keep drags from scrolling parents — unless drags are theirs.
        if (isUserScrollEnabled) parent?.requestDisallowInterceptTouchEvent(true)
        pagingFlingHandled = false
        scroller.forceFinished(true)
        zoomAnimator?.cancel()
        removeCallbacks(settleRunnable)
        isTouching = true
      }
      MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> isTouching = false
    }
    // A gesture that starts on a selection handle is a handle drag throughout.
    if (textSelection.onTouchEvent(event)) {
      if (!isTouching) scheduleSettle()
      return true
    }
    scaleDetector.onTouchEvent(event)
    gestureDetector.onTouchEvent(event)
    if (event.actionMasked == MotionEvent.ACTION_UP && isPaged && !isZoomedIn && !pagingFlingHandled) {
      settleOnPage()
    }
    if (!isTouching && scroller.isFinished && zoomAnimator == null) scheduleSettle()
    return true
  }

  // endregion

  // region Geometry

  private fun rebuildLayout() {
    if (width <= 0 || (layoutDependsOnHeight && height <= 0) || pageSizes.isEmpty()) {
      layout = null
      invalidate()
      return
    }
    // Keep the same point of the same page at the viewport start across relayouts.
    val old = layout
    var anchorPage = -1
    var anchorFraction = 0f
    val sameMode = old != null && old.axis == axis && old.isPaged == isPaged && old.fit == fit &&
      old.isReversed == (isRightToLeft && isHorizontal)
    if (old != null && sameMode && old.pageCount == pageSizes.size) {
      val position = mainOffset / scale
      anchorPage = old.slotAt(position)
      anchorFraction = (position - old.slotStart(anchorPage)) / (old.slotEnd(anchorPage) - old.slotStart(anchorPage))
    }
    val newLayout = PdfDocumentLayout(pageSizes, width.toFloat(), spacingPx, height.toFloat(), axis, isPaged, fit, isRightToLeft)
    layout = newLayout
    // Tiles are positioned in the old layout's coordinates.
    clearTiles()
    lastSubmitted = emptyList()

    val initial = pendingInitialPage
    if (initial != null) {
      pendingInitialPage = null
      shownPage = initial.coerceIn(0, newLayout.pageCount - 1)
      scale = scale.coerceIn(minScale, maxScale)
      setMainOffsetRaw(clampMain(newLayout.slotStart(initial.coerceIn(0, newLayout.pageCount - 1)) * scale))
      // Content wider than the view (pages of different widths) starts centred.
      val crossContent = (if (isHorizontal) newLayout.contentHeight else newLayout.contentWidth) * scale
      val crossViewport = (if (isHorizontal) height else width).toFloat()
      val crossOffset = max(0f, (crossContent - crossViewport) / 2f)
      if (isHorizontal) offsetY = crossOffset else offsetX = crossOffset
    } else if (anchorPage >= 0) {
      val slotLength = newLayout.slotEnd(anchorPage) - newLayout.slotStart(anchorPage)
      setMainOffsetRaw(clampMain((newLayout.slotStart(anchorPage) + anchorFraction * slotLength) * scale))
    }
    clampAndRefresh()
  }

  private val isHorizontal: Boolean get() = axis == PdfDocumentLayout.Axis.HORIZONTAL

  /** Prefetch direction for a main-axis offset change; RTL runs against the offset. */
  private fun directionForOffsetChange(delta: Float): PrefetchPlanner.Direction {
    val forward = (delta > 0) != (layout?.isReversed == true)
    return if (forward) PrefetchPlanner.Direction.FORWARD else PrefetchPlanner.Direction.BACKWARD
  }

  /** Whether page geometry depends on the view height, not only its width. */
  private val layoutDependsOnHeight: Boolean
    get() = isHorizontal || isPaged || fit != PdfDocumentLayout.Fit.WIDTH

  /** Scroll offset along the main axis, in zoomed pixels. */
  private val mainOffset: Float get() = if (isHorizontal) offsetX else offsetY

  private val mainViewportLength: Float get() = (if (isHorizontal) width else height).toFloat()

  private val isZoomedIn: Boolean get() = scale > minScale * 1.01f

  private val minimumPagingFlingVelocity: Float = ViewConfiguration.get(context).scaledMinimumFlingVelocity * 4f

  private fun scrollBounds(): RectF {
    val layout = layout ?: return RectF()
    val contentWidth = layout.contentWidth * scale
    val contentHeight = layout.contentHeight * scale
    // Content smaller than the view is centred, except a short vertical
    // document, which starts at the top.
    val minX = if (contentWidth < width) -(width - contentWidth) / 2f else 0f
    val maxX = if (contentWidth < width) minX else contentWidth - width
    val minY = if (contentHeight < height && isHorizontal) -(height - contentHeight) / 2f else 0f
    val maxY = if (contentHeight < height) minY else contentHeight - height
    if (isSinglePage && shownPage < layout.pageCount) {
      // Vertical, paged layout: scrolling is confined to the shown page's slot.
      val start = layout.slotStart(shownPage) * scale
      val end = max(start, layout.slotEnd(shownPage) * scale - height)
      return RectF(minX, start, maxX, end)
    }
    return RectF(minX, minY, maxX, maxY)
  }

  private fun clampMain(position: Float): Float {
    val bounds = scrollBounds()
    return if (isHorizontal) position.coerceIn(bounds.left, bounds.right) else position.coerceIn(bounds.top, bounds.bottom)
  }

  private fun setMainOffset(position: Float) {
    if (isHorizontal) setOffsets(position, offsetY) else setOffsets(offsetX, position)
  }

  /** Sets the main offset without the viewport-change side effects (layout rebuild). */
  private fun setMainOffsetRaw(position: Float) {
    if (isHorizontal) offsetX = position else offsetY = position
  }

  private fun scrollByContent(dx: Float, dy: Float) {
    val mainDelta = if (isHorizontal) dx else dy
    if (mainDelta != 0f) {
      direction = directionForOffsetChange(mainDelta)
    }
    setOffsets(offsetX + dx, offsetY + dy)
  }

  private fun setOffsets(x: Float, y: Float) {
    val bounds = scrollBounds()
    offsetX = x.coerceIn(bounds.left, bounds.right)
    offsetY = y.coerceIn(bounds.top, bounds.bottom)
    onViewportChanged()
    awakenScrollBars()
  }

  private fun clampAndRefresh() = setOffsets(offsetX, offsetY)

  /** Zooms to [target] keeping the content under ([focusX], [focusY]) fixed. */
  private fun zoomAround(target: Float, focusX: Float, focusY: Float) {
    val newScale = target.coerceIn(minScale, maxScale)
    if (layout == null) {
      scale = newScale
      return
    }
    val contentX = (offsetX + focusX) / scale
    val contentY = (offsetY + focusY) / scale
    scale = newScale
    setOffsets(contentX * newScale - focusX, contentY * newScale - focusY)
  }

  private fun animateZoom(target: Float, focusX: Float, focusY: Float) {
    zoomAnimator?.cancel()
    val clamped = target.coerceIn(minScale, maxScale)
    zoomAnimator = ValueAnimator.ofFloat(scale, clamped).apply {
      duration = 250
      interpolator = DecelerateInterpolator()
      addUpdateListener { zoomAround(it.animatedValue as Float, focusX, focusY) }
      addListener(object : android.animation.AnimatorListenerAdapter() {
        override fun onAnimationEnd(animation: android.animation.Animator) {
          if (zoomAnimator === animation) zoomAnimator = null
          scheduleSettle()
        }
      })
      start()
    }
  }

  private fun visiblePages(): IntRange {
    val layout = layout ?: return IntRange.EMPTY
    if (width <= 0 || height <= 0) return IntRange.EMPTY
    if (isSinglePage) return if (shownPage < layout.pageCount) shownPage..shownPage else IntRange.EMPTY
    return layout.visibleRange(mainOffset / scale, (mainOffset + mainViewportLength) / scale)
  }

  // endregion

  // region Viewport → rendering

  private fun onViewportChanged() {
    val layout = layout ?: return
    val visible = visiblePages()
    if (visible != previousVisible) {
      for (page in visible) {
        if (page in previousVisible) continue
        if (cache.contains(page)) statHits++ else statMisses++
      }
      previousVisible = visible
    }
    val bounds = scrollBounds()
    val mainStart = if (isHorizontal) bounds.left else bounds.top
    val mainEnd = if (isHorizontal) bounds.right else bounds.bottom
    // Scrolled to the far end: the last page, even when it is too short to
    // reach the viewport centre. In RTL the last page is at the start.
    val lastPageEdgeReached = if (layout.isReversed) {
      mainOffset <= mainStart + 1f && mainEnd > mainStart
    } else {
      mainOffset >= mainEnd - 1f && mainOffset > 0f
    }
    val page = when {
      isSinglePage -> shownPage
      lastPageEdgeReached -> layout.pageCount - 1
      else -> layout.pageAt((mainOffset + mainViewportLength / 2f) / scale)
    }
    if (page >= 0 && page != currentPage) {
      currentPage = page
      listener?.onPageChanged(page, layout.pageCount)
    }
    textSelection.onViewportMoved()
    requestRenders()
    invalidate()
  }

  private fun scheduleSettle() {
    removeCallbacks(settleRunnable)
    postDelayed(settleRunnable, SETTLE_DELAY_MS)
  }

  /** The viewport stopped moving: render zoom tiles for what is on screen. */
  private fun onSettled() {
    if (isTouching || !scroller.isFinished || zoomAnimator != null) return
    if (relayoutPending) {
      relayoutPending = false
      rebuildLayout()
    }
    // Paged mode: after zooming back out, settle on a page again.
    if (isPaged && !isZoomedIn && settleOnPage()) return
    if (layout != null && abs(scale - lastReportedScale) > SCALE_EPSILON) {
      lastReportedScale = scale
      listener?.onScaleSettled(scale)
    }
    tileGeneration++
    val visible = visiblePages()
    tiles.keys.filter { it !in visible }.forEach { page -> tiles.remove(page)?.let { releaseAfterFrame(it.bitmap) } }
    requestRenders(force = true)
    textSelection.onViewportSettled()
  }

  private fun requestRenders(force: Boolean = false) {
    val session = session ?: return
    val layout = layout ?: return
    if (!isAttachedToWindow || width <= 0 || height <= 0) return
    val visible = visiblePages()
    val center = layout.pageAt((mainOffset + mainViewportLength / 2f) / scale)
    // A single page never shows its neighbours; don't spend memory on them.
    val planned = if (isSinglePage) visible.toList() else PrefetchPlanner.plan(visible, center, layout.pageCount, direction)

    val bases = planned.filter { it !in failedPages }.map { baseRequest(layout, it) }
    val constrained = SystemClock.uptimeMillis() < constrainedUntilMs
    val fit = PrefetchPlanner.fitToBudget(
      pageBytes = bases.map { it.bitmapWidth.toLong() * it.bitmapHeight * 4 },
      minKeep = bases.count { it.page in visible },
      baseBudget = if (constrained) 0L else defaultCacheBudget(),
      maxBudget = if (constrained) 0L else MAX_CACHE_BYTES,
    )
    if (fit.budgetBytes != cache.budgetBytes) {
      cache.updateBudget(fit.budgetBytes, visible.toSet())
      updateCacheStats()
    }

    val requests = ArrayList<RenderRequest>(fit.keep + visible.count())
    val settled = !isTouching && scroller.isFinished && zoomAnimator == null
    for (index in 0 until fit.keep) {
      val base = bases[index]
      val page = base.page
      val cached = cache.get(page)
      // Height follows the page's real aspect ratio (decided on the render
      // thread), so only the width identifies a stale resolution.
      if (cached == null || cached.width != base.bitmapWidth) {
        requests += base
      }
    }
    if (settled) {
      // Tiles go after visible base pages but before prefetch.
      val tileRequests = visible.mapNotNull { page -> tileRequest(layout, page) }
      val insertAt = requests.indexOfFirst { it.page !in visible }.let { if (it < 0) requests.size else it }
      requests.addAll(insertAt, tileRequests)
    }
    if (!force && requests == lastSubmitted) return
    lastSubmitted = requests
    session.submit(requests)
  }

  private fun baseRequest(layout: PdfDocumentLayout, page: Int): RenderRequest.Base {
    var bitmapWidth = layout.pageWidth(page)
    var bitmapHeight = layout.pageHeight(page)
    // Very tall pages (receipts, posters) would otherwise allocate enormous
    // bitmaps; cap the base layer and let tiles supply detail.
    val maxPixels = max(width.toFloat() * height * MAX_BASE_VIEWPORTS, 1f)
    val pixels = bitmapWidth * bitmapHeight
    if (pixels > maxPixels) {
      val factor = sqrt(maxPixels / pixels)
      bitmapWidth *= factor
      bitmapHeight *= factor
    }
    return RenderRequest.Base(page, max(1, bitmapWidth.roundToInt()), max(1, bitmapHeight.roundToInt()), maxPixels.toLong())
  }

  private fun tileRequest(layout: PdfDocumentLayout, page: Int): RenderRequest.Tile? {
    if (page in failedPages) return null
    // A page without a base bitmap may still have an estimated size; its tile
    // geometry would be wrong. Tiles follow once the base layer is in.
    val base = cache.get(page) ?: return null
    // Pixels of base bitmap per unzoomed content pixel.
    val baseResolution = base.width / layout.pageWidth(page)
    if (scale <= baseResolution * TILE_THRESHOLD) {
      tiles.remove(page)?.let { releaseAfterFrame(it.bitmap) }
      return null
    }
    val viewLeft = offsetX / scale
    val viewTop = offsetY / scale
    val left = max(layout.pageLeft(page), viewLeft)
    val top = max(layout.pageTop(page), viewTop)
    val right = min(layout.pageRight(page), viewLeft + width / scale)
    val bottom = min(layout.pageBottom(page), viewTop + height / scale)
    if (right <= left || bottom <= top) return null
    val usedWidth = min(width, ceil((right - left) * scale).toInt())
    val usedHeight = min(height, ceil((bottom - top) * scale).toInt())
    return RenderRequest.Tile(
      page = page,
      generation = tileGeneration,
      bitmapWidth = width,
      bitmapHeight = height,
      usedWidth = usedWidth,
      usedHeight = usedHeight,
      scale = layout.pointsToPixels(page) * scale,
      dx = (layout.pageLeft(page) - left) * scale,
      dy = (layout.pageTop(page) - top) * scale,
      contentLeft = left,
      contentTop = top,
      viewScale = scale,
    )
  }

  // endregion

  // region Memory management

  private fun trimToVisible() {
    constrainedUntilMs = SystemClock.uptimeMillis() + MEMORY_PRESSURE_BACKOFF_MS
    val visible = visiblePages().toSet()
    cache.trimTo(0, visible)
    bitmapPool.clear()
    updateCacheStats()
  }

  private fun clearTiles() {
    tiles.values.forEach { releaseAfterFrame(it.bitmap) }
    tiles.clear()
  }

  /**
   * Returns [bitmap] to the pool once the frame that may still reference it
   * has been drawn, so the renderer never overwrites pixels on screen.
   */
  private fun releaseAfterFrame(bitmap: Bitmap) {
    if (isAttachedToWindow) {
      postOnAnimation { bitmapPool.release(bitmap) }
    } else {
      // Not attached: no frame can reference it, and a posted runnable would
      // wait for the next attach.
      bitmapPool.release(bitmap)
    }
  }

  /** Room for two viewport-sized bitmaps (tiles, or a couple of pages). */
  private fun poolBudget(): Long = max(MIN_POOL_BYTES, width.toLong() * height * 4)

  private fun updateCacheStats() {
    statResidentPages = cache.count
    statCacheBytes = cache.sizeBytes
  }

  private fun defaultCacheBudget(): Long {
    val heapBased = Runtime.getRuntime().maxMemory() / 6
    // Always room for the visible pages plus a little look-ahead.
    val viewportBytes = max(1, width).toLong() * max(1, height) * 4
    return heapBased.coerceIn(MIN_CACHE_BYTES, MAX_CACHE_BYTES).coerceAtLeast(viewportBytes * 4)
  }

  // endregion

  private companion object {
    const val SETTLE_DELAY_MS = 80L

    /** Zoom changes below this (0.1%) are not reported. */
    const val SCALE_EPSILON = 0.001f
    const val PAGE_SNAP_DURATION_MS = 250
    /** How long to keep only visible pages after a memory-pressure signal. */
    const val MEMORY_PRESSURE_BACKOFF_MS = 30_000L
    /** Render zoom tiles once the screen shows 15% more pixels than the base layer has. */
    const val TILE_THRESHOLD = 1.15f
    /** Upper bound for a page's base bitmap, in viewports worth of pixels. */
    const val MAX_BASE_VIEWPORTS = 2f
    const val MIN_CACHE_BYTES = 24L * 1024 * 1024
    const val MIN_POOL_BYTES = 8L * 1024 * 1024
    const val MAX_CACHE_BYTES = 128L * 1024 * 1024
  }
}
