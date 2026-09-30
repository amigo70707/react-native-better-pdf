package com.margelo.nitro.betterpdf

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LruByteCacheTest {
  private class Blob(val bytes: Long)

  private val evicted = mutableListOf<Int>()
  private fun cache(budget: Long) = LruByteCache<Int, Blob>(budget, { it.bytes }, { key, _ -> evicted += key })

  @Test
  fun `evicts least recently used entries over budget`() {
    val cache = cache(budget = 30)
    cache.put(1, Blob(10))
    cache.put(2, Blob(10))
    cache.put(3, Blob(10))
    cache.get(1) // 1 becomes most recent
    cache.put(4, Blob(10))
    assertEquals(listOf(2), evicted)
    assertEquals(30, cache.sizeBytes)
    assertNull(cache.get(2))
  }

  @Test
  fun `protected keys survive even when the budget is exceeded`() {
    val cache = cache(budget = 15)
    cache.put(1, Blob(10))
    cache.put(2, Blob(10), protectedKeys = setOf(1))
    assertTrue(cache.contains(1))
    assertTrue(cache.contains(2))
    assertEquals(20, cache.sizeBytes)
    assertEquals(emptyList<Int>(), evicted)
  }

  @Test
  fun `replacing a key releases the old value and keeps the size exact`() {
    val cache = cache(budget = 100)
    cache.put(1, Blob(10))
    cache.put(1, Blob(25))
    assertEquals(listOf(1), evicted)
    assertEquals(25, cache.sizeBytes)
    assertEquals(1, cache.count)
  }

  @Test
  fun `trimTo and updateBudget shrink to the target`() {
    val cache = cache(budget = 100)
    (1..5).forEach { cache.put(it, Blob(10)) }
    cache.trimTo(20, protectedKeys = setOf(1))
    assertTrue(cache.contains(1))
    assertEquals(20, cache.sizeBytes)
    cache.updateBudget(0)
    assertEquals(0, cache.count)
    assertEquals(0, cache.sizeBytes)
  }

  @Test
  fun `clear evicts everything exactly once`() {
    val cache = cache(budget = 100)
    (1..3).forEach { cache.put(it, Blob(1)) }
    cache.clear()
    assertEquals(listOf(1, 2, 3), evicted.sorted())
    assertFalse(cache.contains(1))
    assertEquals(0, cache.sizeBytes)
  }

  @Test
  fun `a single entry larger than the budget is admitted, then evicted by the next put`() {
    val cache = cache(budget = 5)
    cache.put(1, Blob(50))
    assertTrue(cache.contains(1))
    cache.put(2, Blob(1))
    assertFalse(cache.contains(1))
  }
}
