import PDFKit
import UIKit

/// Places `PdfPageOverlay` views — React views in a layer above the PDF
/// view, a sibling in the same box — on their pages. Run on every frame
/// the document moves (scroll, zoom, layout), so overlays follow their
/// pages without JS. Views are found by their `nativeID` (which React
/// Native keeps as `nativeId` on its component views; read through KVC, as
/// the class is not visible to Swift) and cached until they leave the
/// layer.
final class PageOverlays {
  var placements: [PdfOverlayPlacement] = []
  var layerId: String? {
    didSet {
      if layerId != oldValue {
        layer = nil
        views.removeAll()
      }
    }
  }

  private weak var layer: UIView?
  private var views: [String: WeakView] = [:]

  /// Places every overlay for the current scroll position and zoom.
  func layout(pdfView: PDFView, container: UIView) {
    guard !placements.isEmpty, let layer = findLayer(near: container) else { return }
    // No document (loading, failed): every overlay hides.
    let document = pdfView.document
    let pageCount = document?.pageCount ?? 0
    let visible = Set(pdfView.visiblePages.map { ObjectIdentifier($0) })
    for placement in placements {
      guard let view = view(for: placement.id, in: layer), let parent = view.superview else { continue }
      guard placement.page >= 0, placement.page < Double(pageCount),
            let page = document?.page(at: Int(placement.page)),
            visible.contains(ObjectIdentifier(page)) else {
        view.isHidden = true
        continue
      }
      // PDF points from the page's top-left, as onPageTap reports them.
      let pageRect = page.pageRect(
        fromTopLeft: CGRect(x: placement.x, y: placement.y, width: placement.width, height: placement.height),
        box: pdfView.displayBox
      )
      // Into the coordinates of the overlay's parent (the layer, or a view in it).
      let rect = parent.convert(pdfView.convert(pageRect, from: page), from: pdfView)
      place(view, in: rect, scalesWithZoom: placement.scalesWithZoom)
      view.isHidden = false
    }
  }

  /// Transforms `view` (laid out at the overlay's size in points) so it
  /// covers `rect`, or — not scaling — so its top-left corner is there.
  private func place(_ view: UIView, in rect: CGRect, scalesWithZoom: Bool) {
    let size = view.bounds.size
    guard size.width > 0, size.height > 0 else { return }
    let scale = scalesWithZoom ? rect.width / size.width : 1
    let target = CGPoint(x: rect.minX + size.width * scale / 2, y: rect.minY + size.height * scale / 2)
    // UIView transforms apply around the centre the layout gave the view.
    let center = view.center
    view.transform = CGAffineTransform(translationX: target.x - center.x, y: target.y - center.y)
      .scaledBy(x: scale, y: scale)
  }

  private func findLayer(near container: UIView) -> UIView? {
    if let layer, layer.window != nil, Self.nativeId(of: layer) == layerId { return layer }
    guard let layerId else { return nil }
    // container → React Native's view for PdfView's native component → the box.
    var ancestor = container.superview
    for _ in 0..<3 {
      guard let current = ancestor else { break }
      if let found = current.subviews.first(where: { Self.nativeId(of: $0) == layerId }) {
        layer = found
        views.removeAll()
        return found
      }
      ancestor = current.superview
    }
    return nil
  }

  private func view(for id: String, in layer: UIView) -> UIView? {
    if let cached = views[id]?.view, cached.isDescendant(of: layer), Self.nativeId(of: cached) == id {
      return cached
    }
    guard let found = Self.descendant(of: layer, nativeId: id) else { return nil }
    views[id] = WeakView(view: found)
    return found
  }

  private static func descendant(of root: UIView, nativeId: String) -> UIView? {
    var queue = root.subviews
    while !queue.isEmpty {
      let view = queue.removeFirst()
      if Self.nativeId(of: view) == nativeId { return view }
      queue.append(contentsOf: view.subviews)
    }
    return nil
  }

  private static func nativeId(of view: UIView) -> String? {
    guard view.responds(to: nativeIdSelector) else { return nil }
    return view.value(forKey: "nativeId") as? String
  }

  private static let nativeIdSelector = NSSelectorFromString("nativeId")
}

private struct WeakView {
  weak var view: UIView?
}
