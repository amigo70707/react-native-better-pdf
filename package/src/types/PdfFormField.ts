import type { PdfFormWidget } from './PdfFormWidget';

/** What every form field has. */
interface PdfFormFieldBase {
  /** Fully qualified name, parent names first: `'address.city'`. */
  name: string;
  /** The document forbids changing it. */
  isReadOnly: boolean;
  /** The document marks it as required (not enforced). */
  isRequired: boolean;
  /** Where the field is shown; a radio group has one widget per button. */
  widgets: PdfFormWidget[];
}

export interface PdfTextFormField extends PdfFormFieldBase {
  type: 'text';
  value: string;
  isMultiline: boolean;
  /** Maximum number of characters; absent when unlimited. */
  maxLength?: number;
}

export interface PdfCheckboxFormField extends PdfFormFieldBase {
  type: 'checkbox';
  isChecked: boolean;
}

export interface PdfRadioFormField extends PdfFormFieldBase {
  type: 'radio';
  /** The selected option, `null` when none is. */
  value: string | null;
  /** Each button's option, in the order of `widgets`. */
  options: string[];
}

export interface PdfComboBoxFormField extends PdfFormFieldBase {
  type: 'combo-box';
  /** The chosen option or typed text, `null` when empty. */
  value: string | null;
  options: string[];
  /** Text other than the options can be typed in. */
  isEditable: boolean;
}

export interface PdfListBoxFormField extends PdfFormFieldBase {
  type: 'list-box';
  /** The selected options. */
  values: string[];
  options: string[];
  isMultiSelect: boolean;
}

export interface PdfSignatureFormField extends PdfFormFieldBase {
  type: 'signature';
  /** The document carries a signature value for it (not verified). */
  isSigned: boolean;
}

export interface PdfPushButtonFormField extends PdfFormFieldBase {
  type: 'push-button';
}

/** A form (AcroForm) field with its current value, told apart by `type`. */
export type PdfFormField =
  | PdfTextFormField
  | PdfCheckboxFormField
  | PdfRadioFormField
  | PdfComboBoxFormField
  | PdfListBoxFormField
  | PdfSignatureFormField
  | PdfPushButtonFormField;
