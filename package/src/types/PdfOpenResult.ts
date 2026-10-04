import type { PdfError } from './PdfError';

/** What `BetterPdfDocument.open` reports about the opened document. */
export interface PdfOpenedDocument {
  pageCount: number;
  /** As in `PdfLoadEvent.filePath`. */
  filePath?: string;
  /** As in `PdfLoadEvent.isStale`. */
  isStale: boolean;
}

/**
 * Outcome of a native open: exactly one of `document` and `error` is set.
 * Native rejections carry only a message, so failures cross the boundary as
 * values and `PdfDocument.open` turns them into `PdfOpenError`s.
 */
export interface PdfOpenResult {
  document?: PdfOpenedDocument;
  error?: PdfError;
}
