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
}));
mock.module('react-native-nitro-modules', () => ({
  callback: <T,>(f: T) => ({ f }),
  getHostComponent: () => FakeHost,
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
        onPageTap={() => calls.push('tap')}
      />,
    );
    const tap = lastHost().onPageTap as { f: (event: unknown) => void };
    act(() => renderer.update(<PdfView source={{ uri: 'x' }} />));
    expect(lastHost()).toMatchObject({
      password: '',
      initialPage: 0,
      initialScale: 1,
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
      enableTextSelection: true,
    });
    // Still subscribed, but the removed handler is no longer called.
    expect(lastHost().onPageTap).toBe(tap);
    tap.f({ page: 0, x: 1, y: 2 });
    expect(calls).toEqual([]);
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
      initialPage: 4,
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
