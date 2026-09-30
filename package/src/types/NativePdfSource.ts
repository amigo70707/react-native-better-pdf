/**
 * The document source as received by the native view. The public
 * `PdfSource` union accepted by `PdfView` is normalized into this shape, so
 * native code handles a single representation.
 *
 * `uri` schemes understood natively:
 * - `http://`, `https://` — downloaded natively into the app cache.
 * - `file://` or an absolute path — opened in place.
 * - `content://` (Android) — opened through the ContentResolver.
 * - a bare resource name (Android release builds of `require('./x.pdf')`) —
 *   resolved from `res/raw`.
 */
export interface NativePdfSource {
  /** Location of the document; see the supported schemes above. */
  uri: string;
  /** Request headers for remote sources. Ignored for local sources. */
  headers?: Record<string, string>;
}
