/**
 * Delivered to `PdfView`'s `onLoad` once a document is open and ready to
 * display.
 */
export interface PdfLoadEvent {
  /** Number of pages in the document. */
  pageCount: number;
  /** Width of the first page in PDF points (1/72"). */
  firstPageWidth: number;
  /** Height of the first page in PDF points (1/72"). */
  firstPageHeight: number;
  /**
   * Absolute path of the local file being displayed: the file itself for
   * `file://` sources, the downloaded copy for remote ones (app cache, valid
   * for this app session; the OS may purge it later), or the extracted copy
   * of a bundled asset. `undefined` when there is no file on disk, e.g. for
   * Android `content://` sources. Useful for sharing or printing.
   */
  filePath?: string;
}
