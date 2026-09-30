import PDFKit

extension PDFView {
  /// Whether `page` lies entirely inside the view, in view coordinates (so
  /// independent of how PDFKit orders or mirrors its scroll content).
  func isPageFullyVisible(_ page: PDFPage) -> Bool {
    let rect = convert(page.bounds(for: displayBox), from: page)
    return bounds.insetBy(dx: -1, dy: -1).contains(rect)
  }
}
