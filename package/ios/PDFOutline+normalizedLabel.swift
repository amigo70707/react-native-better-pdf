import PDFKit

extension PDFOutline {
  /// The label on one line: control characters (line breaks, tabs) become a
  /// space, surrounding whitespace is trimmed, at most 512 characters — as on
  /// Android.
  var normalizedLabel: String {
    let label = (label ?? "").replacingOccurrences(of: "[\\u0000-\\u001F\\u007F]+", with: " ", options: .regularExpression)
    return String(label.trimmingCharacters(in: .whitespaces).prefix(512))
  }
}
