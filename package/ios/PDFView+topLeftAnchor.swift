import PDFKit

extension PDFView {
  /// The page nearest to the top-left corner of the area below `topClearance`
  /// (the view's top-left corner without one), and that corner in PDF points
  /// from the page's top-left (negative or past the page's size when the
  /// corner is outside it). Inverse of `scroll(_:topLeftTo:on:topClearance:)`.
  func topLeftAnchor(topClearance: CGFloat = 0) -> (page: PDFPage, point: CGPoint)? {
    let corner = CGPoint(x: bounds.minX, y: bounds.minY + topClearance)
    guard let page = page(for: corner, nearest: true) else { return nil }
    return (page, page.topLeftPoint(from: convert(corner, to: page), box: displayBox))
  }
}
