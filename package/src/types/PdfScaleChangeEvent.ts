import type { PdfViewProps } from '../PdfView';

/**
 * The zoom settled at a new value.
 *
 * @see {@linkcode PdfViewProps.onScaleChange}
 */
export interface PdfScaleChangeEvent {
  /** Relative to the fitted page, like `minScale`, `maxScale` and `zoomTo`. */
  scale: number;
}
