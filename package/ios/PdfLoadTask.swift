import Foundation

/// Handle for an in-flight load. `cancel()` is idempotent and safe from any thread.
final class PdfLoadTask {
  private let lock = NSLock()
  private var cancelled = false
  private var download: URLSessionDownloadTask?

  var isCancelled: Bool { lock.withLock { cancelled } }

  func cancel() {
    let download = lock.withLock { () -> URLSessionDownloadTask? in
      cancelled = true
      return self.download
    }
    download?.cancel()
  }

  func attach(_ task: URLSessionDownloadTask) -> Bool {
    return lock.withLock {
      if cancelled { return false }
      download = task
      return true
    }
  }
}
