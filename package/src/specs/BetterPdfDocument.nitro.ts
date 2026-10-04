import type { HybridObject } from 'react-native-nitro-modules';
import type { NativePdfSource } from '../types/NativePdfSource';
import type { PdfDocumentMetadata } from '../types/PdfDocumentMetadata';
import type { PdfLoadProgressEvent } from '../types/PdfLoadProgressEvent';
import type { PdfOpenResult } from '../types/PdfOpenResult';
import type { PdfOutlineEntry } from '../types/PdfOutlineEntry';
import type { PdfPageImage } from '../types/PdfPageImage';
import type { PdfPageSize } from '../types/PdfPageSize';
import type { PdfRenderPageOptions } from '../types/PdfRenderPageOptions';

/**
 * One document opened without a view. Internal: apps use `PdfDocument`,
 * which adds typed open errors, `AbortSignal` cancellation and the outline
 * tree on top.
 *
 * Every method works off the JS thread; calls run one at a time, in order.
 */
export interface BetterPdfDocument extends HybridObject<{
  ios: 'swift';
  android: 'kotlin';
}> {
  /**
   * Opens `source` like the view does (downloads, cache options, password).
   * Called once per object. `onProgress` (remote downloads) is throttled
   * like the view's. Failures resolve with `error`; after `close()` the
   * promise never resolves.
   */
  open(
    source: NativePdfSource,
    password?: string,
    onProgress?: (event: PdfLoadProgressEvent) => void,
  ): Promise<PdfOpenResult>;
  /** The information dictionary. Read on first call, then cached. */
  getMetadata(): Promise<PdfDocumentMetadata>;
  /** Every page's displayed size, by zero-based page. Cached. */
  getPageSizes(): Promise<PdfPageSize[]>;
  /** The outline, flattened depth-first. Cached. */
  getOutline(): Promise<PdfOutlineEntry[]>;
  /** Renders zero-based `page` to an image file, like the view's. */
  renderPage(
    page: number,
    options: PdfRenderPageOptions,
  ): Promise<PdfPageImage>;
  /**
   * Cancels an open in progress and releases the document; calls already
   * running finish, later ones reject. Idempotent.
   */
  close(): void;
}
