extension Optional where Wrapped == NativePdfSource {
  /// Whether two sources show the same document, so a re-sent source never
  /// reloads. Sources with a `cacheKey` are the same document when their
  /// keys are equal, whatever their `uri` and headers (signed URLs, rotating
  /// tokens). Cache options alone never reload.
  func isSameSource(as other: NativePdfSource?) -> Bool {
    switch (self, other) {
    case (nil, nil):
      return true
    case let (lhs?, rhs?):
      if lhs.cacheKey != nil || rhs.cacheKey != nil { return lhs.cacheKey == rhs.cacheKey }
      return lhs.uri == rhs.uri && (lhs.headers ?? [:]) == (rhs.headers ?? [:])
    default:
      return false
    }
  }
}
