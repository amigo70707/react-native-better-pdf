import UIKit

extension UIView {
  /// Double-tap recognizers on this view and all of its descendants.
  func doubleTapRecognizers() -> [UITapGestureRecognizer] {
    var result: [UITapGestureRecognizer] = []
    var queue: [UIView] = [self]
    while !queue.isEmpty {
      let view = queue.removeFirst()
      for recognizer in view.gestureRecognizers ?? [] {
        if let tap = recognizer as? UITapGestureRecognizer, tap.numberOfTapsRequired == 2 {
          result.append(tap)
        }
      }
      queue.append(contentsOf: view.subviews)
    }
    return result
  }
}
