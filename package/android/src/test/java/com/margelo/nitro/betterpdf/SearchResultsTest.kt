package com.margelo.nitro.betterpdf

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SearchResultsTest {
  private fun rect(n: Int) = listOf(PageRect(n.toFloat(), 0f, n + 1f, 1f))

  private fun results(vararg pages: Pair<Int, Int>) = SearchResults().apply {
    for ((page, count) in pages) add(page, List(count) { rect(it) })
  }

  @Test
  fun `counts and orders matches across pages added out of order`() {
    val results = results(5 to 2, 1 to 1, 9 to 3)
    assertEquals(6, results.count)
    assertEquals(SearchMatch(5, 0, rect(0)), results.selectFirstAtOrAfter(3))
    // Page 1's match comes first in document order.
    assertEquals(1, results.currentIndex)
  }

  @Test
  fun `the current index follows its match as earlier pages arrive`() {
    val results = results(5 to 1)
    results.selectFirstAtOrAfter(5)
    assertEquals(0, results.currentIndex)
    results.add(2, List(3) { rect(it) })
    assertEquals(3, results.currentIndex)
    assertEquals(5, results.currentMatch?.page)
  }

  @Test
  fun `next and previous walk matches in order and wrap`() {
    val results = results(2 to 2, 7 to 1)
    assertEquals(SearchMatch(2, 0, rect(0)), results.next())
    assertEquals(SearchMatch(2, 1, rect(1)), results.next())
    assertEquals(SearchMatch(7, 0, rect(0)), results.next())
    assertEquals(SearchMatch(2, 0, rect(0)), results.next())
    assertEquals(SearchMatch(7, 0, rect(0)), results.previous())
    assertEquals(SearchMatch(2, 1, rect(1)), results.previous())
  }

  @Test
  fun `previous without a current match starts at the last one`() {
    assertEquals(SearchMatch(7, 0, rect(0)), results(2 to 2, 7 to 1).previous())
  }

  @Test
  fun `selecting past the last match wraps to the first`() {
    assertEquals(SearchMatch(2, 0, rect(0)), results(2 to 1, 4 to 1).selectFirstAtOrAfter(9))
  }

  @Test
  fun `no matches, no current`() {
    val results = SearchResults()
    results.add(3, emptyList())
    assertEquals(0, results.count)
    assertNull(results.next())
    assertNull(results.previous())
    assertNull(results.selectFirstAtOrAfter(0))
    assertNull(results.currentIndex)
  }

  @Test
  fun `re-adding a page replaces its matches`() {
    val results = results(1 to 2)
    results.add(1, List(3) { rect(it) })
    assertEquals(3, results.count)
  }
}
