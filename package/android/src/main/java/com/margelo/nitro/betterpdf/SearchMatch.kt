package com.margelo.nitro.betterpdf

/** One search match: its page, its position among that page's matches, and the rectangles it covers. */
data class SearchMatch(val page: Int, val indexOnPage: Int, val rects: List<PageRect>)
