import PDFKit

extension PDFPage {
  /// Converts a point in PDF page space (origin bottom-left of the box) to
  /// PDF points from the top-left corner of `box` — the convention used by
  /// `onPageTap` on every platform.
  func topLeftPoint(from pagePoint: CGPoint, box: PDFDisplayBox) -> CGPoint {
    let bounds = bounds(for: box)
    return CGPoint(x: pagePoint.x - bounds.minX, y: bounds.maxY - pagePoint.y)
  }
}
