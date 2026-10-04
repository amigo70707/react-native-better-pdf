import PDFKit

extension PDFPage {
  /// Whether a form widget the reader can fill in covers `pagePoint`, in PDF
  /// page space: a tap there edits the form, it is not a page tap.
  func hasFillableWidget(at pagePoint: CGPoint) -> Bool {
    guard let widget = annotation(at: pagePoint) as? FormWidgetAnnotation else { return false }
    return !widget.isReadOnly
  }
}
