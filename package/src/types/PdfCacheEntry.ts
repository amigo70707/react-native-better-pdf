import type { PdfCacheMode } from './PdfCacheMode';

/**
 * The downloaded copy of a remote document, as returned by
 * `PdfCache.download()` and `PdfCache.getEntry()`.
 */
export interface PdfCacheEntry {
  /** Absolute path of the downloaded file. Valid until the copy is removed. */
  filePath: string;
  /** Size of the file in bytes. */
  byteSize: number;
  /** How long the copy is kept. */
  cache: PdfCacheMode;
  /**
   * When the copy was downloaded, or last confirmed unchanged by the server,
   * in milliseconds since the Unix epoch. `maxAgeMs` counts from here.
   */
  validatedAtMs: number;
}
