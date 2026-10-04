import type { NativePdfFormField } from './NativePdfFormField';

/** `onFormChange` as sent by the native view. */
export interface NativePdfFormChangeEvent {
  field: NativePdfFormField;
}
