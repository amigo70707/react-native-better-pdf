package com.margelo.nitro.betterpdf

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProgressThrottleTest {
  @Test
  fun `the first update is emitted immediately`() {
    assertTrue(ProgressThrottle().shouldEmit(nowMs = 5, loadedBytes = 1, isComplete = false))
  }

  @Test
  fun `updates within the interval are dropped`() {
    val throttle = ProgressThrottle(intervalMs = 100)
    assertTrue(throttle.shouldEmit(0, 10, false))
    assertFalse(throttle.shouldEmit(50, 20, false))
    assertFalse(throttle.shouldEmit(99, 30, false))
    assertTrue(throttle.shouldEmit(100, 40, false))
  }

  @Test
  fun `completion is always emitted, even inside the interval`() {
    val throttle = ProgressThrottle(intervalMs = 100)
    assertTrue(throttle.shouldEmit(0, 10, false))
    assertTrue(throttle.shouldEmit(1, 1_000, true))
  }

  @Test
  fun `the same byte count is never emitted twice`() {
    val throttle = ProgressThrottle(intervalMs = 100)
    assertTrue(throttle.shouldEmit(0, 1_000, false))
    assertFalse(throttle.shouldEmit(500, 1_000, true))
  }

  @Test
  fun `a fast download of any size produces a bounded number of events`() {
    val throttle = ProgressThrottle(intervalMs = 100)
    // 400 MB in 64 KB chunks over 2 seconds.
    val chunks = 400 * 1024 / 64
    var emitted = 0
    for (chunk in 1..chunks) {
      val nowMs = chunk * 2_000L / chunks
      if (throttle.shouldEmit(nowMs, chunk * 65_536L, chunk == chunks)) emitted++
    }
    assertEquals(21, emitted) // t = 0.0 … 2.0 s every 100 ms, the last being completion
  }
}
