/**
 * Where a form field is shown: zero-based page and a rectangle in PDF
 * points from the top-left corner of the page as displayed — as
 * `onPageTap` reports points and `PdfPageOverlay` places views.
 */
export interface PdfFormWidget {
  page: number;
  x: number;
  y: number;
  width: number;
  height: number;
}
