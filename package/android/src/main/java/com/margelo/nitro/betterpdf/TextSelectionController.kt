package com.margelo.nitro.betterpdf

import android.content.ClipData
import android.content.ClipboardManager
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.view.ActionMode
import android.view.HapticFeedbackConstants
import android.view.Menu
import android.view.MenuItem
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration

/**
 * Text selection for [PdfDocumentView], as in a read-only text field: a long
 * press selects the word under the finger, two handles adjust the selection,
 * and the platform's floating toolbar offers Copy and Select all. A
 * selection stays on one page.
 *
 * Page text is only reachable through an open page, so every selection is
 * resolved on the document's render thread ([Host.submit]) and comes back
 * through [onResolved]. Everything else runs on the UI thread.
 *
 * The app hears about a selection once it settles ([Host.onSelectionSettled]):
 * after the long press or handle drag ends and its text is known, never per
 * touch move.
 */
internal class TextSelectionController(private val view: View, private val host: Host) {
  interface Host {
    /** Maps a rectangle on [page] in PDF points to view pixels; false without a layout. */
    fun mapToView(page: Int, left: Float, top: Float, right: Float, bottom: Float, out: RectF): Boolean

    /** The page and point (PDF points) under a view pixel; null in a gap between pages. */
    fun pagePointAt(viewX: Float, viewY: Float): PagePoint?

    /** The point on [page] (PDF points) under a view pixel, clamped to the page. */
    fun pagePointOn(page: Int, viewX: Float, viewY: Float): PagePoint?

    /** The size of [page] in PDF points. */
    fun pageSize(page: Int): PdfDocumentLayout.PageSize?

    fun submit(query: TextSelectionQuery)

    fun onSelectionSettled(text: String)
  }

  /** Set by the view: the prop, on Android 15+ only. */
  var isEnabled = false
    set(value) {
      field = value
      if (!value) clear()
    }

  var selection: PageTextSelection? = null
    private set

  /** Last query sent; results of older queries are dropped. */
  private var generation = 0
  /** Query whose result is on screen (or was found to select nothing). */
  private var shownGeneration = 0
  private var longPressGeneration = -1
  private var drag: PageTextSelection.HandleDrag? = null
  /** From the finger to where the dragged handle aims, in view pixels. */
  private var dragOffsetX = 0f
  private var dragOffsetY = 0f
  private var reportedText = ""
  /** The floating toolbar; null again as soon as this class finishes it. */
  private var actionMode: ActionMode? = null

  private val highlightPaint = Paint()
  private val startHandle: Drawable?
  private val stopHandle: Drawable?
  private val mapped = RectF()
  private val handleRect = Rect()
  private val touchSlop = ViewConfiguration.get(view.context).scaledTouchSlop

  init {
    // The same look as selected text in the app's text fields.
    val attributes = view.context.obtainStyledAttributes(
      intArrayOf(android.R.attr.textColorHighlight, android.R.attr.textSelectHandleLeft, android.R.attr.textSelectHandleRight),
    )
    try {
      highlightPaint.color = attributes.getColor(0, DEFAULT_HIGHLIGHT)
      startHandle = attributes.getDrawable(1)
      stopHandle = attributes.getDrawable(2)
    } finally {
      attributes.recycle()
    }
  }

  // region Input (UI thread)

  fun onLongPress(viewX: Float, viewY: Float) {
    if (!isEnabled) return
    val point = host.pagePointAt(viewX, viewY) ?: return
    val at = TextSelectionQuery.Boundary.Point(point.x, point.y)
    longPressGeneration = ++generation
    host.submit(TextSelectionQuery(longPressGeneration, point.page, at, at))
  }

  /** Handle drags. Returns true for every event of a gesture that started on a handle. */
  fun onTouchEvent(event: MotionEvent): Boolean {
    when (event.actionMasked) {
      MotionEvent.ACTION_DOWN -> {
        val selection = selection ?: return false
        val end = handleAt(selection, event.x, event.y) ?: return false
        val anchor = selection.anchor(end)
        if (!host.mapToView(selection.page, anchor.x, anchor.y - selection.lineHeight(end) / 2f, anchor.x, anchor.y, mapped)) return false
        drag = selection.drag(end)
        dragOffsetX = mapped.left - event.x
        dragOffsetY = mapped.top - event.y
        // The drag is ours, also when the document leaves drags to its parents.
        view.parent?.requestDisallowInterceptTouchEvent(true)
        hideActionMode()
        return true
      }
      MotionEvent.ACTION_MOVE -> {
        val drag = drag ?: return false
        val point = host.pagePointOn(drag.page, event.x + dragOffsetX, event.y + dragOffsetY) ?: return true
        host.submit(drag.query(point.x, point.y, ++generation))
        return true
      }
      MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
        if (drag == null) return false
        drag = null
        showActionMode()
        reportIfSettled()
        return true
      }
    }
    return drag != null
  }

  // endregion

  // region Results (UI thread)

  fun onResolved(query: TextSelectionQuery, result: PageTextSelection?) {
    if (query.generation <= shownGeneration) return
    shownGeneration = query.generation
    val isLongPress = query.generation == longPressGeneration
    when {
      result != null -> {
        selection = result
        if (isLongPress) view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        view.invalidate()
        if (drag == null) showActionMode()
      }
      // A long press outside text drops the selection; a handle dragged
      // over nothing keeps the last one.
      isLongPress -> {
        clear()
        return
      }
    }
    reportIfSettled()
  }

  // endregion

  // region Viewport (UI thread)

  /** The toolbar would float over moving content; it comes back on [onViewportSettled]. */
  fun onViewportMoved() {
    if (selection != null) hideActionMode()
  }

  fun onViewportSettled() {
    if (selection != null && drag == null) showActionMode()
  }

  /** Drops the selection (reported as `''` if the app saw text) and any result still being resolved. */
  fun clear() {
    shownGeneration = ++generation
    drag = null
    finishActionMode()
    if (selection != null) {
      selection = null
      view.invalidate()
    }
    report("")
  }

  // endregion

  // region Drawing (UI thread)

  /** Draws in view coordinates, after the pages. */
  fun draw(canvas: Canvas) {
    val selection = selection ?: return
    for (rect in selection.bounds) {
      if (host.mapToView(selection.page, rect.left, rect.top, rect.right, rect.bottom, mapped)) {
        canvas.drawRect(mapped, highlightPaint)
      }
    }
    for (end in PageTextSelection.End.entries) {
      val drawable = handleDrawable(end) ?: continue
      if (handleBounds(selection, end, drawable, handleRect)) {
        drawable.bounds = handleRect
        drawable.draw(canvas)
      }
    }
  }

  private fun handleDrawable(end: PageTextSelection.End): Drawable? =
    if (end == PageTextSelection.End.START) startHandle else stopHandle

  /**
   * The handle hangs below the line, its tip on the selection's edge. As in
   * TextView, the tip of the start handle is at 3/4 of its width and that of
   * the stop handle at 1/4.
   */
  private fun handleBounds(selection: PageTextSelection, end: PageTextSelection.End, drawable: Drawable, out: Rect): Boolean {
    val anchor = selection.anchor(end)
    if (!host.mapToView(selection.page, anchor.x, anchor.y, anchor.x, anchor.y, mapped)) return false
    val width = drawable.intrinsicWidth
    val height = drawable.intrinsicHeight
    val tip = if (end == PageTextSelection.End.START) width * 3 / 4 else width / 4
    val left = mapped.left.toInt() - tip
    val top = mapped.top.toInt()
    out.set(left, top, left + width, top + height)
    return true
  }

  private fun handleAt(selection: PageTextSelection, x: Float, y: Float): PageTextSelection.End? {
    // The stop handle wins where both overlap (a one-letter selection): it
    // is the one users drag most.
    for (end in listOf(PageTextSelection.End.STOP, PageTextSelection.End.START)) {
      val drawable = handleDrawable(end) ?: continue
      if (!handleBounds(selection, end, drawable, handleRect)) continue
      handleRect.inset(-touchSlop, -touchSlop)
      if (handleRect.contains(x.toInt(), y.toInt())) return end
    }
    return null
  }

  // endregion

  // region Toolbar

  private fun showActionMode() {
    val selection = selection ?: return
    if (!isOnScreen(selection)) {
      finishActionMode()
      return
    }
    val mode = actionMode
    if (mode == null) {
      actionMode = view.startActionMode(actionModeCallback, ActionMode.TYPE_FLOATING)
    } else {
      mode.invalidateContentRect()
      mode.hide(0)
    }
  }

  private fun hideActionMode() {
    actionMode?.hide(ViewConfiguration.getDefaultActionModeHideDuration())
  }

  private fun finishActionMode() {
    val mode = actionMode ?: return
    // Cleared first, so onDestroyActionMode can tell this from the user dismissing it.
    actionMode = null
    mode.finish()
  }

  private fun isOnScreen(selection: PageTextSelection): Boolean {
    val union = selection.union
    return host.mapToView(selection.page, union.left, union.top, union.right, union.bottom, mapped) &&
      mapped.intersects(0f, 0f, view.width.toFloat(), view.height.toFloat())
  }

  private fun copy() {
    val text = selection?.text ?: return
    view.context.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText(null, text))
    // Like a text field: copying ends the selection.
    clear()
  }

  private fun selectAll() {
    val selection = selection ?: return
    val size = host.pageSize(selection.page) ?: return
    host.submit(
      TextSelectionQuery(
        ++generation,
        selection.page,
        TextSelectionQuery.Boundary.Point(0f, 0f),
        TextSelectionQuery.Boundary.Point(size.widthPoints, size.heightPoints),
      ),
    )
  }

  private val actionModeCallback = object : ActionMode.Callback2() {
    override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
      menu.add(Menu.NONE, android.R.id.copy, 0, android.R.string.copy)
        .setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
      menu.add(Menu.NONE, android.R.id.selectAll, 1, android.R.string.selectAll)
        .setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
      return true
    }

    override fun onPrepareActionMode(mode: ActionMode, menu: Menu): Boolean = false

    override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean = when (item.itemId) {
      android.R.id.copy -> {
        copy()
        true
      }
      android.R.id.selectAll -> {
        selectAll()
        true
      }
      else -> false
    }

    override fun onDestroyActionMode(mode: ActionMode) {
      if (actionMode !== mode) return
      // Dismissed by the user (e.g. back): drop the selection, as a text field does.
      actionMode = null
      clear()
    }

    override fun onGetContentRect(mode: ActionMode, view: View, outRect: Rect) {
      val selection = selection
      if (selection == null || !isOnScreen(selection)) {
        outRect.setEmpty()
        return
      }
      mapped.roundOut(outRect)
    }
  }

  // endregion

  private fun reportIfSettled() {
    if (drag != null || shownGeneration != generation) return
    report(selection?.text ?: "")
  }

  private fun report(text: String) {
    if (text == reportedText) return
    reportedText = text
    host.onSelectionSettled(text)
  }

  private companion object {
    /** TextView's highlight colour when the theme defines none. */
    const val DEFAULT_HIGHLIGHT = 0x6633B5E5
  }
}
