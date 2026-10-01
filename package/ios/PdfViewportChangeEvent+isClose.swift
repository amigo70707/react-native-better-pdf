extension PdfViewportChangeEvent {
  /// Same position within rounding noise (0.5 pt, 0.001 zoom): not worth an
  /// event. Matches Android's `DocumentViewport.isCloseTo`.
  func isClose(to other: PdfViewportChangeEvent) -> Bool {
    viewport.page == other.viewport.page &&
      abs(viewport.x - other.viewport.x) < 0.5 &&
      abs(viewport.y - other.viewport.y) < 0.5 &&
      abs(viewport.scale - other.viewport.scale) < 0.001 &&
      firstVisiblePage == other.firstVisiblePage &&
      lastVisiblePage == other.lastVisiblePage &&
      isAtEnd == other.isAtEnd
  }
}
