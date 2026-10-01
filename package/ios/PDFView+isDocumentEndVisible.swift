import PDFKit

extension PDFView {
  /// Whether the far end of the last page is on screen: its bottom, or in
  /// horizontal layouts its trailing edge (the left one with `displaysRTL`).
  /// In view coordinates, so independent of how PDFKit mirrors its content.
  func isDocumentEndVisible() -> Bool {
    guard let document, document.pageCount > 0,
          let last = document.page(at: document.pageCount - 1) else { return false }
    let rect = convert(last.bounds(for: displayBox), from: last)
    if displayDirection == .horizontal {
      let edge = displaysRTL ? rect.minX : rect.maxX
      return edge >= bounds.minX - 1 && edge <= bounds.maxX + 1
    }
    return rect.maxY >= bounds.minY - 1 && rect.maxY <= bounds.maxY + 1
  }
}
