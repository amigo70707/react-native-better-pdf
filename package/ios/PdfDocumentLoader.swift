import CryptoKit
import Foundation
import PDFKit

/// Resolves a `NativePdfSource` to a local file and opens it as a
/// `PDFDocument`, entirely off the main thread.
///
/// Remote documents are downloaded with `URLSession` into
/// `Caches/BetterPdf/<sha256(uri + headers)>.pdf`. A downloaded file is reused
/// for the rest of the process lifetime (so a password retry or a remount does
/// not download again) and re-downloaded on the next launch.
final class PdfDocumentLoader {
  private static let downloadedFiles = DownloadRegistry()
  private static let workQueue = DispatchQueue(label: "com.betterpdf.load", qos: .userInitiated, attributes: .concurrent)

  /// Loads the document. `progress` (remote downloads only, throttled) and
  /// `completion` are called on the main queue, unless cancelled; the final
  /// progress update always precedes `completion`.
  static func load(
    source: NativePdfSource,
    password: String?,
    progress: @escaping (_ loadedBytes: Int64, _ totalBytes: Int64?) -> Void,
    completion: @escaping (Result<PDFDocument, PdfLoadFailure>) -> Void
  ) -> PdfLoadTask {
    let task = PdfLoadTask()
    let finish: (Result<PDFDocument, PdfLoadFailure>) -> Void = { result in
      DispatchQueue.main.async {
        if !task.isCancelled { completion(result) }
      }
    }
    let throttle = ProgressThrottle()
    let reportProgress: (Int64, Int64?, Bool) -> Void = { loadedBytes, totalBytes, isComplete in
      let nowMs = DispatchTime.now().uptimeNanoseconds / 1_000_000
      guard throttle.shouldEmit(nowMs: nowMs, loadedBytes: loadedBytes, isComplete: isComplete) else { return }
      DispatchQueue.main.async {
        if !task.isCancelled { progress(loadedBytes, totalBytes) }
      }
    }
    workQueue.async {
      resolveLocalFile(source: source, task: task, progress: reportProgress) { fileResult in
        // Downloads complete on URLSession's delegate queue; parse on ours.
        workQueue.async {
          if task.isCancelled { return }
          switch fileResult {
          case .failure(let failure):
            finish(.failure(failure))
          case .success(let url):
            finish(openDocument(at: url, password: password))
          }
        }
      }
    }
    return task
  }

  // MARK: - Source resolution

  private static func resolveLocalFile(
    source: NativePdfSource,
    task: PdfLoadTask,
    progress: @escaping (Int64, Int64?, Bool) -> Void,
    completion: @escaping (Result<URL, PdfLoadFailure>) -> Void
  ) {
    let uri = source.uri
    if uri.isEmpty {
      return completion(.failure(.invalidSource(uri)))
    }
    if uri.hasPrefix("/") {
      return completion(existingFile(URL(fileURLWithPath: uri)))
    }
    guard let url = URL(string: uri), let scheme = url.scheme?.lowercased() else {
      return completion(.failure(.invalidSource(uri)))
    }
    switch scheme {
    case "file":
      completion(existingFile(url))
    case "http", "https":
      download(url: url, headers: source.headers ?? [:], task: task, progress: progress, completion: completion)
    default:
      completion(.failure(.invalidSource(uri)))
    }
  }

  private static func existingFile(_ url: URL) -> Result<URL, PdfLoadFailure> {
    var isDirectory: ObjCBool = false
    guard FileManager.default.fileExists(atPath: url.path, isDirectory: &isDirectory), !isDirectory.boolValue else {
      return .failure(.fileNotFound(url.path))
    }
    return .success(url)
  }

  private static func download(
    url: URL,
    headers: [String: String],
    task: PdfLoadTask,
    progress: @escaping (Int64, Int64?, Bool) -> Void,
    completion: @escaping (Result<URL, PdfLoadFailure>) -> Void
  ) {
    let key = cacheKey(url: url, headers: headers)
    if let cached = downloadedFiles.file(for: key), FileManager.default.fileExists(atPath: cached.path) {
      return completion(.success(cached))
    }

    var request = URLRequest(url: url)
    for (name, value) in headers {
      request.setValue(value, forHTTPHeaderField: name)
    }

    var progressObservation: NSKeyValueObservation?
    let downloadTask = URLSession.shared.downloadTask(with: request) { tempURL, response, error in
      progressObservation?.invalidate()
      if let error {
        let cancelled = (error as? URLError)?.code == .cancelled
        if !cancelled {
          completion(.failure(PdfLoadFailure(
            code: .networkError,
            message: "Downloading \(url.absoluteString) failed",
            nativeDescription: error.localizedDescription
          )))
        }
        return
      }
      if let http = response as? HTTPURLResponse, !(200..<300).contains(http.statusCode) {
        return completion(.failure(PdfLoadFailure(
          code: .httpError,
          message: "Downloading \(url.absoluteString) failed with HTTP \(http.statusCode)",
          httpStatus: http.statusCode
        )))
      }
      guard let tempURL else {
        return completion(.failure(PdfLoadFailure(code: .networkError, message: "Download produced no file")))
      }
      let downloadedBytes = tempURL.fileSize()
      progress(downloadedBytes, response.flatMap { reportedTotalBytes($0.expectedContentLength, loaded: downloadedBytes) }, true)
      do {
        let destination = try cacheDirectory().appendingPathComponent("\(key).pdf")
        // The temp file is deleted when this handler returns, so move it synchronously.
        if FileManager.default.fileExists(atPath: destination.path) {
          _ = try FileManager.default.replaceItemAt(destination, withItemAt: tempURL)
        } else {
          try FileManager.default.moveItem(at: tempURL, to: destination)
        }
        downloadedFiles.set(destination, for: key)
        completion(.success(destination))
      } catch {
        completion(.failure(PdfLoadFailure(
          code: .networkError,
          message: "Could not store the downloaded PDF",
          nativeDescription: error.localizedDescription
        )))
      }
    }
    progressObservation = downloadTask.observe(\.countOfBytesReceived, options: []) { observed, _ in
      let loadedBytes = observed.countOfBytesReceived
      progress(loadedBytes, reportedTotalBytes(observed.countOfBytesExpectedToReceive, loaded: loadedBytes), false)
    }
    if task.attach(downloadTask) {
      downloadTask.resume()
    } else {
      progressObservation?.invalidate()
    }
  }

  /// The server-reported size, unless unknown (-1) or already exceeded
  /// (transparently decompressed responses report the compressed size).
  private static func reportedTotalBytes(_ expected: Int64, loaded: Int64) -> Int64? {
    return expected >= 0 && expected >= loaded ? expected : nil
  }

  private static func cacheDirectory() throws -> URL {
    let caches = try FileManager.default.url(for: .cachesDirectory, in: .userDomainMask, appropriateFor: nil, create: true)
    let directory = caches.appendingPathComponent("BetterPdf", isDirectory: true)
    try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
    return directory
  }

  private static func cacheKey(url: URL, headers: [String: String]) -> String {
    var material = url.absoluteString
    for name in headers.keys.sorted() {
      material += "\n\(name):\(headers[name] ?? "")"
    }
    return SHA256.hash(data: Data(material.utf8)).map { String(format: "%02x", $0) }.joined()
  }

  // MARK: - Opening

  private static func openDocument(at url: URL, password: String?) -> Result<PDFDocument, PdfLoadFailure> {
    // PDFDocument(url:) parses the cross-reference table synchronously; this
    // is why the whole load runs on `workQueue`.
    guard let document = PDFDocument(url: url) else {
      return .failure(.invalidPdf(url.path))
    }
    if document.isLocked {
      guard let password, !password.isEmpty else {
        return .failure(PdfLoadFailure(code: .passwordRequired, message: "This PDF is password protected"))
      }
      guard document.unlock(withPassword: password) else {
        return .failure(PdfLoadFailure(code: .invalidPassword, message: "The password is incorrect"))
      }
    }
    if document.pageCount == 0 {
      return .failure(PdfLoadFailure(code: .invalidPdf, message: "The PDF at \(url.path) has no pages"))
    }
    return .success(document)
  }
}
