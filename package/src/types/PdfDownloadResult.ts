import type { PdfCacheEntry } from './PdfCacheEntry';
import type { PdfError } from './PdfError';

/**
 * Outcome of a native download: exactly one of `entry` and `error` is set.
 * Native rejections carry only a message, so failures cross the boundary as
 * values and `PdfCache` turns them into `PdfDownloadError`s.
 */
export interface PdfDownloadResult {
  entry?: PdfCacheEntry;
  error?: PdfError;
}
