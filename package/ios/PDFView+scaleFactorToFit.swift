import PDFKit

extension PDFView {
  /// The scale at which the document's first page fits the view by
  /// `policy`, within the page break margins; 0 before layout. PDFKit applies
  /// one scale to every page.
  func scaleFactorToFit(_ policy: PdfFitPolicy) -> CGFloat {
    guard let page = document?.page(at: 0) else { return 0 }
    var size = page.bounds(for: displayBox).size
    if page.rotation % 180 != 0 { swap(&size.width, &size.height) }
    let margins = displaysPageBreaks ? pageBreakMargins : .zero
    let width = bounds.width - margins.left - margins.right
    let height = bounds.height - margins.top - margins.bottom
    guard size.width > 0, size.height > 0, width > 0, height > 0 else { return 0 }
    switch policy {
    case .width: return width / size.width
    case .height: return height / size.height
    case .page: return min(width / size.width, height / size.height)
    }
  }
}
