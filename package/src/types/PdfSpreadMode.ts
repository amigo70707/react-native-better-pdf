import type { PdfViewProps } from '../PdfView';

/**
 * How facing pages are shown side by side.
 *
 * - `'none'`: one page per row.
 * - `'pairs'`: pages 1–2, 3–4, … side by side, like slides or a document
 *   printed two-up.
 * - `'book'`: the first page alone, like a cover, then 2–3, 4–5, …, like
 *   an open book or magazine.
 *
 * @see {@linkcode PdfViewProps.spreadMode}
 */
export type PdfSpreadMode = 'none' | 'pairs' | 'book';
