package com.margelo.nitro.betterpdf

import android.graphics.Point
import android.graphics.pdf.models.selection.PageSelection
import android.graphics.pdf.models.selection.SelectionBoundary
import android.os.Build
import androidx.annotation.RequiresExtension
import kotlin.math.roundToInt

/**
 * The platform's selection result as a [PageTextSelection]; shared by both
 * renderers (`PdfRenderer` and `PdfRendererPreV` return the same models).
 * Null when a boundary cannot be expressed.
 */
@RequiresExtension(extension = Build.VERSION_CODES.S, version = 13)
internal fun PageSelection.toPageTextSelection(page: Int): PageTextSelection? {
  val contents = selectedTextContents
  return PageTextSelection.of(
    page = page,
    start = start.toBoundary() ?: return null,
    stop = stop.toBoundary() ?: return null,
    // The platform ends lines with "\r\n"; PDFKit and JS use "\n".
    text = contents.joinToString("") { it.text }.replace(LINE_BREAK, "\n"),
    bounds = contents.flatMap { content -> content.bounds.map { it.toPageRect() } },
  )
}

@RequiresExtension(extension = Build.VERSION_CODES.S, version = 13)
internal fun TextSelectionQuery.Boundary.toSelectionBoundary(): SelectionBoundary = when (this) {
  is TextSelectionQuery.Boundary.Index -> SelectionBoundary(index)
  // The platform takes whole PDF points.
  is TextSelectionQuery.Boundary.Point -> SelectionBoundary(Point(x.roundToInt(), y.roundToInt()))
}

/** A resolved boundary carries an index or a point (index -1), never neither in practice. */
@RequiresExtension(extension = Build.VERSION_CODES.S, version = 13)
private fun SelectionBoundary.toBoundary(): TextSelectionQuery.Boundary? {
  if (index >= 0) return TextSelectionQuery.Boundary.Index(index)
  val point = point ?: return null
  return TextSelectionQuery.Boundary.Point(point.x.toFloat(), point.y.toFloat())
}

private val LINE_BREAK = Regex("\r\n?")
