import Foundation

extension URL {
  /// Whether the file starts like a PDF: `%PDF-` within its first 1024
  /// bytes, where readers accept it. Rejects error pages and login forms that
  /// servers and captive portals return with a `200` status. Blocking.
  func hasPdfHeader() -> Bool {
    guard let handle = try? FileHandle(forReadingFrom: self) else { return false }
    defer { try? handle.close() }
    guard let head = try? handle.read(upToCount: 1024) else { return false }
    return head.range(of: Data("%PDF-".utf8)) != nil
  }
}
