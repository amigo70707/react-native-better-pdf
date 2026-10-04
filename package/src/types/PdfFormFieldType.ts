/**
 * The kind of a form field (an AcroForm field type and its flags):
 * `'combo-box'` is a drop-down choice, `'list-box'` a scrolling one.
 */
export type PdfFormFieldType =
  | 'text'
  | 'checkbox'
  | 'radio'
  | 'combo-box'
  | 'list-box'
  | 'signature'
  | 'push-button';
