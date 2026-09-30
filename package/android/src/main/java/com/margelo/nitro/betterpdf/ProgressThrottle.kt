package com.margelo.nitro.betterpdf

/**
 * Decides which download progress updates reach JS: at most one per
 * [intervalMs], always the final one, never the same byte count twice.
 * Bounds `onLoadProgress` to ~10 events per second regardless of file size or
 * network speed. Owned by one thread (the document's worker).
 */
class ProgressThrottle(private val intervalMs: Long = 100) {
  private var lastEmitMs: Long? = null
  private var lastLoadedBytes = -1L

  fun shouldEmit(nowMs: Long, loadedBytes: Long, isComplete: Boolean): Boolean {
    if (loadedBytes == lastLoadedBytes) return false
    val last = lastEmitMs
    if (!isComplete && last != null && nowMs - last < intervalMs) return false
    lastEmitMs = nowMs
    lastLoadedBytes = loadedBytes
    return true
  }
}
