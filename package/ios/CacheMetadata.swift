import Foundation

/// What is known about a downloaded copy: when the server last confirmed it
/// current (download or `304 Not Modified`), and the validators to ask again.
struct CacheMetadata: Codable, Equatable {
  var validatedAtMs: Int64
  var etag: String?
  var lastModified: String?

  /// Whether the copy may be used without asking the server. A nil
  /// `maxAgeMs` never expires; `0` (and NaN) always asks. A clock that went
  /// backwards also asks, so a wrong date can never pin an outdated copy.
  func isFresh(maxAgeMs: Double?, nowMs: Int64) -> Bool {
    guard let maxAgeMs else { return true }
    let ageMs = nowMs - validatedAtMs
    return ageMs >= 0 && Double(ageMs) < maxAgeMs
  }
}
