package com.margelo.nitro.betterpdf

import java.util.TreeMap

/**
 * The matches of one search, in document order (page, then position on the
 * page), and which one is current. Pages arrive in any order (the search
 * starts at the reader's page and wraps); the current match is tracked by
 * identity, so its index stays right as earlier pages arrive.
 *
 * Pure Kotlin, unit tested (`SearchResultsTest`). Owned by the UI thread.
 */
class SearchResults {
  private val pages = TreeMap<Int, List<List<PageRect>>>()
  private var current: SearchMatch? = null

  /** Matches found so far. */
  var count = 0
    private set

  val currentMatch: SearchMatch? get() = current

  /** Zero-based position of the current match among all matches so far. */
  val currentIndex: Int?
    get() {
      val match = current ?: return null
      return pages.headMap(match.page).values.sumOf { it.size } + match.indexOnPage
    }

  /** Records [page]'s matches; pages without matches may be skipped. */
  fun add(page: Int, matches: List<List<PageRect>>) {
    if (matches.isEmpty()) return
    pages.put(page, matches)?.let { count -= it.size }
    count += matches.size
  }

  fun matchesOn(page: Int): List<List<PageRect>> = pages[page].orEmpty()

  /** Makes the first match at or after [page] current (wrapping to the first match). */
  fun selectFirstAtOrAfter(page: Int): SearchMatch? {
    val entry = pages.ceilingEntry(page) ?: pages.firstEntry() ?: return null
    return select(entry.key, 0)
  }

  /** The next match, wrapping to the first. */
  fun next(): SearchMatch? {
    val match = current ?: return pages.firstEntry()?.let { select(it.key, 0) }
    if (match.indexOnPage + 1 < matchesOn(match.page).size) return select(match.page, match.indexOnPage + 1)
    val entry = pages.higherEntry(match.page) ?: pages.firstEntry() ?: return null
    return select(entry.key, 0)
  }

  /** The previous match, wrapping to the last. */
  fun previous(): SearchMatch? {
    val match = current ?: return pages.lastEntry()?.let { select(it.key, it.value.size - 1) }
    if (match.indexOnPage > 0) return select(match.page, match.indexOnPage - 1)
    val entry = pages.lowerEntry(match.page) ?: pages.lastEntry() ?: return null
    return select(entry.key, entry.value.size - 1)
  }

  private fun select(page: Int, indexOnPage: Int): SearchMatch {
    val match = SearchMatch(page, indexOnPage, pages.getValue(page)[indexOnPage])
    current = match
    return match
  }
}
