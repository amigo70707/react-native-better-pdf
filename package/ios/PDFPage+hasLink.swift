import PDFKit

extension PDFPage {
  /// Whether a link PDFKit follows covers `pagePoint`, in PDF page space: a
  /// URL, an in-document destination, or an action PDFKit performs.
  /// Annotations with other actions (JavaScript, launching an app) do
  /// nothing when tapped, so taps on them are page taps, as on Android.
  func hasLink(at pagePoint: CGPoint) -> Bool {
    guard let annotation = annotation(at: pagePoint) else { return false }
    if annotation.url != nil || annotation.destination != nil { return true }
    switch annotation.action {
    case is PDFActionURL, is PDFActionGoTo, is PDFActionRemoteGoTo, is PDFActionNamed, is PDFActionResetForm:
      return true
    default:
      return false
    }
  }
}
