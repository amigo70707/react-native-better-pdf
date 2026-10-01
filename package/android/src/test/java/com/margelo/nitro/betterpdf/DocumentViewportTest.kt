package com.margelo.nitro.betterpdf

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DocumentViewportTest {
  private val base = DocumentViewport(PagePoint(2, 10f, 100f), 1.5f, 2..3, isAtEnd = false)

  @Test
  fun `rounding noise is the same position`() {
    assertTrue(base.isCloseTo(base.copy(anchor = PagePoint(2, 10.2f, 99.7f), scale = 1.5004f)))
  }

  @Test
  fun `any real change is a new position`() {
    assertFalse(base.isCloseTo(base.copy(anchor = PagePoint(3, 10f, 100f))))
    assertFalse(base.isCloseTo(base.copy(anchor = PagePoint(2, 10f, 101f))))
    assertFalse(base.isCloseTo(base.copy(scale = 1.51f)))
    assertFalse(base.isCloseTo(base.copy(visiblePages = 2..4)))
    assertFalse(base.isCloseTo(base.copy(isAtEnd = true)))
  }
}
