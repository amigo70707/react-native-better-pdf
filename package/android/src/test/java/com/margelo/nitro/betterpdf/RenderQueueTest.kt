package com.margelo.nitro.betterpdf

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

class RenderQueueTest {
  @Test
  fun `work for pages the user left is dropped before it starts`() {
    val queue = RenderQueue<Int>()
    queue.submit(listOf(10, 11, 12, 13, 14))
    assertEquals(10, queue.poll())
    // User jumped from page 10 to page 100.
    queue.submit(listOf(100, 99, 101, 102))
    val rendered = generateSequence { queue.poll().also { queue.finish() } }.toList()
    assertEquals(listOf(100, 99, 101, 102), rendered)
    assertEquals(4, queue.cancelled) // 11, 12, 13, 14
  }

  @Test
  fun `the in-flight request is never queued twice`() {
    val queue = RenderQueue<Int>()
    queue.submit(listOf(1, 2))
    assertEquals(1, queue.poll()) // rendering 1
    queue.submit(listOf(1, 2, 3))
    assertEquals(listOf(2, 3), generateSequence { queue.poll() }.toList())
    queue.finish()
  }

  @Test
  fun `a finished request can be requested again`() {
    val queue = RenderQueue<Int>()
    queue.submit(listOf(1))
    assertEquals(1, queue.poll())
    queue.finish()
    queue.submit(listOf(1))
    assertEquals(1, queue.poll())
  }

  @Test
  fun `resubmitting the same work cancels nothing`() {
    val queue = RenderQueue<Int>()
    queue.submit(listOf(1, 2, 3))
    queue.submit(listOf(1, 2, 3))
    queue.submit(listOf(3, 2, 1))
    assertEquals(0, queue.cancelled)
    assertEquals(3, queue.pendingCount)
  }

  @Test
  fun `the queue length is bounded by the latest submission`() {
    val queue = RenderQueue<Int>()
    repeat(1_000) { frame -> queue.submit(List(6) { frame + it }) }
    assertEquals(6, queue.pendingCount)
  }

  @Test
  fun `close drops pending work, ignores new work and releases a blocked consumer`() {
    val queue = RenderQueue<Int>()
    val released = CountDownLatch(1)
    var taken: Int? = -1
    val consumer = thread {
      taken = queue.take()
      released.countDown()
    }
    Thread.sleep(50)
    queue.close()
    assertTrue(released.await(2, TimeUnit.SECONDS))
    consumer.join()
    assertNull(taken)
    queue.submit(listOf(1))
    assertNull(queue.poll())
    assertEquals(0, queue.pendingCount)
  }

  @Test
  fun `take hands work to a waiting consumer`() {
    val queue = RenderQueue<Int>()
    var taken: Int? = null
    val consumer = thread { taken = queue.take() }
    Thread.sleep(50)
    queue.submit(listOf(7))
    consumer.join(2_000)
    assertEquals(7, taken)
  }

  @Test
  fun `urgent work goes first and survives resubmission`() {
    val queue = RenderQueue<Int>()
    queue.submit(listOf(1, 2, 3))
    queue.submitUrgent(100)
    // A viewport change replaces the render list, not the urgent request.
    queue.submit(listOf(4, 5))
    assertEquals(100, queue.poll())
    queue.finish()
    assertEquals(listOf(4, 5), generateSequence { queue.poll().also { queue.finish() } }.toList())
  }

  @Test
  fun `a newer urgent request replaces one that has not started`() {
    val queue = RenderQueue<Int>()
    queue.submitUrgent(100)
    queue.submitUrgent(101)
    assertEquals(101, queue.poll())
    queue.finish()
    assertNull(queue.poll())
    assertEquals(0, queue.cancelled)
  }

  @Test
  fun `urgent work wakes a waiting consumer`() {
    val queue = RenderQueue<Int>()
    var taken: Int? = null
    val consumer = thread { taken = queue.take() }
    Thread.sleep(50)
    queue.submitUrgent(9)
    consumer.join(2_000)
    assertEquals(9, taken)
  }

  @Test
  fun `close drops urgent work`() {
    val queue = RenderQueue<Int>()
    queue.submitUrgent(9)
    queue.close()
    assertNull(queue.poll())
    queue.submitUrgent(10)
    assertNull(queue.poll())
  }
}
