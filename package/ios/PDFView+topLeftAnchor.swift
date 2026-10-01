import PDFKit

extension PDFView {
  /// The page nearest to the view's top-left corner, and that corner in PDF
  /// points from the page's top-left (negative or past the page's size when
  /// the corner is outside it). Inverse of `scroll(_:topLeftTo:on:)`.
  func topLeftAnchor() -> (page: PDFPage, point: CGPoint)? {
    let corner = CGPoint(x: bounds.minX, y: bounds.minY)
    guard let page = page(for: corner, nearest: true) else { return nil }
    return (page, page.topLeftPoint(from: convert(corner, to: page), box: displayBox))
  }
}
