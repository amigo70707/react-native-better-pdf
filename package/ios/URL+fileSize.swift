import Foundation

extension URL {
  /// Size of the file at this URL in bytes, 0 when it cannot be read.
  func fileSize() -> Int64 {
    let attributes = try? FileManager.default.attributesOfItem(atPath: path)
    return (attributes?[.size] as? NSNumber)?.int64Value ?? 0
  }
}
