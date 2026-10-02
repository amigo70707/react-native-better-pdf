import type { NativePdfSource } from './types/NativePdfSource';

/**
 * The print job name of a document whose app gave none: the source's file
 * name (`report.pdf` for `https://example.com/files/report.pdf?token=…`),
 * or `'Document'` when the source names no file (`content://` URIs name
 * rows, not files).
 */
export function defaultPrintJobName(source: NativePdfSource): string {
  if (source.uri.startsWith('content:')) return 'Document';
  const path = source.uri.split(/[?#]/)[0] ?? '';
  const segment = path.slice(path.lastIndexOf('/') + 1);
  let name = segment;
  try {
    name = decodeURIComponent(segment);
  } catch {
    // A malformed escape: keep the segment as written.
  }
  return name.trim() === '' ? 'Document' : name;
}
