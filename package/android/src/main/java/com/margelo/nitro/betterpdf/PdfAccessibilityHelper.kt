package com.margelo.nitro.betterpdf

import android.graphics.Rect
import android.graphics.RectF
import android.os.Bundle
import android.view.View
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat.AccessibilityActionCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat.CollectionInfoCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat.CollectionItemInfoCompat
import androidx.customview.widget.ExploreByTouchHelper

/**
 * Exposes the pages on screen to TalkBack and other accessibility services
 * as virtual views (the document is drawn on one canvas, so there are no
 * child views to describe). Each page reads as its label ("Page 3 of 12")
 * followed by its text where the platform renderer provides it. The host is
 * a scrollable list of pages, so TalkBack's scroll gestures and "page x of y"
 * collection announcements work. Installing it replaces React Native's own
 * delegate for this view (see [onPopulateNodeForHost] for what that keeps).
 */
internal class PdfAccessibilityHelper(
  private val view: View,
  private val host: PdfAccessibilityHost,
) : ExploreByTouchHelper(view) {
  private val bounds = RectF()
  private val rect = Rect()

  override fun getVirtualViewAt(x: Float, y: Float): Int {
    val page = host.accessibilityPageAt(x, y)
    return if (page >= 0) page else INVALID_ID
  }

  override fun getVisibleVirtualViews(virtualViewIds: MutableList<Int>) {
    host.accessibilityVisiblePages().forEach { virtualViewIds += it }
  }

  override fun onPopulateNodeForVirtualView(virtualViewId: Int, node: AccessibilityNodeInfoCompat) {
    val label = host.accessibilityPageLabel(virtualViewId)
    val text = host.accessibilityPageText(virtualViewId)
    node.text = if (text.isNullOrEmpty()) label else "$label\n$text"
    node.isFocusable = true
    node.setCollectionItemInfo(CollectionItemInfoCompat.obtain(virtualViewId, 1, 0, 1, false))
    host.accessibilityPageBounds(virtualViewId, bounds)
    bounds.roundOut(rect)
    // ExploreByTouchHelper requires non-empty bounds; a page scrolled away
    // between the list and this call keeps a 1 px placeholder.
    if (rect.isEmpty) rect.set(0, 0, 1, 1)
    @Suppress("DEPRECATION") // Required by ExploreByTouchHelper for virtual views.
    node.setBoundsInParent(rect)
  }

  override fun onPopulateNodeForHost(node: AccessibilityNodeInfoCompat) {
    super.onPopulateNodeForHost(node)
    // React Native exposes `testID` as the resource id through its own
    // accessibility delegate, which it only installs on views without one —
    // so this delegate must do it, or E2E tools cannot find the view.
    (view.getTag(com.facebook.react.R.id.react_test_id) as? String)?.let { node.viewIdResourceName = it }
    node.className = "android.widget.ScrollView"
    node.isScrollable = true
    node.addAction(AccessibilityActionCompat.ACTION_SCROLL_FORWARD)
    node.addAction(AccessibilityActionCompat.ACTION_SCROLL_BACKWARD)
    node.setCollectionInfo(CollectionInfoCompat.obtain(host.accessibilityPageCount, 1, false))
  }

  override fun onPerformActionForVirtualView(virtualViewId: Int, action: Int, arguments: Bundle?): Boolean = false
}
