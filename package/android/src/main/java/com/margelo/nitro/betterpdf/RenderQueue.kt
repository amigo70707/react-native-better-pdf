package com.margelo.nitro.betterpdf

/**
 * The render work queue between the UI thread (producer) and the document's
 * render thread (consumer).
 *
 * The UI thread submits the *complete* list of wanted work, highest priority
 * first, on every viewport change. The new list replaces the old one, so work
 * for pages the user has scrolled away from is dropped before it starts —
 * the only effective cancellation, since `PdfRenderer.Page.render` cannot be
 * interrupted. The request currently being rendered is never queued twice.
 *
 * One urgent request (a text selection the user is waiting for) can jump the
 * queue: it is taken before any pending work, [submit] never drops it, and
 * only a newer urgent request replaces it before it starts.
 *
 * Ordered requests (changes to the document's form) come before both, first
 * in first out, and are never dropped or replaced.
 *
 * Pure Kotlin so its guarantees are unit tested (`RenderQueueTest`).
 */
class RenderQueue<T : Any> {
  private val lock = Object()
  private var pending: List<T> = emptyList()
  private var urgent: T? = null
  private val ordered = ArrayDeque<T>()
  private var inFlight: T? = null

  @Volatile
  var isClosed = false
    private set

  /** Requests dropped before they started. */
  @Volatile
  var cancelled = 0L
    private set

  /** Replaces the queue with [requests]. Ignored once closed. */
  fun submit(requests: List<T>) {
    synchronized(lock) {
      if (isClosed) return
      val current = inFlight
      val wanted = if (current == null) requests else requests.filter { it != current }
      if (pending.isNotEmpty()) {
        val wantedSet = wanted.toHashSet()
        cancelled += pending.count { it !in wantedSet }
      }
      pending = wanted
      lock.notifyAll()
    }
  }

  /**
   * Queues [request] ahead of all pending work, replacing an urgent request
   * that has not started. Ignored once closed.
   */
  fun submitUrgent(request: T) {
    synchronized(lock) {
      if (isClosed) return
      urgent = request
      lock.notifyAll()
    }
  }

  /** Queues [request] ahead of all other work, after earlier ordered requests. Ignored once closed. */
  fun submitInOrder(request: T) {
    synchronized(lock) {
      if (isClosed) return
      ordered.addLast(request)
      lock.notifyAll()
    }
  }

  /** Blocks until work is available; `null` once closed. Marks the result in flight. */
  fun take(): T? {
    synchronized(lock) {
      while (pending.isEmpty() && urgent == null && ordered.isEmpty() && !isClosed) lock.wait()
      return pollLocked()
    }
  }

  /** Non-blocking [take]. */
  fun poll(): T? = synchronized(lock) { pollLocked() }

  /** Call when the in-flight request finished (successfully or not). */
  fun finish() {
    synchronized(lock) { inFlight = null }
  }

  /** Drops all pending work and wakes the consumer; returns the ordered requests that will not run. */
  fun close(): List<T> {
    synchronized(lock) {
      isClosed = true
      pending = emptyList()
      urgent = null
      val dropped = ordered.toList()
      ordered.clear()
      lock.notifyAll()
      return dropped
    }
  }

  val pendingCount: Int get() = synchronized(lock) { pending.size }

  private fun pollLocked(): T? {
    if (isClosed) return null
    ordered.removeFirstOrNull()?.let {
      inFlight = it
      return it
    }
    urgent?.let {
      urgent = null
      inFlight = it
      return it
    }
    if (pending.isEmpty()) return null
    val next = pending.first()
    pending = pending.drop(1)
    inFlight = next
    return next
  }
}
