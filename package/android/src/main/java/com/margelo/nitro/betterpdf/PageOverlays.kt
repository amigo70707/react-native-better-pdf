package com.margelo.nitro.betterpdf

import android.graphics.RectF
import android.view.View
import android.view.ViewGroup
import com.facebook.react.R
import com.facebook.react.uimanager.util.ReactFindViewUtil
import java.lang.ref.WeakReference

/**
 * Places `PdfPageOverlay` views — React views in a layer above the PDF
 * view, a sibling in the same box — on their pages. Run on every frame the
 * pages move ([PdfDocumentView.onPagesMoved]), so overlays follow their pages
 * without JS. Views are found by their `nativeID` and cached until they
 * leave the layer.
 */
internal class PageOverlays(private val documentView: PdfDocumentView, private val host: View) {
  var placements: Array<PdfOverlayPlacement> = emptyArray()
  var layerId: String? = null
    set(value) {
      if (value != field) {
        layer = null
        views.clear()
      }
      field = value
    }

  private var layer: WeakReference<ViewGroup>? = null
  private val views = HashMap<String, WeakReference<View>>()
  private val rect = RectF()
  private val documentLocation = IntArray(2)
  private val parentLocation = IntArray(2)

  /** Places every overlay for the current scroll position and zoom. */
  fun layout() {
    if (placements.isEmpty()) return
    val layer = findLayer() ?: return
    documentView.getLocationInWindow(documentLocation)
    // Overlays mostly share a parent (the layer): measure each parent once.
    var measuredParent: View? = null
    for (placement in placements) {
      val view = view(placement.id, layer) ?: continue
      val parent = view.parent as? View ?: continue
      // NaN fails the comparison; past the last page is never on screen.
      val onScreen = placement.page >= 0 &&
        documentView.pageRectInView(
          placement.page.toInt(),
          placement.x.toFloat(),
          placement.y.toFloat(),
          placement.width.toFloat(),
          placement.height.toFloat(),
          rect,
        )
      if (!onScreen) {
        view.visibility = View.INVISIBLE
        continue
      }
      // Into the coordinates of the overlay's parent (the layer, or a view in it).
      if (parent !== measuredParent) {
        parent.getLocationInWindow(parentLocation)
        measuredParent = parent
      }
      rect.offset(
        (documentLocation[0] - parentLocation[0]).toFloat(),
        (documentLocation[1] - parentLocation[1]).toFloat(),
      )
      place(view, rect, placement.scalesWithZoom)
      view.visibility = View.VISIBLE
    }
  }

  /**
   * Transforms [view] (laid out at the overlay's size) so it covers [target],
   * or — not scaling — so its top-left corner is there.
   */
  private fun place(view: View, target: RectF, scalesWithZoom: Boolean) {
    if (view.width <= 0 || view.height <= 0) return
    val scale = if (scalesWithZoom) target.width() / view.width else 1f
    view.pivotX = 0f
    view.pivotY = 0f
    view.scaleX = scale
    view.scaleY = scale
    view.translationX = target.left - view.left
    view.translationY = target.top - view.top
  }

  private fun findLayer(): ViewGroup? {
    layer?.get()?.let { if (it.isAttachedToWindow && nativeId(it) == layerId) return it }
    val layerId = layerId ?: return null
    // The native view React Native lays out → the box it renders for PdfView.
    val box = host.parent as? ViewGroup ?: return null
    for (i in 0 until box.childCount) {
      val child = box.getChildAt(i)
      if (child is ViewGroup && nativeId(child) == layerId) {
        layer = WeakReference(child)
        views.clear()
        return child
      }
    }
    return null
  }

  private fun view(id: String, layer: ViewGroup): View? {
    views[id]?.get()?.let { if (nativeId(it) == id && isInside(it, layer)) return it }
    val found = ReactFindViewUtil.findView(layer, id) ?: return null
    views[id] = WeakReference(found)
    return found
  }

  private fun isInside(view: View, layer: ViewGroup): Boolean {
    var parent = view.parent
    while (parent != null) {
      if (parent === layer) return true
      parent = parent.parent
    }
    return false
  }

  private fun nativeId(view: View): String? = view.getTag(R.id.view_tag_native_id) as? String
}
