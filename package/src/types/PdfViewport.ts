import type { PdfViewProps, PdfViewRef } from '../PdfView';

/**
 * A reading position: the point of a page at the view's top-left corner,
 * and the zoom. In PDF points (1/72"), so it survives rotation, other
 * screen sizes and layout changes. Save it from
 * {@linkcode PdfViewProps.onViewportChange} and restore it with
 * {@linkcode PdfViewProps.initialViewport} or
 * {@linkcode PdfViewRef.goToViewport}.
 */
export interface PdfViewport {
  /** Zero-based page nearest to the view's top-left corner. */
  page: number;
  /**
   * PDF points from the page's left edge to the view's left edge. Negative
   * when the view starts left of the page (a margin or the gap before it).
   */
  x: number;
  /**
   * PDF points from the page's top edge to the view's top edge. Negative
   * when the view starts above the page (a margin or the gap before it).
   */
  y: number;
  /** Zoom relative to the fitted page (see `minScale`). */
  scale: number;
}
