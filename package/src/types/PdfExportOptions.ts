/** Options of `PdfViewRef.exportDocument`. */
export interface PdfExportOptions {
  /**
   * Absolute path (or `file://` URI) of the file to write; its directory
   * must exist and an existing file is replaced. Default: a new file in the
   * app's cache directory, named after the document.
   */
  filePath?: string;
}
