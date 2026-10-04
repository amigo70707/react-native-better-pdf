/**
 * A value for `PdfViewRef.setFormFieldValues`, by field type: text and
 * combo box — a string; checkbox — a boolean; radio — the option to select;
 * list box — the options to select (or one); `null` clears the field
 * (empty text, unchecked, nothing selected).
 */
export type PdfFormFieldValue = string | boolean | readonly string[] | null;
