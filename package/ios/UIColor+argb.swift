import UIKit

extension UIColor {
  /// A colour from React Native's `processColor` output: ARGB in the low
  /// 32 bits (unsigned on iOS, signed when it comes from Android code).
  convenience init(argb: Double) {
    let value = UInt32(truncatingIfNeeded: Int64(argb))
    self.init(
      red: CGFloat((value >> 16) & 0xFF) / 255,
      green: CGFloat((value >> 8) & 0xFF) / 255,
      blue: CGFloat(value & 0xFF) / 255,
      alpha: CGFloat(value >> 24) / 255
    )
  }
}
