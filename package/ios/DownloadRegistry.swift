import Foundation

/// Thread-safe map of cache key → downloaded file for this process.
final class DownloadRegistry {
  private let lock = NSLock()
  private var files: [String: URL] = [:]

  func file(for key: String) -> URL? { lock.withLock { files[key] } }
  func set(_ url: URL, for key: String) { lock.withLock { files[key] = url } }
}
