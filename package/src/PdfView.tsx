import {
  useImperativeHandle,
  useLayoutEffect,
  useMemo,
  useRef,
  type Ref,
} from 'react';
import { Image, type ViewProps } from 'react-native';
import {
  callback,
  getHostComponent,
  type HybridRef,
} from 'react-native-nitro-modules';
import BetterPdfViewConfig from '../nitrogen/generated/shared/json/BetterPdfViewConfig.json';
import { buildOutlineTree } from './outline';
import { normalizeSource, sourceKey, type PdfSource } from './source';
import type {
  PdfViewMethods,
  PdfViewProps as NativeProps,
} from './specs/BetterPdfView.nitro';
import type { NativePdfSource } from './types/NativePdfSource';
import type { PdfError } from './types/PdfError';
import type { PdfFitPolicy } from './types/PdfFitPolicy';
import type { PdfLinkPressEvent } from './types/PdfLinkPressEvent';
import type { PdfLoadEvent } from './types/PdfLoadEvent';
import type { PdfLoadProgressEvent } from './types/PdfLoadProgressEvent';
import type { PdfOutlineItem } from './types/PdfOutlineItem';
import type { PdfPageChangeEvent } from './types/PdfPageChangeEvent';
import type { PdfPageTapEvent } from './types/PdfPageTapEvent';
import type { PdfScaleChangeEvent } from './types/PdfScaleChangeEvent';
import type { PdfTextSelectionChangeEvent } from './types/PdfTextSelectionChangeEvent';
import type { PdfViewStats } from './types/PdfViewStats';

const NativeBetterPdfView = getHostComponent<NativeProps, PdfViewMethods>(
  'BetterPdfView',
  () => BetterPdfViewConfig,
);

type NativeRef = HybridRef<NativeProps, PdfViewMethods>;

export interface NavigationOptions {
  /** Default `true`. */
  animated?: boolean;
}

export interface PdfViewRef {
  /** Scrolls so `page` (zero-based, clamped) is at the top of the viewport. */
  goToPage(page: number, options?: NavigationOptions): void;
  /** Zooms around the viewport center. Relative to the fitted page, clamped to [minScale, maxScale]. */
  zoomTo(scale: number, options?: NavigationOptions): void;
  /** Native diagnostics. `null` before the native view has mounted. */
  getStats(): PdfViewStats | null;
  /**
   * The document's table of contents (outline / bookmarks), or `[]` when it
   * has none. Read off the main thread on first call, then cached. Rejects
   * before {@linkcode PdfViewProps.onLoad} and, on Android, for encrypted
   * documents (their titles are encrypted).
   */
  getOutline(): Promise<PdfOutlineItem[]>;
}

export interface PdfViewProps extends ViewProps {
  source: PdfSource;
  password?: string;
  /** Zero-based page shown when a document finishes loading. Default `0`. */
  initialPage?: number;
  /**
   * Zoom applied once when a document finishes loading, relative to the
   * fitted page and clamped to [`minScale`, `maxScale`]; like
   * {@linkcode PdfViewProps.initialPage}, later changes have no effect (use
   * `ref.zoomTo()`). Not reported through
   * {@linkcode PdfViewProps.onScaleChange}. Default `1`.
   */
  initialScale?: number;
  /** Relative to the fitted page (see {@linkcode PdfViewProps.fitPolicy}). Default `1`. */
  minScale?: number;
  /** Relative to the fitted page (see {@linkcode PdfViewProps.fitPolicy}). Default `4`. */
  maxScale?: number;
  /** Gap between pages in dp / pt. Default `8`. */
  pageSpacing?: number;
  /**
   * Lay pages out left to right and scroll horizontally. Default `false`.
   */
  horizontal?: boolean;
  /**
   * Show one page at a time: swipes snap to the previous or next page. A
   * page longer than the view scrolls freely before moving on. While zoomed
   * in, panning is free; zooming back out snaps to a page again. Swipes
   * follow {@linkcode PdfViewProps.horizontal}: vertical by default.
   * Default `false`.
   */
  enablePaging?: boolean;
  /**
   * Right-to-left reading order for horizontal layouts (Arabic, Hebrew,
   * manga): the first page is on the right and reading continues to the
   * left, with or without {@linkcode PdfViewProps.enablePaging}. Page
   * numbers keep document order. No effect on vertical layouts or
   * {@linkcode PdfViewProps.singlePage}. Default `false`.
   *
   * iOS: PDFKit honours right-to-left only with
   * {@linkcode PdfViewProps.enablePaging}; a continuous horizontal layout
   * stays left to right.
   */
  enableRTL?: boolean;
  /**
   * Show only one page, as a static image — for thumbnails and previews.
   * The page is {@linkcode PdfViewProps.initialPage}; `ref.goToPage()`
   * switches it. The view ignores touches, so it can sit inside a
   * `Pressable` or a scrolling list: no scrolling, zooming,
   * {@linkcode PdfViewProps.onPageTap} or {@linkcode PdfViewProps.onLinkPress}.
   * Other pages are never rendered. Takes precedence over
   * {@linkcode PdfViewProps.horizontal} and
   * {@linkcode PdfViewProps.enablePaging}. Default `false`.
   */
  singlePage?: boolean;
  /**
   * How pages are scaled to the view at zoom `1`; `minScale`, `maxScale` and
   * `zoomTo` are relative to this fit. Default `'width'` for the vertical
   * continuous layout, `'page'` with {@linkcode PdfViewProps.horizontal},
   * {@linkcode PdfViewProps.enablePaging} or
   * {@linkcode PdfViewProps.singlePage}.
   *
   * Android fits every page individually. iOS (PDFKit) applies one scale to
   * the whole continuous document: `'width'` fits the widest page when
   * scrolling vertically; otherwise the first page is fitted.
   */
  fitPolicy?: PdfFitPolicy;
  /**
   * Double tap toggles zoom (Android: fitted page ↔ 2×; iOS: PDFKit's own
   * zoom step, e.g. 1.7× for an A4 page on an iPhone). When `false`,
   * double taps do nothing and {@linkcode PdfViewProps.onPageTap} fires
   * right away instead of after the double-tap window (~300 ms). Pinch zoom
   * and `zoomTo()` are unaffected. Default `true`.
   */
  enableDoubleTapZoom?: boolean;
  /**
   * When `false`, dragging does not scroll the document: the drag goes to
   * parent views instead (e.g. a surrounding `ScrollView` scrolls). Pinch
   * and double-tap zoom, {@linkcode PdfViewProps.onPageTap},
   * {@linkcode PdfViewProps.onLinkPress} and `ref.goToPage()` / `zoomTo()`
   * keep working. Same name and default as React Native's `ScrollView`.
   * Default `true`.
   */
  scrollEnabled?: boolean;
  /** Show the vertical scroll indicator while scrolling. Default `true`. */
  showsVerticalScrollIndicator?: boolean;
  /**
   * Show the horizontal scroll indicator while scrolling a page that is
   * zoomed wider than the view. Default `true`.
   */
  showsHorizontalScrollIndicator?: boolean;
  /**
   * Let the user select text, as in a read-only text field: a long press
   * selects a word, handles adjust the selection and the system menu offers
   * Copy and Select all. `false` turns selection off entirely. Default
   * `true`.
   *
   * iOS uses PDFKit's selection, which can span pages. Android draws its
   * own in the app theme's colours; a selection stays on one page, and
   * copying ends it. Android: needs Android 15+, or Android 12–14 with SDK
   * extension 13; other devices ignore long presses.
   */
  enableTextSelection?: boolean;
  onLoad?: (event: PdfLoadEvent) => void;
  /**
   * Download progress of a remote document, at most every 100 ms plus once on
   * completion (before {@linkcode PdfViewProps.onLoad}). Not called for local
   * files or documents already downloaded in this app session.
   */
  onLoadProgress?: (event: PdfLoadProgressEvent) => void;
  onError?: (error: PdfError) => void;
  onPageChange?: (event: PdfPageChangeEvent) => void;
  /**
   * The user tapped a link to a URL. Links to other pages of the document
   * are followed natively and not reported here. The viewer never opens URLs
   * itself — without this handler, URL links do nothing.
   *
   * Android: link detection needs Android 15+, or Android 12–14 with SDK
   * extension 13; other devices report the tap through
   * {@linkcode PdfViewProps.onPageTap} instead.
   */
  onLinkPress?: (event: PdfLinkPressEvent) => void;
  /**
   * The user tapped a page outside of any link. Delivered after the
   * double-tap-to-zoom window has passed.
   */
  onPageTap?: (event: PdfPageTapEvent) => void;
  /**
   * The zoom settled at a new value — once per change, after a pinch or
   * double-tap ends or `ref.zoomTo()` finishes; never during a gesture.
   * Not called when the value is unchanged (e.g. rotation keeps the relative
   * zoom).
   */
  onScaleChange?: (event: PdfScaleChangeEvent) => void;
  /**
   * The text selection settled at different text — after a long press, a
   * handle drag or Select all ends, never while a handle moves — or was
   * cleared (`text` is `''`), e.g. by a tap elsewhere or loading another
   * document.
   */
  onTextSelectionChange?: (event: PdfTextSelectionChangeEvent) => void;
  ref?: Ref<PdfViewRef>;
}

type Handlers = Pick<
  PdfViewProps,
  | 'onLoad'
  | 'onLoadProgress'
  | 'onError'
  | 'onPageChange'
  | 'onLinkPress'
  | 'onPageTap'
  | 'onScaleChange'
  | 'onTextSelectionChange'
>;

/**
 * Keeps the native source prop referentially stable while its value is
 * unchanged. Nitro diffs props by JS identity, so without this an inline
 * `source={{ uri }}` would re-send the source to native on every render.
 */
function useStableSource(source: PdfSource): NativePdfSource {
  const normalized = normalizeSource(source, Image.resolveAssetSource);
  const key = sourceKey(normalized);
  const cached = useRef<{ key: string; value: NativePdfSource } | null>(null);
  if (cached.current?.key !== key) {
    cached.current = { key, value: normalized };
  }
  return cached.current.value;
}

/**
 * A native, virtualized PDF viewer. Scrolling, zooming, rendering and
 * caching all happen natively; JS only sends configuration and receives
 * load / page-change / error events.
 */
export function PdfView({
  source,
  password,
  initialPage,
  initialScale,
  minScale,
  maxScale,
  pageSpacing,
  horizontal,
  enablePaging,
  enableRTL,
  singlePage,
  fitPolicy,
  enableDoubleTapZoom,
  scrollEnabled,
  showsVerticalScrollIndicator,
  showsHorizontalScrollIndicator,
  enableTextSelection,
  onLoad,
  onLoadProgress,
  onError,
  onPageChange,
  onLinkPress,
  onPageTap,
  onScaleChange,
  onTextSelectionChange,
  ref,
  ...viewProps
}: PdfViewProps) {
  const nativeSource = useStableSource(source);

  // Callbacks read the latest handler through a ref so the wrapped functions
  // handed to native never change identity after mount.
  const renderedHandlers: Handlers = {
    onLoad,
    onLoadProgress,
    onError,
    onPageChange,
    onLinkPress,
    onPageTap,
    onScaleChange,
    onTextSelectionChange,
  };
  const handlers = useRef(renderedHandlers);
  useLayoutEffect(() => {
    handlers.current = renderedHandlers;
  });
  const nativeCallbacks = useMemo(
    () => ({
      onLoad: callback((event: PdfLoadEvent) =>
        handlers.current.onLoad?.(event),
      ),
      onLoadProgress: callback((event: PdfLoadProgressEvent) =>
        handlers.current.onLoadProgress?.(event),
      ),
      onError: callback((error: PdfError) => handlers.current.onError?.(error)),
      onPageChange: callback((event: PdfPageChangeEvent) =>
        handlers.current.onPageChange?.(event),
      ),
      onLinkPress: callback((event: PdfLinkPressEvent) =>
        handlers.current.onLinkPress?.(event),
      ),
      onPageTap: callback((event: PdfPageTapEvent) =>
        handlers.current.onPageTap?.(event),
      ),
      onScaleChange: callback((event: PdfScaleChangeEvent) =>
        handlers.current.onScaleChange?.(event),
      ),
      onTextSelectionChange: callback((event: PdfTextSelectionChangeEvent) =>
        handlers.current.onTextSelectionChange?.(event),
      ),
    }),
    [],
  );

  const nativeRef = useRef<NativeRef | null>(null);
  const hybridRef = useMemo(
    () =>
      callback((instance: NativeRef) => {
        nativeRef.current = instance;
      }),
    [],
  );

  useImperativeHandle(
    ref,
    () => ({
      goToPage(page, options) {
        nativeRef.current?.goToPage(page, options?.animated ?? true);
      },
      zoomTo(scale, options) {
        nativeRef.current?.zoomTo(scale, options?.animated ?? true);
      },
      async getOutline() {
        const native = nativeRef.current;
        if (native == null) throw new Error('The PDF view is not mounted');
        return buildOutlineTree(await native.getOutline());
      },
      getStats() {
        return nativeRef.current?.getStats() ?? null;
      },
    }),
    [],
  );

  // Only subscribe to events the app listens to, so native skips the JS hop
  // entirely for the others. Once subscribed, an event stays subscribed (the
  // handler ref makes a removed handler a no-op): unsubscribing would send
  // `null`, which Nitro rejects.
  const subscribed = useRef(new Set<keyof Handlers>());
  const subscription = <K extends keyof Handlers>(name: K) => {
    if (renderedHandlers[name] !== undefined) subscribed.current.add(name);
    return subscribed.current.has(name) ? nativeCallbacks[name] : undefined;
  };

  return (
    <NativeBetterPdfView
      {...viewProps}
      source={nativeSource}
      // Every optional prop is sent as a concrete value: when a prop is
      // removed, Fabric sends `null`, which Nitro's optional converters reject.
      // An empty password means "none" on both platforms.
      password={password ?? ''}
      initialPage={initialPage ?? 0}
      initialScale={initialScale ?? 1}
      minScale={minScale ?? 1}
      maxScale={maxScale ?? 4}
      pageSpacing={pageSpacing ?? 8}
      horizontal={horizontal ?? false}
      enablePaging={enablePaging ?? false}
      enableRTL={enableRTL ?? false}
      singlePage={singlePage ?? false}
      fitPolicy={
        fitPolicy ??
        (horizontal === true || enablePaging === true || singlePage === true
          ? 'page'
          : 'width')
      }
      enableDoubleTapZoom={enableDoubleTapZoom ?? true}
      scrollEnabled={scrollEnabled ?? true}
      showsVerticalScrollIndicator={showsVerticalScrollIndicator ?? true}
      showsHorizontalScrollIndicator={showsHorizontalScrollIndicator ?? true}
      enableTextSelection={enableTextSelection ?? true}
      onLoad={subscription('onLoad')}
      onLoadProgress={subscription('onLoadProgress')}
      onError={subscription('onError')}
      onPageChange={subscription('onPageChange')}
      onLinkPress={subscription('onLinkPress')}
      onPageTap={subscription('onPageTap')}
      onScaleChange={subscription('onScaleChange')}
      onTextSelectionChange={subscription('onTextSelectionChange')}
      hybridRef={hybridRef}
    />
  );
}
