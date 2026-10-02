import type { PdfViewProps } from '../PdfView';

/**
 * Colours pages are shown in.
 *
 * - `'light'`: as the document defines them.
 * - `'dark'`: lightness inverted, hues kept — white paper turns black,
 *   black text white, and a red heading stays red.
 *
 * The same values as React Native's `useColorScheme()`.
 *
 * @see {@linkcode PdfViewProps.colorScheme}
 */
export type PdfColorScheme = 'light' | 'dark';
