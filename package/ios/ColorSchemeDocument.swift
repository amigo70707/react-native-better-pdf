import PDFKit

/// The displayed document: its own delegate, so every page PDFKit creates
/// is a `ColorSchemePage`, drawn dark while `isDark` is set, and every text
/// markup annotation a `ColorSchemeAnnotation`. Separate instances of the file
/// (page images, printing, outline) are plain `PDFDocument`s and keep the
/// document's colours.
final class ColorSchemeDocument: PDFDocument, PDFDocumentDelegate {
  private let lock = NSLock()
  private var dark = false

  /// Set on the main thread; read by pages on PDFKit's drawing threads.
  var isDark: Bool {
    get {
      lock.lock()
      defer { lock.unlock() }
      return dark
    }
    set {
      lock.lock()
      dark = newValue
      lock.unlock()
    }
  }

  override init?(url: URL) {
    super.init(url: url)
    delegate = self
  }

  func classForPage() -> AnyClass {
    ColorSchemePage.self
  }

  /// Only text markup needs drawing differently on dark pages.
  func `class`(forAnnotationType annotationType: String) -> AnyClass {
    ColorSchemeAnnotation.isMarkup(annotationType) ? ColorSchemeAnnotation.self : PDFAnnotation.self
  }
}
