import PDFKit

extension PDFDocument {
  /// Every page's displayed size (see `PDFPage.displaySize`), by zero-based
  /// page. Call on the thread that owns the document.
  func pageSizes() -> [PdfPageSize] {
    return (0..<pageCount).compactMap { page(at: $0)?.displaySize }
      .map { PdfPageSize(width: Double($0.width), height: Double($0.height)) }
  }
}
