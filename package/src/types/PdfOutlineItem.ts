import type { PdfViewRef } from '../PdfView';

/**
 * A table-of-contents entry (PDF outline / bookmark).
 *
 * @see {@linkcode PdfViewRef.getOutline}
 */
export interface PdfOutlineItem {
  title: string;
  /**
   * Zero-based page the entry points to. `undefined` for entries without a
   * destination in this document: headings that only group children, links
   * to URLs or other files, and broken references.
   */
  page?: number;
  children: PdfOutlineItem[];
}
