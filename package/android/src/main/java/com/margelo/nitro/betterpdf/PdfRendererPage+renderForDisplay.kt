package com.margelo.nitro.betterpdf

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.pdf.PdfRenderer
import android.graphics.pdf.RenderParams
import android.os.Build

/**
 * Renders for the screen, including every annotation kind the platform
 * renderer can draw. Without flags `PdfRenderer` draws no annotations except
 * form fields; measured per kind in docs/api-design.md ("Annotations").
 */
// API 37's flags are passed as literals (this module compiles against API
// 36), which lint cannot match against RenderParams' @IntDef.
@SuppressLint("WrongConstant")
internal fun PdfRenderer.Page.renderForDisplay(target: Bitmap, clip: Rect?, matrix: Matrix) {
  if (Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM) {
    render(target, clip, matrix, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
    return
  }
  var flags = RenderParams.FLAG_RENDER_HIGHLIGHT_ANNOTATIONS or RenderParams.FLAG_RENDER_TEXT_ANNOTATIONS
  if (Build.VERSION.SDK_INT >= API_37) {
    // RenderParams.FLAG_RENDER_STAMP_ANNOTATIONS / FLAG_RENDER_FREETEXT_ANNOTATIONS
    // (API 37); literals because this module compiles against API 36.
    flags = flags or FLAG_RENDER_STAMP_ANNOTATIONS or FLAG_RENDER_FREETEXT_ANNOTATIONS
  }
  val params = RenderParams.Builder(RenderParams.RENDER_MODE_FOR_DISPLAY).setRenderFlags(flags).build()
  render(target, clip, matrix, params)
}

private const val API_37 = 37
private const val FLAG_RENDER_STAMP_ANNOTATIONS = 8
private const val FLAG_RENDER_FREETEXT_ANNOTATIONS = 16
