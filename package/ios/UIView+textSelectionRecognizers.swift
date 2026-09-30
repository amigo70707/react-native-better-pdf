import UIKit

extension UIView {
  /// The recognizers that select text in this view's subtree: those of every
  /// `UITextInteraction`, as UIKit lists them for failure requirements (long
  /// press, word and tap-and-a-half taps, handle drags). PDFKit hosts its
  /// text selection in such interactions; link taps are not among them.
  func textSelectionRecognizers() -> [UIGestureRecognizer] {
    var result: [UIGestureRecognizer] = []
    var queue: [UIView] = [self]
    while !queue.isEmpty {
      let view = queue.removeFirst()
      for case let interaction as UITextInteraction in view.interactions {
        result.append(contentsOf: interaction.gesturesForFailureRequirements)
      }
      queue.append(contentsOf: view.subviews)
    }
    return result
  }
}
