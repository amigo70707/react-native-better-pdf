import PDFKit
import UIKit

/// Hosts the `PDFView` of a `HybridBetterPdfView`: reports layout changes,
/// turns single taps into page taps, intercepts link clicks, and reports
/// PDFKit's text selection once it settles.
///
/// It is the PDFView's delegate so that URL links are never opened by PDFKit
/// itself — the app decides through `onLinkPress`. Links to other pages are
/// still followed by PDFKit.
final class PdfContainerView: UIView, PDFViewDelegate, UIGestureRecognizerDelegate {
  /// `nil` once released in `onDropView`.
  private(set) var pdfView: PDFView?
  var onLayout: (() -> Void)?
  /// A URL link was clicked.
  var linkPressed: ((URL) -> Void)?
  /// A page was tapped outside of any link; the point is in PDF points from
  /// the page's top-left corner.
  var pageTapped: ((PDFPage, CGPoint) -> Void)?
  /// The text selection settled at different text; `""` when cleared.
  var selectionSettled: ((String) -> Void)?

  private let tapRecognizer = UITapGestureRecognizer()
  private let touchObserver = TouchObserverGestureRecognizer()
  private var observers: [NSObjectProtocol] = []
  private var selectionSettleWork: DispatchWorkItem?
  private var reportedSelection = ""

  /// When false, double taps do not zoom and single taps are reported
  /// without waiting for a possible second tap.
  var isDoubleTapZoomEnabled = true {
    didSet { applyDoubleTapZoom() }
  }

  /// When false, PDFKit's text selection gestures are disabled and any
  /// selection is cleared.
  var isTextSelectionEnabled = true {
    didSet { applyTextSelection() }
  }

  override init(frame: CGRect) {
    super.init(frame: frame)
    let pdfView = PDFView()
    self.pdfView = pdfView
    pdfView.frame = bounds
    pdfView.autoresizingMask = [.flexibleWidth, .flexibleHeight]
    pdfView.displayMode = .singlePageContinuous
    pdfView.displayDirection = .vertical
    pdfView.displaysPageBreaks = true
    pdfView.autoScales = false
    pdfView.backgroundColor = .clear
    pdfView.delegate = self
    clipsToBounds = true
    addSubview(pdfView)

    tapRecognizer.addTarget(self, action: #selector(handleTap(_:)))
    tapRecognizer.cancelsTouchesInView = false
    tapRecognizer.delegate = self
    addGestureRecognizer(tapRecognizer)
    touchObserver.delegate = self
    addGestureRecognizer(touchObserver)

    // Posted for every movement of a selection handle; reported once settled.
    observers.append(NotificationCenter.default.addObserver(
      forName: .PDFViewSelectionChanged, object: pdfView, queue: .main
    ) { [weak self] _ in
      self?.selectionDidChange()
    })
    // Paged layouts create each page's text view when the page first shows.
    observers.append(NotificationCenter.default.addObserver(
      forName: .PDFViewVisiblePagesChanged, object: pdfView, queue: .main
    ) { [weak self] _ in
      guard let self, !self.isTextSelectionEnabled else { return }
      self.applyTextSelection()
    })
  }

  @available(*, unavailable)
  required init?(coder: NSCoder) {
    fatalError("init(coder:) is not supported")
  }

  override func layoutSubviews() {
    super.layoutSubviews()
    onLayout?()
  }

  /// PDFKit creates its gesture recognizers with the document and again when
  /// the layout mode changes; call after either.
  func applyDoubleTapZoom() {
    guard let pdfView else { return }
    for recognizer in pdfView.doubleTapRecognizers() where recognizer.isDoubleTapZoom {
      recognizer.isEnabled = isDoubleTapZoomEnabled
    }
  }

  /// Destroys the PDFView and everything it caches (page views, tiles,
  /// layers). The hybrid object — and with it this container — can outlive
  /// the React view for as long as JS holds a `hybridRef` to it, until the JS
  /// garbage collector runs; measured: ~7.5 MB retained per closed viewer
  /// otherwise.
  /// PDFKit creates its text views with the document and again when the
  /// layout mode changes or a page first shows in paged layouts; call after
  /// the first two (the third is observed).
  func applyTextSelection() {
    guard let pdfView else { return }
    for recognizer in pdfView.textSelectionRecognizers() {
      recognizer.isEnabled = isTextSelectionEnabled
    }
    if !isTextSelectionEnabled, pdfView.currentSelection != nil { pdfView.clearSelection() }
  }

  /// A new document starts without a selection.
  func resetSelection() {
    selectionSettleWork?.cancel()
    reportSelection("")
  }

  func releasePdfView() {
    for observer in observers { NotificationCenter.default.removeObserver(observer) }
    observers = []
    selectionSettleWork?.cancel()
    pdfView?.delegate = nil
    pdfView?.document = nil
    pdfView?.removeFromSuperview()
    pdfView = nil
  }

  // MARK: PDFViewDelegate

  /// Implementing this stops PDFKit from opening the URL itself.
  func pdfViewWillClick(onLink sender: PDFView, with url: URL) {
    linkPressed?(url)
  }

  // MARK: UIGestureRecognizerDelegate

  /// PDFKit's own recognizers (scrolling, links, text) keep working.
  func gestureRecognizer(
    _ gestureRecognizer: UIGestureRecognizer,
    shouldRecognizeSimultaneouslyWith otherGestureRecognizer: UIGestureRecognizer
  ) -> Bool {
    return true
  }

  /// Single taps wait for double taps to fail (so a double tap zooms or
  /// selects a word without also reporting page taps) — unless double-tap
  /// zoom is off, then they are reported immediately. Evaluated per gesture,
  /// so recognizers PDFKit creates later are covered too.
  func gestureRecognizer(
    _ gestureRecognizer: UIGestureRecognizer,
    shouldRequireFailureOf otherGestureRecognizer: UIGestureRecognizer
  ) -> Bool {
    guard gestureRecognizer === tapRecognizer, isDoubleTapZoomEnabled else { return false }
    return (otherGestureRecognizer as? UITapGestureRecognizer)?.numberOfTapsRequired == 2
  }

  // MARK: Text selection

  private func selectionDidChange() {
    guard let pdfView else { return }
    guard isTextSelectionEnabled else {
      // A selection from a gesture PDFKit created since the last apply.
      if pdfView.currentSelection != nil {
        pdfView.clearSelection()
        applyTextSelection()
      }
      resetSelection()
      return
    }
    scheduleSelectionReport()
  }

  /// The selection has settled when it did not change for
  /// `selectionSettleDelay` and no finger is down: PDFKit's handles and loupe
  /// track their own touches, so the scroll view's state says nothing.
  private func scheduleSelectionReport() {
    selectionSettleWork?.cancel()
    let work = DispatchWorkItem { [weak self] in self?.reportSelectionIfSettled() }
    selectionSettleWork = work
    DispatchQueue.main.asyncAfter(deadline: .now() + Self.selectionSettleDelay, execute: work)
  }

  private func reportSelectionIfSettled() {
    if touchObserver.isTouching {
      scheduleSelectionReport()
      return
    }
    reportSelection(pdfView?.currentSelection?.string ?? "")
  }

  private func reportSelection(_ text: String) {
    guard text != reportedSelection else { return }
    reportedSelection = text
    selectionSettled?(text)
  }

  private static let selectionSettleDelay = DispatchTimeInterval.milliseconds(100)

  // MARK: Taps

  @objc private func handleTap(_ recognizer: UITapGestureRecognizer) {
    guard recognizer.state == .ended, let pdfView, pageTapped != nil else { return }
    let viewPoint = recognizer.location(in: pdfView)
    guard let page = pdfView.page(for: viewPoint, nearest: false) else { return }
    let pagePoint = pdfView.convert(viewPoint, to: page)
    // Link taps are handled by PDFKit (and `pdfViewWillClick`), not reported as page taps.
    guard !page.hasLink(at: pagePoint) else { return }
    pageTapped?(page, page.topLeftPoint(from: pagePoint, box: pdfView.displayBox))
  }
}
