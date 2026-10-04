import type { PdfFormField } from './PdfFormField';

/** A form field the reader changed, with its new value. */
export interface PdfFormChangeEvent {
  field: PdfFormField;
}
