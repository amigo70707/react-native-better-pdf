import PDFKit

/// A form widget of a `ColorSchemeDocument`. Reports the reader's changes
/// to its document — PDFKit has no notification for them; measured: it
/// sets `widgetStringValue` when text editing ends and `buttonWidgetState`
/// on a toggle — and acts read-only while the document's form filling is
/// off.
final class FormWidgetAnnotation: PDFAnnotation {
  override var widgetStringValue: String? {
    get { super.widgetStringValue }
    set {
      super.widgetStringValue = newValue
      reportChange()
    }
  }

  override var buttonWidgetState: PDFWidgetCellState {
    get { super.buttonWidgetState }
    set {
      super.buttonWidgetState = newValue
      reportChange()
    }
  }

  override var isReadOnly: Bool {
    get { isReadOnlyInDocument || formDocument?.isFormFillingEnabled == false }
    set { super.isReadOnly = newValue }
  }

  /// The document's read-only flag, whether or not filling is on.
  var isReadOnlyInDocument: Bool { super.isReadOnly }

  private var formDocument: ColorSchemeDocument? { page?.document as? ColorSchemeDocument }

  /// PDFKit also sets values while it builds the annotation, before it is
  /// on a page; those are not changes.
  private func reportChange() {
    guard page != nil else { return }
    formDocument?.widgetDidChange(self)
  }
}
