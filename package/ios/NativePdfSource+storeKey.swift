import CryptoKit
import Foundation

extension NativePdfSource {
  /// File name of this source's downloaded copy: a hash of `cacheKey` when
  /// set, else of `uri` and the headers (sorted, so their order does not
  /// matter).
  func storeKey() -> String {
    var material: String
    if let cacheKey {
      material = "key\n\(cacheKey)"
    } else {
      material = "uri\n\(uri)"
      let headers = self.headers ?? [:]
      for name in headers.keys.sorted() {
        material += "\n\(name):\(headers[name] ?? "")"
      }
    }
    return SHA256.hash(data: Data(material.utf8)).map { String(format: "%02x", $0) }.joined()
  }
}
