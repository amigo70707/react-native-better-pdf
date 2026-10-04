import PDFKit

/// The displayed document: its own delegate, so every page PDFKit creates
/// is a `ColorSchemePage`, drawn dark while `isDark` is set, every text
/// markup annotation a `ColorSchemeAnnotation` and every form widget a
/// `FormWidgetAnnotation`. Separate instances of the file (page images,
/// printing, outline) are plain `PDFDocument`s and keep the document's
/// colours.
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

  /// Main thread: whether the reader can fill the form in.
  var isFormFillingEnabled = true
  /// Main thread: a widget's value was set, by the reader or by PDFKit
  /// loading its page (see `FormInteraction`).
  var widgetChanged: ((FormWidgetAnnotation) -> Void)?
  /// Main thread: values changed since loading, by the reader or by code.
  private(set) var formChangeCount = 0
  private var isQuiet = false

  func widgetDidChange(_ widget: FormWidgetAnnotation) {
    guard !isQuiet else { return }
    widgetChanged?(widget)
  }

  func noteFormChanged() {
    formChangeCount += 1
  }

  /// Runs `body` without reporting widget changes: code setting values, and
  /// writing the document, where PDFKit sets buttons' states again
  /// (measured: a radio group reported a change on every write).
  func withoutReportingChanges<T>(countingAsChange: Bool = false, _ body: () throws -> T) rethrows -> T {
    isQuiet = true
    defer {
      isQuiet = false
      if countingAsChange { formChangeCount += 1 }
    }
    return try body()
  }

  override init?(url: URL) {
    super.init(url: url)
    delegate = self
  }

  func classForPage() -> AnyClass {
    ColorSchemePage.self
  }

  /// Text markup draws differently on dark pages; widgets report changes.
  func `class`(forAnnotationType annotationType: String) -> AnyClass {
    if annotationType == "Widget" || annotationType == "/Widget" { return FormWidgetAnnotation.self }
    return ColorSchemeAnnotation.isMarkup(annotationType) ? ColorSchemeAnnotation.self : PDFAnnotation.self
  }
}
