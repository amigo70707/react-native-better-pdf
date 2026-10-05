/** Options of `PdfViewRef.exportDocument`. */
export interface PdfExportOptions {
  /**
   * Absolute path (or `file://` URI) of the file to write; its directory
   * must exist and an existing file is replaced — except the displayed
   * document's own file (`onLoad`'s `filePath`), which is rejected.
   * Default: a new file in the app's cache directory, named after the
   * document.
   */
  filePath?: string;
}
