/**
 * Delivered to `PdfView`'s `onTextSelectionChange` once the user's text
 * selection settles at different text.
 */
export interface PdfTextSelectionChangeEvent {
  /** The selected text; empty when the selection was cleared. */
  text: string;
}
