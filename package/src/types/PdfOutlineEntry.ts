/**
 * One outline entry as sent by the native view: the tree flattened
 * depth-first, with each entry's depth. `PdfView` rebuilds the tree
 * (`PdfOutlineItem`); a flat struct keeps the Nitro boundary non-recursive.
 */
export interface PdfOutlineEntry {
  title: string;
  /** Zero-based target page; absent when the entry has no destination in the document. */
  page?: number;
  /** 0 for top-level entries. */
  depth: number;
}
