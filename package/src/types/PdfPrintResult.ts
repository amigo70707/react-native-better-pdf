/** How the system print dialog opened by `PdfViewRef.print` was closed. */
export interface PdfPrintResult {
  /**
   * `true` when the user sent the document to a printer (or saved it as
   * PDF), `false` when they closed the dialog without printing.
   */
  isSubmitted: boolean;
}
