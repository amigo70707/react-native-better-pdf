package com.margelo.nitro.betterpdf.outline

/**
 * One outline (bookmark) entry in document order (depth-first). [page] is
 * the zero-based target page, or null when the entry has no destination in
 * this document (a grouping entry, a URL, a broken reference).
 */
data class OutlineEntry(val title: String, val page: Int?, val depth: Int)
