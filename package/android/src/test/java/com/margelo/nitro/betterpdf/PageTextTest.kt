package com.margelo.nitro.betterpdf

import org.junit.Assert.assertEquals
import org.junit.Test

class PageTextTest {
  @Test
  fun `runs are joined, line ends normalised and blank space trimmed`() =
    assertEquals("Page 1\nfirst line\nsecond", joinPageText(listOf("  Page 1\r\n", "first line\r", "\nsecond\r\n ")))

  @Test
  fun `an empty page has empty text`() = assertEquals("", joinPageText(emptyList()))
}
