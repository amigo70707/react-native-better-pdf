import UIKit

extension UIScrollView {
  /// Whether the scroll view is at the end of its content along one axis
  /// (and has been scrolled at all); vertically, `bottomClearance` is the
  /// space the content may scroll beyond its end.
  func isScrolledToEnd(horizontal: Bool, bottomClearance: CGFloat = 0) -> Bool {
    if horizontal {
      return contentOffset.x > 0 && contentOffset.x + bounds.width >= contentSize.width - 1
    }
    return contentOffset.y > 0 && contentOffset.y + bounds.height >= contentSize.height + bottomClearance - 1
  }
}
