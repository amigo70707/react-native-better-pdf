import PDFKit
import UIKit

/// A page drawn dark while its `ColorSchemeDocument` is: lightness
/// inverted, hues kept. The page is drawn on white, inverted with a
/// difference blend against white, then drawn again into a transparency
/// layer composited in the hue blend mode, which takes the hue from the
/// page and saturation and lightness from the inverted result — black text
/// turns white, a red heading stays red. (Drawn straight in the hue mode,
/// highlight annotations' own multiply blend would turn them black.)
final class ColorSchemePage: PDFPage {
  override func draw(with box: PDFDisplayBox, to context: CGContext) {
    guard (document as? ColorSchemeDocument)?.isDark == true else {
      super.draw(with: box, to: context)
      return
    }
    let area = context.boundingBoxOfClipPath
    // Paper first, in case PDFKit paints it outside this method.
    fill(area, in: context, blendMode: .normal)
    super.draw(with: box, to: context)
    fill(area, in: context, blendMode: .difference)
    context.saveGState()
    context.setBlendMode(.hue)
    context.beginTransparencyLayer(auxiliaryInfo: nil)
    super.draw(with: box, to: context)
    context.endTransparencyLayer()
    context.restoreGState()
    for case let annotation as ColorSchemeAnnotation in annotations where annotation.isMarkup {
      annotation.drawScreened(with: box, in: context)
    }
  }

  private func fill(_ area: CGRect, in context: CGContext, blendMode: CGBlendMode) {
    context.saveGState()
    context.setBlendMode(blendMode)
    context.setFillColor(UIColor.white.cgColor)
    context.fill(area)
    context.restoreGState()
  }
}
