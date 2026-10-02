import PDFKit

/// An annotation of a `ColorSchemeDocument`. Annotations keep their own
/// colours on dark pages: PDFKit draws them apart from the page. Text
/// markup (highlight, underline, strike-out, squiggly) is composited in
/// the multiply mode there, which turns it black on a dark page; while the
/// page is dark, the page draws it instead (`drawScreened`).
final class ColorSchemeAnnotation: PDFAnnotation {
  var isMarkup: Bool { Self.isMarkup(type ?? "") }

  /// Highlight, underline, strike-out or squiggly — with or without the
  /// subtype's leading slash.
  static func isMarkup(_ type: String) -> Bool {
    markupTypes.contains(type.hasPrefix("/") ? String(type.dropFirst()) : type)
  }

  override func draw(with box: PDFDisplayBox, in context: CGContext) {
    if isMarkup, (page?.document as? ColorSchemeDocument)?.isDark == true { return }
    super.draw(with: box, in: context)
  }

  /// Draws this markup lightening a dark page: composited in the screen
  /// mode, the inverse of the multiply it is drawn in on light pages. A
  /// highlight at half strength, so white text on it should stay readable.
  func drawScreened(with box: PDFDisplayBox, in context: CGContext) {
    context.saveGState()
    context.setBlendMode(.screen)
    if type == "Highlight" { context.setAlpha(0.5) }
    context.beginTransparencyLayer(auxiliaryInfo: nil)
    super.draw(with: box, in: context)
    context.endTransparencyLayer()
    context.restoreGState()
  }

  private static let markupTypes: Set<String> = ["Highlight", "Underline", "StrikeOut", "Squiggly"]
}
