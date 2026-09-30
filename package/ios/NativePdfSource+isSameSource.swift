extension Optional where Wrapped == NativePdfSource {
  /// Structural equality, so a re-sent but identical source never reloads.
  func isSameSource(as other: NativePdfSource?) -> Bool {
    switch (self, other) {
    case (nil, nil):
      return true
    case let (lhs?, rhs?):
      return lhs.uri == rhs.uri && (lhs.headers ?? [:]) == (rhs.headers ?? [:])
    default:
      return false
    }
  }
}
