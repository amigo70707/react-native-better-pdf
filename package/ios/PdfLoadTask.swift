import Foundation

/// Handle for an in-flight load. `cancel()` is idempotent and safe from any thread.
final class PdfLoadTask {
  private let lock = NSLock()
  private var cancelled = false
  private var subscription: DocumentCache.Subscription?

  var isCancelled: Bool { lock.withLock { cancelled } }

  func cancel() {
    let subscription = lock.withLock { () -> DocumentCache.Subscription? in
      cancelled = true
      return self.subscription
    }
    subscription?.cancel()
  }

  /// Ties a cache request to this task; false (and nothing attached) once
  /// cancelled.
  func attach(_ subscription: DocumentCache.Subscription) -> Bool {
    return lock.withLock {
      if cancelled { return false }
      self.subscription = subscription
      return true
    }
  }
}
