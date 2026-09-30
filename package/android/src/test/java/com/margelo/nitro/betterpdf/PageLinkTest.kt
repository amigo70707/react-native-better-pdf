package com.margelo.nitro.betterpdf

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PageLinkTest {
  private val url = PageLink.Url(listOf(PageRect(10f, 20f, 110f, 40f)), "https://example.com")

  @Test
  fun `a point inside the bounds hits, edges included`() {
    assertTrue(url.contains(50f, 30f))
    assertTrue(url.contains(10f, 20f))
    assertTrue(url.contains(110f, 40f))
  }

  @Test
  fun `a point outside the bounds misses`() {
    assertFalse(url.contains(9.9f, 30f))
    assertFalse(url.contains(50f, 40.1f))
  }

  @Test
  fun `a link spanning several lines hits on any of its rectangles`() {
    val wrapped = PageLink.Goto(listOf(PageRect(300f, 100f, 500f, 112f), PageRect(50f, 112f, 120f, 124f)), page = 3)
    assertTrue(wrapped.contains(400f, 105f))
    assertTrue(wrapped.contains(60f, 120f))
    assertFalse(wrapped.contains(200f, 120f))
  }

  @Test
  fun `the first matching link wins, so goto links listed first take precedence`() {
    val goto = PageLink.Goto(listOf(PageRect(0f, 0f, 100f, 100f)), page = 2)
    val links: List<PageLink> = listOf(goto, url)
    assertEquals(goto, links.firstOrNull { it.contains(50f, 30f) })
  }
}
