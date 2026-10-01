import PDFKit

extension PDFPage {
  /// The page's size as displayed, in PDF points: its crop box (which PDFKit
  /// clips to the media box), width and height swapped for a quarter-turn
  /// rotation — the same rule as Android's page-tree reader.
  var displaySize: CGSize {
    let box = bounds(for: .cropBox)
    return rotation % 180 == 0 ? box.size : CGSize(width: box.height, height: box.width)
  }
}
