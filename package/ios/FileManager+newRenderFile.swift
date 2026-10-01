import Foundation

extension FileManager {
  /// A new, uniquely named file URL in `Caches/BetterPdf/renders` (the
  /// directory is created; the file is not).
  func newRenderFile(pathExtension: String) throws -> URL {
    let directory = try url(for: .cachesDirectory, in: .userDomainMask, appropriateFor: nil, create: true)
      .appendingPathComponent("BetterPdf/renders", isDirectory: true)
    try createDirectory(at: directory, withIntermediateDirectories: true)
    return directory.appendingPathComponent(UUID().uuidString).appendingPathExtension(pathExtension)
  }
}
