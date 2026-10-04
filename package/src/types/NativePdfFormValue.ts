/**
 * A value to set, as sent to the native view: exactly one of `text`,
 * `isChecked` and `options`, or none to clear the field.
 */
export interface NativePdfFormValue {
  name: string;
  text?: string;
  isChecked?: boolean;
  options?: string[];
}
