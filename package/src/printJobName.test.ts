import { describe, expect, test } from 'bun:test';
import { defaultPrintJobName } from './printJobName';

describe('defaultPrintJobName', () => {
  test("names the job after the source's file", () => {
    expect(
      defaultPrintJobName({
        uri: 'https://example.com/files/Q3%20report.pdf?token=abc#page=2',
      }),
    ).toBe('Q3 report.pdf');
    expect(defaultPrintJobName({ uri: '/data/user/0/app/files/a.pdf' })).toBe(
      'a.pdf',
    );
    expect(
      defaultPrintJobName({
        uri: 'http://localhost:8081/assets/sample.pdf?platform=ios&hash=1',
      }),
    ).toBe('sample.pdf');
    // Android release builds of require(): a resource name.
    expect(defaultPrintJobName({ uri: 'assets_sample' })).toBe('assets_sample');
  });

  test("falls back to 'Document' when the source names no file", () => {
    expect(
      defaultPrintJobName({
        uri: 'content://com.android.providers.downloads/document/42',
      }),
    ).toBe('Document');
    expect(defaultPrintJobName({ uri: 'https://example.com/' })).toBe(
      'Document',
    );
    expect(defaultPrintJobName({ uri: '' })).toBe('Document');
  });

  test('keeps a malformed escape as written', () => {
    expect(defaultPrintJobName({ uri: 'file:///tmp/100%.pdf' })).toBe(
      '100%.pdf',
    );
  });
});
