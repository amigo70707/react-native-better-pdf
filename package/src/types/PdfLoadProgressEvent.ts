/**
 * Delivered to `PdfView`'s `onLoadProgress` while a remote document is being
 * downloaded.
 *
 * Emitted at most every 100 ms, plus once when the download completes (before
 * `onLoad`). Local files and documents already downloaded in this app session
 * load without progress events.
 *
 * @example
 * ```tsx
 * onLoadProgress={({ loadedBytes, totalBytes }) =>
 *   setProgress(totalBytes === undefined ? undefined : loadedBytes / totalBytes)
 * }
 * ```
 */
export interface PdfLoadProgressEvent {
  /** Bytes downloaded so far. */
  loadedBytes: number;
  /**
   * Size of the download in bytes, when the server reports it. `undefined`
   * when unknown, for example without a `Content-Length` header or when the
   * response is transparently decompressed.
   */
  totalBytes?: number;
}
