import PDFKit

extension PDFPage {
  /// Whether a link annotation (URL or in-document destination) covers
  /// `pagePoint`, in PDF page space.
  func hasLink(at pagePoint: CGPoint) -> Bool {
    guard let annotation = annotation(at: pagePoint) else { return false }
    return annotation.url != nil || annotation.destination != nil || annotation.action != nil
  }
}
