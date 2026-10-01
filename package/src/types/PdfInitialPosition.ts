/**
 * Where a document opens. Internal: `PdfView` builds it from `initialPage`
 * and `initialScale`, or from `initialViewport`, which adds `x` and `y`
 * (both or neither). Without them the page starts at its top, centred
 * across.
 */
export interface PdfInitialPosition {
  page: number;
  scale: number;
  x?: number;
  y?: number;
}
