/**
 * A page's size as displayed, in PDF points (1/72"): its crop box (clipped
 * to the media box), with width and height swapped for pages rotated by 90°
 * or 270°.
 */
export interface PdfPageSize {
  width: number;
  height: number;
}
