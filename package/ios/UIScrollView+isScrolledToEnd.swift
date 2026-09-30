import UIKit

extension UIScrollView {
  /// Whether the scroll view is at the end of its content along one axis
  /// (and has been scrolled at all).
  func isScrolledToEnd(horizontal: Bool) -> Bool {
    if horizontal {
      return contentOffset.x > 0 && contentOffset.x + bounds.width >= contentSize.width - 1
    }
    return contentOffset.y > 0 && contentOffset.y + bounds.height >= contentSize.height - 1
  }
}
