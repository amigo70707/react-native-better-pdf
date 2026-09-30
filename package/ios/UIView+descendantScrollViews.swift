import UIKit

extension UIView {
  /// Every `UIScrollView` in this view's subtree, including this view.
  func descendantScrollViews() -> [UIScrollView] {
    var result: [UIScrollView] = []
    var queue: [UIView] = [self]
    while !queue.isEmpty {
      let view = queue.removeFirst()
      if let scrollView = view as? UIScrollView { result.append(scrollView) }
      queue.append(contentsOf: view.subviews)
    }
    return result
  }
}
