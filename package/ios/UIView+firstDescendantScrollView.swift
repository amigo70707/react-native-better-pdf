import UIKit

extension UIView {
  /// The first `UIScrollView` in this view's subtree (breadth-first),
  /// including this view itself.
  func firstDescendantScrollView() -> UIScrollView? {
    var queue: [UIView] = [self]
    while !queue.isEmpty {
      let view = queue.removeFirst()
      if let scrollView = view as? UIScrollView { return scrollView }
      queue.append(contentsOf: view.subviews)
    }
    return nil
  }
}
