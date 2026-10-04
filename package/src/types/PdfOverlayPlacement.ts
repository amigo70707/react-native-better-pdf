/**
 * Where native code places a `PdfPageOverlay`'s view; internal to the
 * `PdfView` component.
 */
export interface PdfOverlayPlacement {
  /** The overlay view's `nativeID` inside the overlay layer. */
  id: string;
  /** Zero-based page. */
  page: number;
  /** PDF points from the page's top-left corner, as in `onPageTap`. */
  x: number;
  y: number;
  width: number;
  height: number;
  /** Scaled with the page (true) or kept at its size, its top-left corner pinned. */
  scalesWithZoom: boolean;
}
