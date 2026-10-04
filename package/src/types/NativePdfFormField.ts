import type { PdfFormFieldType } from './PdfFormFieldType';
import type { PdfFormWidget } from './PdfFormWidget';

/**
 * A form field as sent by the native view: one flat struct for every type,
 * every member concrete. `PdfView` turns it into a `PdfFormField`.
 */
export interface NativePdfFormField {
  name: string;
  type: PdfFormFieldType;
  isReadOnly: boolean;
  isRequired: boolean;
  widgets: PdfFormWidget[];
  /** Text field and combo box: the value ('' when empty). */
  text: string;
  /** Checkbox: checked. */
  isChecked: boolean;
  /** Radio: each widget's option; combo and list box: the options. */
  options: string[];
  /** Radio, combo and list box: the selected options (a combo box's typed text too). */
  selectedOptions: string[];
  isMultiline: boolean;
  /** Text field: maximum length, 0 when unlimited. */
  maxLength: number;
  isMultiSelect: boolean;
  isEditable: boolean;
  isSigned: boolean;
}
