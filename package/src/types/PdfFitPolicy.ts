import type { PdfViewProps } from '../PdfView';

/**
 * How a page is scaled to the view at zoom `1`.
 *
 * - `'width'`: the page width fills the view.
 * - `'height'`: the page height fills the view.
 * - `'page'`: the whole page fits in the view.
 *
 * @see {@linkcode PdfViewProps.fitPolicy}
 */
export type PdfFitPolicy = 'width' | 'height' | 'page';
