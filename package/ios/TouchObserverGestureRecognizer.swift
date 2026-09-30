import UIKit
import UIKit.UIGestureRecognizerSubclass

/// Tells whether a finger is on the view, without taking part in gesture
/// recognition: it never recognizes, never cancels, delays or prevents other
/// recognizers' touches, and fails once the last finger lifts (which resets
/// it for the next touch).
///
/// PDFKit's text selection handles track their own touches, so no public
/// state says whether a selection drag is still in progress.
final class TouchObserverGestureRecognizer: UIGestureRecognizer {
  private(set) var isTouching = false
  private var activeTouches = 0

  override init(target: Any?, action: Selector?) {
    super.init(target: target, action: action)
    cancelsTouchesInView = false
    delaysTouchesBegan = false
    delaysTouchesEnded = false
  }

  override func touchesBegan(_ touches: Set<UITouch>, with event: UIEvent) {
    activeTouches += touches.count
    isTouching = true
  }

  override func touchesEnded(_ touches: Set<UITouch>, with event: UIEvent) {
    lift(touches)
  }

  override func touchesCancelled(_ touches: Set<UITouch>, with event: UIEvent) {
    lift(touches)
  }

  override func reset() {
    activeTouches = 0
    isTouching = false
  }

  override func canPrevent(_ preventedGestureRecognizer: UIGestureRecognizer) -> Bool { false }

  override func canBePrevented(by preventingGestureRecognizer: UIGestureRecognizer) -> Bool { false }

  private func lift(_ touches: Set<UITouch>) {
    activeTouches -= touches.count
    guard activeTouches <= 0 else { return }
    isTouching = false
    state = .failed
  }
}
