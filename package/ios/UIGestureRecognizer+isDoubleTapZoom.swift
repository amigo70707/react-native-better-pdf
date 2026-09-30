import UIKit

extension UIGestureRecognizer {
  /// A double-tap recognizer that zooms (PDFKit's), as opposed to the text
  /// interaction's double tap that selects a word.
  var isDoubleTapZoom: Bool {
    guard let tap = self as? UITapGestureRecognizer, tap.numberOfTapsRequired == 2 else { return false }
    return !(delegate is UIInteraction)
  }
}
