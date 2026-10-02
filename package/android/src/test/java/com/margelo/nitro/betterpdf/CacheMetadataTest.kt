package com.margelo.nitro.betterpdf

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CacheMetadataTest {
  private val metadata = CacheMetadata(validatedAtMs = 1_000, etag = null, lastModified = null)

  @Test
  fun `without maxAgeMs a copy never expires`() {
    assertTrue(metadata.isFresh(maxAgeMs = null, nowMs = Long.MAX_VALUE))
  }

  @Test
  fun `a copy is fresh while younger than maxAgeMs`() {
    assertTrue(metadata.isFresh(maxAgeMs = 500.0, nowMs = 1_499))
    assertFalse(metadata.isFresh(maxAgeMs = 500.0, nowMs = 1_500))
  }

  @Test
  fun `zero, negative and NaN always revalidate`() {
    assertFalse(metadata.isFresh(maxAgeMs = 0.0, nowMs = 1_000))
    assertFalse(metadata.isFresh(maxAgeMs = -1.0, nowMs = 1_000))
    assertFalse(metadata.isFresh(maxAgeMs = Double.NaN, nowMs = 1_000))
  }

  @Test
  fun `infinity never expires`() {
    assertTrue(metadata.isFresh(maxAgeMs = Double.POSITIVE_INFINITY, nowMs = Long.MAX_VALUE))
  }

  @Test
  fun `a clock that went backwards revalidates`() {
    assertFalse(metadata.isFresh(maxAgeMs = 60_000.0, nowMs = 999))
  }
}
