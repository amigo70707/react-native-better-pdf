import { describe, expect, test } from 'bun:test';
import {
  toFormField,
  toNativeFilePath,
  toNativeFormValues,
} from './formFields';
import type { NativePdfFormField } from './types/NativePdfFormField';

const widget = { page: 0, x: 10, y: 20, width: 100, height: 20 };

const native = (
  overrides: Partial<NativePdfFormField> & Pick<NativePdfFormField, 'type'>,
): NativePdfFormField => ({
  name: 'field',
  isReadOnly: false,
  isRequired: false,
  widgets: [widget],
  text: '',
  isChecked: false,
  options: [],
  selectedOptions: [],
  isMultiline: false,
  maxLength: 0,
  isMultiSelect: false,
  isEditable: false,
  isSigned: false,
  ...overrides,
});

const base = {
  name: 'field',
  isReadOnly: false,
  isRequired: false,
  widgets: [widget],
};

describe('toFormField', () => {
  test('keeps only the members of each type', () => {
    expect(
      toFormField(native({ type: 'text', text: 'Anna', maxLength: 0 })),
    ).toEqual({ ...base, type: 'text', value: 'Anna', isMultiline: false });
    expect(
      toFormField(native({ type: 'text', isMultiline: true, maxLength: 5 })),
    ).toEqual({
      ...base,
      type: 'text',
      value: '',
      isMultiline: true,
      maxLength: 5,
    });
    expect(toFormField(native({ type: 'checkbox', isChecked: true }))).toEqual({
      ...base,
      type: 'checkbox',
      isChecked: true,
    });
    expect(
      toFormField(
        native({ type: 'signature', isSigned: true, isRequired: true }),
      ),
    ).toEqual({ ...base, isRequired: true, type: 'signature', isSigned: true });
    expect(toFormField(native({ type: 'push-button' }))).toEqual({
      ...base,
      type: 'push-button',
    });
  });

  test('reports no selection as null for radios and combo boxes', () => {
    const options = ['basic', 'pro'];
    expect(toFormField(native({ type: 'radio', options }))).toEqual({
      ...base,
      type: 'radio',
      value: null,
      options,
    });
    expect(
      toFormField(native({ type: 'radio', options, selectedOptions: ['pro'] })),
    ).toMatchObject({ value: 'pro' });
    expect(
      toFormField(native({ type: 'combo-box', options, isEditable: true })),
    ).toEqual({
      ...base,
      type: 'combo-box',
      value: null,
      options,
      isEditable: true,
    });
    expect(
      toFormField(native({ type: 'combo-box', options, text: 'pro' })),
    ).toMatchObject({ value: 'pro' });
  });

  test('reports a list box selection as values', () => {
    expect(
      toFormField(
        native({
          type: 'list-box',
          options: ['Red', 'Green'],
          selectedOptions: ['Green'],
          isMultiSelect: true,
        }),
      ),
    ).toEqual({
      ...base,
      type: 'list-box',
      values: ['Green'],
      options: ['Red', 'Green'],
      isMultiSelect: true,
    });
  });
});

describe('toNativeFormValues', () => {
  test('sends each value in the member its kind uses', () => {
    expect(
      toNativeFormValues({
        name: 'Anna',
        agree: true,
        colours: ['Red', 'Blue'],
        country: null,
      }),
    ).toEqual([
      { name: 'name', text: 'Anna' },
      { name: 'agree', isChecked: true },
      { name: 'colours', options: ['Red', 'Blue'] },
      { name: 'country' },
    ]);
  });

  test('rejects other values before calling native code', () => {
    expect(() =>
      toNativeFormValues({ age: 42 } as unknown as Record<string, string>),
    ).toThrow('Form field "age"');
    expect(() =>
      toNativeFormValues({ tags: [1] } as unknown as Record<string, string[]>),
    ).toThrow('Form field "tags"');
  });
});

describe('toNativeFilePath', () => {
  test('decodes file URIs and sends the default as an empty path', () => {
    expect(toNativeFilePath(undefined)).toBe('');
    expect(toNativeFilePath('/tmp/a b.pdf')).toBe('/tmp/a b.pdf');
    expect(toNativeFilePath('file:///tmp/a%20b.pdf')).toBe('/tmp/a b.pdf');
  });
});
