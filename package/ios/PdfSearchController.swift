import PDFKit
import UIKit

/// Runs `search()` for a view with PDFKit's asynchronous find: matches
/// arrive in document order, are highlighted as they come, and the first one
/// at or after the page being read becomes current. `onSearchChange` is
/// emitted at most every 100 ms while searching, immediately for the first
/// match, navigation, completion and clearing. Main thread.
final class PdfSearchController {
  private weak var pdfView: PDFView?
  private let emit: (PdfSearchChangeEvent) -> Void
  private var document: PDFDocument?
  private var observers: [NSObjectProtocol] = []
  private var matches: [PDFSelection] = []
  private var currentIndex: Int?
  private var query = ""
  private var startPage = 0
  private var isComplete = true
  private var lastEmit = Date.distantPast

  /// Highlight of every match; set by `searchHighlightColor`.
  var matchColor = UIColor(red: 1, green: 214 / 255, blue: 0, alpha: 0.4) {
    didSet { recolorVisibleMatches() }
  }
  /// Highlight of the current match; set by `searchCurrentHighlightColor`.
  var currentColor = UIColor(red: 1, green: 140 / 255, blue: 0, alpha: 0.6) {
    didSet { recolorVisibleMatches() }
  }
  private static let emitInterval: TimeInterval = 0.1

  init(pdfView: PDFView?, emit: @escaping (PdfSearchChangeEvent) -> Void) {
    self.pdfView = pdfView
    self.emit = emit
  }

  func search(_ query: String, startPage: Int) {
    stopFinding()
    guard !query.isEmpty else {
      clear()
      return
    }
    guard let pdfView, let document = pdfView.document else { return }
    self.query = query
    self.startPage = startPage
    self.document = document
    matches = []
    currentIndex = nil
    isComplete = false
    pdfView.highlightedSelections = nil
    let center = NotificationCenter.default
    observers = [
      center.addObserver(forName: .PDFDocumentDidFindMatch, object: document, queue: .main) { [weak self] note in
        guard let selection = note.userInfo?["PDFDocumentFoundSelection"] as? PDFSelection else { return }
        self?.found(selection)
      },
      center.addObserver(forName: .PDFDocumentDidEndFind, object: document, queue: .main) { [weak self] _ in
        self?.finished()
      },
    ]
    document.beginFindString(query, withOptions: [.caseInsensitive])
    emitNow()
  }

  func next() {
    guard !matches.isEmpty else { return }
    select(currentIndex.map { ($0 + 1) % matches.count } ?? 0)
  }

  func previous() {
    guard !matches.isEmpty else { return }
    select(currentIndex.map { ($0 - 1 + matches.count) % matches.count } ?? matches.count - 1)
  }

  func clear() {
    stopFinding()
    let hadSearch = !query.isEmpty
    query = ""
    matches = []
    currentIndex = nil
    isComplete = true
    pdfView?.highlightedSelections = nil
    if hadSearch { emitNow() }
  }

  private func found(_ selection: PDFSelection) {
    selection.color = matchColor
    matches.append(selection)
    if currentIndex == nil, let page = pageIndex(of: selection), page >= startPage {
      select(matches.count - 1)
      return
    }
    if Date().timeIntervalSince(lastEmit) >= Self.emitInterval {
      refreshHighlights()
      emitNow()
    }
  }

  private func finished() {
    stopFinding()
    isComplete = true
    // Nothing at or after the reader's page: wrap to the first match.
    if currentIndex == nil, !matches.isEmpty {
      select(0)
      return
    }
    refreshHighlights()
    emitNow()
  }

  private func select(_ index: Int) {
    currentIndex = index
    refreshHighlights(isRecolored: true)
    let selection = matches[index]
    if let pdfView, let page = selection.pages.first {
      pdfView.go(to: selection.bounds(for: page), on: page)
    }
    emitNow()
  }

  /// PDFKit draws `highlightedSelections` in each selection's colour. It
  /// keeps drawn pages when only a colour changes (measured on iOS 26), so
  /// recolouring resets the list first.
  private func refreshHighlights(isRecolored: Bool = false) {
    if isRecolored { pdfView?.highlightedSelections = nil }
    for (index, selection) in matches.enumerated() {
      selection.color = index == currentIndex ? currentColor : matchColor
    }
    pdfView?.highlightedSelections = matches
  }

  private func recolorVisibleMatches() {
    guard !matches.isEmpty else { return }
    refreshHighlights(isRecolored: true)
  }

  private func pageIndex(of selection: PDFSelection) -> Int? {
    guard let page = selection.pages.first, let document else { return nil }
    let index = document.index(for: page)
    return index == NSNotFound ? nil : index
  }

  private func stopFinding() {
    if let document, document.isFinding { document.cancelFindString() }
    observers.forEach { NotificationCenter.default.removeObserver($0) }
    observers = []
  }

  private func emitNow() {
    lastEmit = Date()
    let currentPage = currentIndex.flatMap { pageIndex(of: matches[$0]) }
    emit(PdfSearchChangeEvent(
      query: query,
      matchCount: Double(matches.count),
      currentMatch: currentIndex.map(Double.init),
      currentPage: currentPage.map(Double.init),
      isComplete: isComplete,
      isSupported: true
    ))
  }
}
