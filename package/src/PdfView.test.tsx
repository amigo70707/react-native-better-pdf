import { beforeEach, describe, expect, mock, test } from 'bun:test';
import { createRef } from 'react';
import { act, create, type ReactTestRenderer } from 'react-test-renderer';

(
  globalThis as { IS_REACT_ACT_ENVIRONMENT?: boolean }
).IS_REACT_ACT_ENVIRONMENT = true;

// --- Native boundary mocks -------------------------------------------------

type HostProps = Record<string, unknown>;
const hostRenders: HostProps[] = [];
const nativeCalls: string[] = [];

const stats: PdfViewStats = {
  pageCount: 3,
  residentPages: 1,
  cacheBytes: 0,
  cacheBudgetBytes: 0,
  cacheHits: 0,
  cacheMisses: 0,
  bitmapAllocations: 0,
  pagesRendered: 0,
  rendersCancelled: 0,
  meanRenderMs: -1,
};

const nativeInstance = {
  goToPage: (page: number, animated: boolean) =>
    nativeCalls.push(`goToPage(${page}, ${animated})`),
  zoomTo: (scale: number, animated: boolean) =>
    nativeCalls.push(`zoomTo(${scale}, ${animated})`),
  getStats: () => stats,
  search: (query: string) => nativeCalls.push(`search(${query})`),
  searchNext: () => nativeCalls.push('searchNext()'),
  searchPrevious: () => nativeCalls.push('searchPrevious()'),
  clearSearch: () => nativeCalls.push('clearSearch()'),
  getPageSizes: () => Promise.resolve([{ width: 595, height: 842 }]),
  renderPage: (page: number, options: { width: number }) =>
    Promise.resolve({
      uri: `file:///page-${page}.png`,
      width: options.width,
      height: 2 * options.width,
    }),
  goToViewport: (viewport: object) =>
    nativeCalls.push(`goToViewport(${JSON.stringify(viewport)})`),
  getOutline: () =>
    Promise.resolve([
      { title: 'Chapter', page: 0, depth: 0 },
      { title: 'Section', page: 2, depth: 1 },
    ]),
};

function FakeHost(props: HostProps) {
  hostRenders.push(props);
  return null;
}

mock.module('react-native', () => ({
  Image: {
    resolveAssetSource: (asset: number) => ({ uri: `asset://${asset}` }),
  },
  // Like React Native: a number for plain colours, an object for
  // PlatformColor, null for invalid strings.
  processColor: (color: unknown) =>
    color === 'blue'
      ? 0xff0000ff
      : typeof color === 'object'
        ? { semantic: ['label'] }
        : null,
}));
// Module mocks are shared by all test files: export everything the package
// imports from Nitro (PdfCache.test.ts needs NitroModules).
mock.module('react-native-nitro-modules', () => ({
  callback: <T,>(f: T) => ({ f }),
  getHostComponent: () => FakeHost,
  NitroModules: {
    createHybridObject: () => {
      throw new Error('Not available in PdfView tests');
    },
  },
}));
mock.module(
  '../nitrogen/generated/shared/json/BetterPdfViewConfig.json',
  () => ({ default: {} }),
);

const { PdfView } = await import('./PdfView');
import type { PdfViewRef } from './PdfView';
import type { PdfViewStats } from './types/PdfViewStats';

// ---------------------------------------------------------------------------

const lastHost = () => {
  const props = hostRenders[hostRenders.length - 1];
  if (props == null) throw new Error('host not rendered');
  return props;
};

function render(element: React.ReactElement): ReactTestRenderer {
  let renderer: ReactTestRenderer | undefined;
  act(() => {
    renderer = create(element);
  });
  if (renderer == null) throw new Error('render failed');
  return renderer;
}

beforeEach(() => {
  hostRenders.length = 0;
  nativeCalls.length = 0;
});

describe('PdfView', () => {
  test('keeps the native source identity when an equal inline source is re-rendered', () => {
    const renderer = render(
      <PdfView source={{ uri: 'file:///a.pdf', headers: { A: '1' } }} />,
    );
    const first = lastHost().source;
    act(() =>
      renderer.update(
        <PdfView source={{ uri: 'file:///a.pdf', headers: { A: '1' } }} />,
      ),
    );
    expect(lastHost().source).toBe(first);
  });

  test('sends a new source object when the value changes', () => {
    const renderer = render(<PdfView source={{ uri: 'file:///a.pdf' }} />);
    const first = lastHost().source;
    act(() => renderer.update(<PdfView source={{ uri: 'file:///b.pdf' }} />));
    expect(lastHost().source).not.toBe(first);
    expect(lastHost().source).toEqual({ uri: 'file:///b.pdf' });
  });

  test('resolves require() assets', () => {
    render(<PdfView source={12} />);
    expect(lastHost().source).toEqual({ uri: 'asset://12' });
  });

  test('forwards link presses and page taps to the latest handlers through stable callbacks', () => {
    const received: string[] = [];
    const renderer = render(
      <PdfView
        source={{ uri: 'x' }}
        onLinkPress={(event) => received.push(`old link ${event.url}`)}
        onPageTap={(event) => received.push(`old tap ${event.page}`)}
      />,
    );
    const link = lastHost().onLinkPress as {
      f: (event: { url: string }) => void;
    };
    const tap = lastHost().onPageTap as {
      f: (event: { page: number; x: number; y: number }) => void;
    };
    act(() =>
      renderer.update(
        <PdfView
          source={{ uri: 'x' }}
          onLinkPress={(event) => received.push(`link ${event.url}`)}
          onPageTap={(event) =>
            received.push(`tap ${event.page} ${event.x} ${event.y}`)
          }
        />,
      ),
    );
    expect(lastHost().onLinkPress).toBe(link);
    expect(lastHost().onPageTap).toBe(tap);
    link.f({ url: 'https://example.com' });
    tap.f({ page: 2, x: 10, y: 20 });
    expect(received).toEqual(['link https://example.com', 'tap 2 10 20']);
  });

  test('forwards load progress through a stable callback', () => {
    const received: Array<[number, number | undefined]> = [];
    const renderer = render(
      <PdfView source={{ uri: 'x' }} onLoadProgress={() => {}} />,
    );
    const progress = lastHost().onLoadProgress as {
      f: (event: { loadedBytes: number; totalBytes?: number }) => void;
    };
    act(() =>
      renderer.update(
        <PdfView
          source={{ uri: 'x' }}
          onLoadProgress={(event) =>
            received.push([event.loadedBytes, event.totalBytes])
          }
        />,
      ),
    );
    expect(lastHost().onLoadProgress).toBe(progress);
    progress.f({ loadedBytes: 10, totalBytes: 100 });
    progress.f({ loadedBytes: 20 });
    expect(received).toEqual([
      [10, 100],
      [20, undefined],
    ]);
  });

  test('passes no native callback for events the app does not listen to', () => {
    render(<PdfView source={{ uri: 'x' }} onLoad={() => {}} />);
    expect(lastHost().onLoad).toBeDefined();
    expect(lastHost().onPageChange).toBeUndefined();
    expect(lastHost().onError).toBeUndefined();
    expect(lastHost().onLoadProgress).toBeUndefined();
    expect(lastHost().onLinkPress).toBeUndefined();
    expect(lastHost().onPageTap).toBeUndefined();
    expect(lastHost().onScaleChange).toBeUndefined();
    expect(lastHost().onTextSelectionChange).toBeUndefined();
  });

  test('forwards text selection changes through a stable callback', () => {
    const texts: string[] = [];
    render(
      <PdfView
        source={{ uri: 'x' }}
        onTextSelectionChange={(event) => texts.push(event.text)}
      />,
    );
    const wrapped = lastHost().onTextSelectionChange as {
      f: (event: { text: string }) => void;
    };
    wrapped.f({ text: 'brown fox' });
    wrapped.f({ text: '' });
    expect(texts).toEqual(['brown fox', '']);
  });

  test('forwards scale changes through a stable callback', () => {
    const scales: number[] = [];
    render(
      <PdfView
        source={{ uri: 'x' }}
        onScaleChange={(event) => scales.push(event.scale)}
      />,
    );
    const wrapped = lastHost().onScaleChange as {
      f: (event: { scale: number }) => void;
    };
    wrapped.f({ scale: 2.5 });
    expect(scales).toEqual([2.5]);
  });

  test('keeps callback identity stable while calling the latest handler', () => {
    const calls: string[] = [];
    const renderer = render(
      <PdfView
        source={{ uri: 'x' }}
        onPageChange={() => calls.push('first')}
      />,
    );
    const wrapped = lastHost().onPageChange as { f: (event: unknown) => void };
    act(() =>
      renderer.update(
        <PdfView
          source={{ uri: 'x' }}
          onPageChange={() => calls.push('second')}
        />,
      ),
    );
    expect(lastHost().onPageChange).toBe(wrapped);
    wrapped.f({ page: 1, pageCount: 2 });
    expect(calls).toEqual(['second']);
  });

  test('never sends undefined for a prop that had a value', () => {
    // Fabric turns a removed prop into `null`, which Nitro rejects; the
    // wrapper must send a concrete value instead.
    const calls: string[] = [];
    const renderer = render(
      <PdfView
        source={{ uri: 'x' }}
        password="p"
        minScale={2}
        fitPolicy="height"
        horizontal
        enableTextSelection={false}
        searchHighlightColor="blue"
        onPageTap={() => calls.push('tap')}
      />,
    );
    const tap = lastHost().onPageTap as { f: (event: unknown) => void };
    act(() => renderer.update(<PdfView source={{ uri: 'x' }} />));
    expect(lastHost()).toMatchObject({
      password: '',
      initialPosition: { page: 0, scale: 1 },
      minScale: 1,
      maxScale: 4,
      pageSpacing: 8,
      horizontal: false,
      enablePaging: false,
      fitPolicy: 'width',
      showsVerticalScrollIndicator: true,
      showsHorizontalScrollIndicator: true,
      singlePage: false,
      enableRTL: false,
      scrollEnabled: true,
      enableDoubleTapZoom: true,
      pageAccessibilityLabel: 'Page {page} of {pageCount}',
      enableTextSelection: true,
      searchHighlightColor: 0x66ffd600,
      searchCurrentHighlightColor: 0x99ff8c00,
    });
    // Still subscribed, but the removed handler is no longer called.
    expect(lastHost().onPageTap).toBe(tap);
    tap.f({ page: 0, x: 1, y: 2 });
    expect(calls).toEqual([]);
  });

  test('sends search highlight colours as ARGB, falling back to the defaults', () => {
    render(
      <PdfView
        source={{ uri: 'x' }}
        searchHighlightColor="blue"
        searchCurrentHighlightColor="not-a-colour"
      />,
    );
    expect(lastHost()).toMatchObject({
      searchHighlightColor: 0xff0000ff,
      searchCurrentHighlightColor: 0x99ff8c00,
    });
    render(
      <PdfView
        source={{ uri: 'x' }}
        searchHighlightColor={{ semantic: ['label'] } as never}
      />,
    );
    expect(lastHost().searchHighlightColor).toBe(0x66ffd600);
  });

  test('defaults fitPolicy to the whole page in horizontal and paged layouts', () => {
    render(<PdfView source={{ uri: 'x' }} enablePaging />);
    expect(lastHost().fitPolicy).toBe('page');
    render(<PdfView source={{ uri: 'x' }} horizontal />);
    expect(lastHost().fitPolicy).toBe('page');
    render(<PdfView source={{ uri: 'x' }} singlePage />);
    expect(lastHost().fitPolicy).toBe('page');
  });

  test('forwards plain props', () => {
    render(
      <PdfView
        source={{ uri: 'x' }}
        initialPage={4}
        minScale={0.5}
        maxScale={6}
        pageSpacing={0}
        showsVerticalScrollIndicator={false}
        showsHorizontalScrollIndicator
        horizontal
        enablePaging
        fitPolicy="height"
        password="p"
        enableTextSelection={false}
      />,
    );
    expect(lastHost()).toMatchObject({
      initialPosition: { page: 4, scale: 1 },
      minScale: 0.5,
      maxScale: 6,
      pageSpacing: 0,
      showsVerticalScrollIndicator: false,
      showsHorizontalScrollIndicator: true,
      horizontal: true,
      enablePaging: true,
      fitPolicy: 'height',
      password: 'p',
      enableTextSelection: false,
    });
  });

  test('ref methods default to animated and map options to native arguments', () => {
    const ref = createRef<PdfViewRef>();
    render(<PdfView ref={ref} source={{ uri: 'x' }} />);
    expect(ref.current?.getStats()).toBeNull();

    const hybridRef = lastHost().hybridRef as {
      f: (instance: typeof nativeInstance) => void;
    };
    hybridRef.f(nativeInstance);

    ref.current?.goToPage(3);
    ref.current?.goToPage(5, { animated: false });
    ref.current?.zoomTo(2);
    expect(nativeCalls).toEqual([
      'goToPage(3, true)',
      'goToPage(5, false)',
      'zoomTo(2, true)',
    ]);
    expect(ref.current?.getStats()).toBe(stats);
  });

  test('initialViewport takes precedence and keeps the native prop stable', () => {
    const viewport = { page: 7, x: -4, y: 120.5, scale: 2 };
    const renderer = render(
      <PdfView
        source={{ uri: 'x' }}
        initialPage={3}
        initialScale={1.5}
        initialViewport={viewport}
      />,
    );
    const position = lastHost().initialPosition;
    expect(position).toEqual({ page: 7, scale: 2, x: -4, y: 120.5 });
    act(() =>
      renderer.update(
        <PdfView
          source={{ uri: 'x' }}
          initialPage={3}
          initialScale={1.5}
          initialViewport={{ ...viewport }}
        />,
      ),
    );
    expect(lastHost().initialPosition).toBe(position);
    act(() =>
      renderer.update(
        <PdfView source={{ uri: 'x' }} initialPage={3} initialScale={1.5} />,
      ),
    );
    expect(lastHost().initialPosition).toEqual({ page: 3, scale: 1.5 });
  });

  test('goToViewport sends only the position fields', () => {
    const ref = createRef<PdfViewRef>();
    render(<PdfView ref={ref} source={{ uri: 'x' }} />);
    const hybridRef = lastHost().hybridRef as {
      f: (instance: typeof nativeInstance) => void;
    };
    hybridRef.f(nativeInstance);
    // e.g. a whole saved event spread into the viewport.
    const saved = { page: 2, x: 0, y: 10, scale: 1, isAtEnd: false };
    ref.current?.goToViewport(saved);
    expect(nativeCalls).toEqual([
      'goToViewport({"page":2,"x":0,"y":10,"scale":1})',
    ]);
  });

  test('forwards viewport changes through a stable callback', () => {
    const events: unknown[] = [];
    render(
      <PdfView
        source={{ uri: 'x' }}
        onViewportChange={(e) => events.push(e)}
      />,
    );
    const event = {
      viewport: { page: 1, x: 0, y: 3, scale: 1 },
      firstVisiblePage: 1,
      lastVisiblePage: 2,
      isAtEnd: false,
    };
    (lastHost().onViewportChange as { f: (e: unknown) => void }).f(event);
    expect(events).toEqual([event]);
  });

  test('getPageSizes rejects before mount and forwards after', async () => {
    const ref = createRef<PdfViewRef>();
    render(<PdfView ref={ref} source={{ uri: 'x' }} />);
    await expect(ref.current!.getPageSizes()).rejects.toThrow('not mounted');
    const hybridRef = lastHost().hybridRef as {
      f: (instance: typeof nativeInstance) => void;
    };
    hybridRef.f(nativeInstance);
    expect(await ref.current!.getPageSizes()).toEqual([
      { width: 595, height: 842 },
    ]);
  });

  test('renderPage rejects before mount and forwards after', async () => {
    const ref = createRef<PdfViewRef>();
    render(<PdfView ref={ref} source={{ uri: 'x' }} />);
    await expect(ref.current!.renderPage(0, { width: 100 })).rejects.toThrow(
      'not mounted',
    );
    const hybridRef = lastHost().hybridRef as {
      f: (instance: typeof nativeInstance) => void;
    };
    hybridRef.f(nativeInstance);
    expect(await ref.current!.renderPage(3, { width: 100 })).toEqual({
      uri: 'file:///page-3.png',
      width: 100,
      height: 200,
    });
  });

  test('search methods forward to the native view', () => {
    const ref = createRef<PdfViewRef>();
    const events: number[] = [];
    render(
      <PdfView
        ref={ref}
        source={{ uri: 'x' }}
        onSearchChange={(event) => events.push(event.matchCount)}
      />,
    );
    const hybridRef = lastHost().hybridRef as {
      f: (instance: typeof nativeInstance) => void;
    };
    hybridRef.f(nativeInstance);
    ref.current?.search('lorem');
    ref.current?.searchNext();
    ref.current?.searchPrevious();
    ref.current?.clearSearch();
    expect(nativeCalls).toEqual([
      'search(lorem)',
      'searchNext()',
      'searchPrevious()',
      'clearSearch()',
    ]);
    const wrapped = lastHost().onSearchChange as {
      f: (event: { matchCount: number }) => void;
    };
    wrapped.f({ matchCount: 3 });
    expect(events).toEqual([3]);
  });

  test('getOutline rejects before mount and returns a tree after', async () => {
    const ref = createRef<PdfViewRef>();
    render(<PdfView ref={ref} source={{ uri: 'x' }} />);
    await expect(ref.current!.getOutline()).rejects.toThrow('not mounted');
    const hybridRef = lastHost().hybridRef as {
      f: (instance: typeof nativeInstance) => void;
    };
    hybridRef.f(nativeInstance);
    expect(await ref.current!.getOutline()).toEqual([
      {
        title: 'Chapter',
        page: 0,
        children: [{ title: 'Section', page: 2, children: [] }],
      },
    ]);
  });
});
