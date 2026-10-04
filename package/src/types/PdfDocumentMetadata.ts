/**
 * A document's information dictionary (title, author, dates), as returned
 * by `PdfDocument.getMetadata()`. Entries the document leaves out or empty
 * are `undefined`.
 */
export interface PdfDocumentMetadata {
  title?: string;
  author?: string;
  subject?: string;
  /** As written by the producer, usually separated by commas or spaces. */
  keywords?: string;
  /** The application that created the original document. */
  creator?: string;
  /** The application that converted it to PDF. */
  producer?: string;
  /**
   * When the document was created, in milliseconds since the Unix epoch
   * (`new Date(createdAtMs)`). A date without a time zone is read as UTC.
   */
  createdAtMs?: number;
  /** When the document was last modified, like {@linkcode createdAtMs}. */
  modifiedAtMs?: number;
}
