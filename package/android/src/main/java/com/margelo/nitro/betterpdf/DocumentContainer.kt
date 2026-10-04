package com.margelo.nitro.betterpdf

import android.annotation.SuppressLint
import android.content.Context
import android.widget.FrameLayout

/**
 * The native view React Native lays out: the document view, filling it,
 * and the form's text editor over a field ([FormFiller] places it after
 * every layout pass, which would put it at the top-left corner).
 */
@SuppressLint("ViewConstructor")
internal class DocumentContainer(context: Context, documentView: PdfDocumentView) : FrameLayout(context) {
  var onLaidOut: (() -> Unit)? = null

  init {
    addView(documentView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
  }

  override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
    super.onLayout(changed, left, top, right, bottom)
    onLaidOut?.invoke()
  }
}
