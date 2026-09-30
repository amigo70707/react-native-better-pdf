import type {
  HybridView,
  HybridViewMethods,
  HybridViewProps,
} from 'react-native-nitro-modules';
import type { NativePdfSource } from '../types/NativePdfSource';
import type { PdfError } from '../types/PdfError';
import type { PdfFitPolicy } from '../types/PdfFitPolicy';
import type { PdfLinkPressEvent } from '../types/PdfLinkPressEvent';
import type { PdfLoadProgressEvent } from '../types/PdfLoadProgressEvent';
import type { PdfLoadEvent } from '../types/PdfLoadEvent';
import type { PdfOutlineEntry } from '../types/PdfOutlineEntry';
import type { PdfPageChangeEvent } from '../types/PdfPageChangeEvent';
import type { PdfPageTapEvent } from '../types/PdfPageTapEvent';
import type { PdfScaleChangeEvent } from '../types/PdfScaleChangeEvent';
import type { PdfTextSelectionChangeEvent } from '../types/PdfTextSelectionChangeEvent';
import type { PdfViewStats } from '../types/PdfViewStats';

/**
 * Props of the native `BetterPdfView`. Apps use the `PdfView` component,
 * which memoizes the source and keeps callbacks identity-stable.
 */
export interface PdfViewProps extends HybridViewProps {
  source?: NativePdfSource;
  password?: string;
  /** Zero-based page shown once a document finishes loading. */
  initialPage?: number;
  /** Zoom applied once a document finishes loading, relative to the fitted page. Default 1. */
  initialScale?: number;
  /** Relative to the fitted page. Default 1. */
  minScale?: number;
  /** Relative to the fitted page. Default 4. */
  maxScale?: number;
  /** Gap between pages in dp / pt. Default 8. */
  pageSpacing?: number;
  /** Pages follow each other left to right. Default false. */
  horizontal?: boolean;
  /** Snap to one page at a time. Default false. */
  enablePaging?: boolean;
  /** Horizontal layouts run right to left (first page on the right); iOS: paged only. Default false. */
  enableRTL?: boolean;
  /** Show only the page at `initialPage` / `goToPage`, without touch handling. Default false. */
  singlePage?: boolean;
  /** Default 'width' when vertical and continuous, otherwise 'page'. */
  fitPolicy?: PdfFitPolicy;
  /** Double tap toggles zoom; when false, taps are reported without waiting for a second tap. Default true. */
  enableDoubleTapZoom?: boolean;
  /** Drags scroll the document (else they go to parent views). Default true. */
  scrollEnabled?: boolean;
  /** Screen-reader page label; {page} (1-based) and {pageCount} are replaced. Default 'Page {page} of {pageCount}'. */
  pageAccessibilityLabel?: string;
  /** Default true. */
  showsVerticalScrollIndicator?: boolean;
  /** Default true. Only visible while zoomed wider than the view. */
  showsHorizontalScrollIndicator?: boolean;
  /** Long press selects text (Android: API 35+). Default true. */
  enableTextSelection?: boolean;
  onLoad?: (event: PdfLoadEvent) => void;
  onLoadProgress?: (event: PdfLoadProgressEvent) => void;
  onError?: (error: PdfError) => void;
  onPageChange?: (event: PdfPageChangeEvent) => void;
  onLinkPress?: (event: PdfLinkPressEvent) => void;
  onPageTap?: (event: PdfPageTapEvent) => void;
  /** Once per settled zoom change (gesture end, zoomTo), never per frame. */
  onScaleChange?: (event: PdfScaleChangeEvent) => void;
  /** Once per settled selection change (gesture end), never per handle move. */
  onTextSelectionChange?: (event: PdfTextSelectionChangeEvent) => void;
}

/** Imperative methods of the native `BetterPdfView`. */
export interface PdfViewMethods extends HybridViewMethods {
  /** Scrolls so that `page` (zero-based, clamped) is at the top of the viewport. */
  goToPage(page: number, animated: boolean): void;
  /** Zooms around the viewport center. `scale` is relative to the fitted page and clamped to [minScale, maxScale]. */
  zoomTo(scale: number, animated: boolean): void;
  getStats(): PdfViewStats;
  /**
   * The document outline, flattened depth-first. Parsed off the main thread
   * on first call and cached. Rejects before a document has loaded, and on
   * Android for encrypted documents.
   */
  getOutline(): Promise<PdfOutlineEntry[]>;
}

export type BetterPdfView = HybridView<PdfViewProps, PdfViewMethods>;
