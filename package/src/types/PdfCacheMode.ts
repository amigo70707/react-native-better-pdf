import type { PdfUriSource } from '../source';

/**
 * How long a downloaded copy of a remote document is kept.
 *
 * - `'session'` — until the app exits. The next launch downloads again.
 * - `'persistent'` — across launches, until it is removed with
 *   `PdfCache.remove()` / `PdfCache.clear()` or the app is uninstalled. The
 *   OS does not purge it and it is not included in device backups. Use it
 *   for offline reading.
 *
 * @see {@linkcode PdfUriSource.cache}
 */
export type PdfCacheMode = 'session' | 'persistent';
