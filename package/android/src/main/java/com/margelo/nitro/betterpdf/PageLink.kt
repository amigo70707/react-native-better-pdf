package com.margelo.nitro.betterpdf

/**
 * A tappable link area on a page: read from the file
 * (`outline.PdfLinkReader`), or from PdfRenderer when the page is first
 * rendered where the file cannot be parsed (encrypted).
 */
sealed interface PageLink {
  /** Areas the link covers; a link spanning several lines has several. */
  val bounds: List<PageRect>

  fun contains(x: Float, y: Float): Boolean = bounds.any { it.contains(x, y) }

  /** Points outside the document; reported to JS as `onLinkPress`. */
  data class Url(override val bounds: List<PageRect>, val url: String) : PageLink

  /** Points to another page of the document; followed natively. */
  data class Goto(override val bounds: List<PageRect>, val page: Int) : PageLink
}
