import PDFKit

extension PDFView {
  /// Whether the far end of the last page is on screen: its bottom (between
  /// the clearances), or in horizontal layouts its trailing edge (the left
  /// one with `displaysRTL`). In view coordinates, so independent of how
  /// PDFKit mirrors its content.
  func isDocumentEndVisible(topClearance: CGFloat = 0, bottomClearance: CGFloat = 0) -> Bool {
    guard let document, document.pageCount > 0,
          let last = document.page(at: document.pageCount - 1) else { return false }
    let rect = convert(last.bounds(for: displayBox), from: last)
    if displayDirection == .horizontal {
      let edge = displaysRTL ? rect.minX : rect.maxX
      return edge >= bounds.minX - 1 && edge <= bounds.maxX + 1
    }
    return rect.maxY >= bounds.minY + topClearance - 1 && rect.maxY <= bounds.maxY - bottomClearance + 1
  }
}
