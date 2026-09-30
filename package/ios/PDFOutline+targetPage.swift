import PDFKit

extension PDFOutline {
  /// The page this entry points to: its destination or a GoTo action's.
  /// `nil` for URL actions, remote documents and entries without a target.
  var targetPage: PDFPage? {
    if let page = destination?.page { return page }
    return (action as? PDFActionGoTo)?.destination.page
  }
}
