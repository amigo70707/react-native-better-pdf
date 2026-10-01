import QuartzCore

/// Calls `onSettle` once `delay` has passed without a `noteMotion()`. Cheap
/// enough to call on every scroll frame: at most one dispatch is pending, and
/// it re-arms itself for the remaining time instead of being replaced.
/// Main thread.
final class SettleDebouncer {
  private let delay: CFTimeInterval
  private let onSettle: () -> Void
  private var lastMotion: CFTimeInterval = 0
  private var isScheduled = false
  /// Bumped by `cancel()` so a dispatch already in flight does nothing.
  private var generation = 0

  init(delay: CFTimeInterval, onSettle: @escaping () -> Void) {
    self.delay = delay
    self.onSettle = onSettle
  }

  func noteMotion() {
    lastMotion = CACurrentMediaTime()
    guard !isScheduled else { return }
    isScheduled = true
    schedule(after: delay)
  }

  func cancel() {
    generation += 1
    isScheduled = false
  }

  private func schedule(after interval: CFTimeInterval) {
    let generation = generation
    DispatchQueue.main.asyncAfter(deadline: .now() + interval) { [weak self] in
      guard let self, self.generation == generation else { return }
      self.fire()
    }
  }

  private func fire() {
    let remaining = lastMotion + delay - CACurrentMediaTime()
    if remaining > 0.001 {
      schedule(after: remaining)
      return
    }
    isScheduled = false
    onSettle()
  }
}
