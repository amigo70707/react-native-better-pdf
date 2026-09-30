import type { PdfError } from './PdfError';

/**
 * Stable, platform-independent reason a document could not be loaded or a
 * page could not be rendered.
 *
 * @see {@linkcode PdfError.code}
 */
export type PdfErrorCode =
  | 'invalid-source'
  | 'file-not-found'
  | 'network-error'
  | 'http-error'
  | 'invalid-pdf'
  | 'password-required'
  | 'invalid-password'
  | 'unsupported-pdf'
  | 'page-render-failed'
  | 'out-of-memory';
