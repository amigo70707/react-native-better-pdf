import type { PdfViewport } from './PdfViewport';

/** The viewport settled at a new position; see `onViewportChange`. */
export interface PdfViewportChangeEvent {
  /** The position to save and restore. */
  viewport: PdfViewport;
  /** Zero-based lowest page index on screen. */
  firstVisiblePage: number;
  /** Zero-based highest page index on screen. */
  lastVisiblePage: number;
  /**
   * The end of the last page is on screen — its bottom, or in horizontal
   * layouts its far edge. Use it to mark a document as read.
   */
  isAtEnd: boolean;
}
