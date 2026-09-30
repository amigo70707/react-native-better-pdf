/**
 * Delivered to `PdfView`'s `onPageChange` when a different page occupies the
 * viewport's centre (or the last page once scrolled to the end).
 */
export interface PdfPageChangeEvent {
  /** Zero-based index of the current page. */
  page: number;
  /** Number of pages in the document. */
  pageCount: number;
}
