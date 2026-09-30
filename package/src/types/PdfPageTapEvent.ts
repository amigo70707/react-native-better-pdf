/**
 * Delivered to `PdfView`'s `onPageTap` when the user taps a page outside of
 * any link.
 *
 * Coordinates are in PDF points (1/72") from the page's top-left corner, so
 * they are the same on every platform, device and zoom level. Taps are
 * reported once the double-tap-to-zoom window has passed.
 */
export interface PdfPageTapEvent {
  /** Zero-based index of the tapped page. */
  page: number;
  /** Horizontal distance from the page's left edge, in PDF points. */
  x: number;
  /** Vertical distance from the page's top edge, in PDF points. */
  y: number;
}
