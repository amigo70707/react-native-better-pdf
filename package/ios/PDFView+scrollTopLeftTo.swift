import PDFKit
import UIKit

extension PDFView {
  /// Scrolls `scrollView` (this view's own, in continuous layouts) so
  /// `point` — PDF points from `page`'s top-left corner — is at the view's
  /// top-left corner, clamped to the scrollable area. Inverse of
  /// `topLeftAnchor()`.
  func scroll(_ scrollView: UIScrollView, topLeftTo point: CGPoint, on page: PDFPage) {
    let viewPoint = convert(page.pagePoint(fromTopLeft: point, box: displayBox), from: page)
    let inset = scrollView.adjustedContentInset
    let maxX = max(-inset.left, scrollView.contentSize.width + inset.right - scrollView.bounds.width)
    let maxY = max(-inset.top, scrollView.contentSize.height + inset.bottom - scrollView.bounds.height)
    let target = CGPoint(
      x: min(max(scrollView.contentOffset.x + viewPoint.x - bounds.minX, -inset.left), maxX),
      y: min(max(scrollView.contentOffset.y + viewPoint.y - bounds.minY, -inset.top), maxY)
    )
    scrollView.setContentOffset(target, animated: false)
  }
}
