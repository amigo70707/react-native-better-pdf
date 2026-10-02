# react-native-better-pdf

A native, virtualized PDF viewer for React Native, built on
[Nitro Views](https://nitro.margelo.com/docs/guides/view-components).

- **iOS**: Apple's PDFKit (`PDFView`).
- **Android**: the platform `PdfRenderer` with a purpose-built native viewer
  (single canvas view, byte-budgeted page cache, zoom tiles, prefetch).
- **Zero JS in the hot path**: scrolling, zooming, rendering, caching and
  prefetching never touch the JS thread. JS sends configuration and commands
  and receives three events.
- **No extra native engines**: no bundled PDFium, no WebView, no
  `react-native-blob-util`. Remote files are downloaded natively.

> Status: **0.1 — MVP**. Viewing only. See [Limitations](#limitations).

## Why

Existing React Native PDF viewers download and hash files in JS, ship their
own PDFium build (~5 MB per Android ABI) and push per-gesture events such as
`onScaleChanged` through JS. This library uses the PDF engines that already
ship with the OS, keeps every per-frame decision native, and exposes a small,
typed API. How it compares in practice is measured, not assumed — see
[docs/benchmarks.md](docs/benchmarks.md).

## Requirements

| | Minimum | Notes |
| --- | --- | --- |
| React Native | 0.81 | New Architecture (Fabric) required — Nitro Views do not support the legacy renderer |
| react-native-nitro-modules | 0.37 | peer dependency |
| iOS | 15.1 | React Native's minimum; PDFKit |
| Android | API 24 | React Native's minimum; passwords, links, text selection and annotations need Android 15+, or Android 12–14 with SDK extension 13 (the updated system PDF module) |
| Expo | SDK 54+ development builds | **Expo Go is not supported** (it cannot load third-party native code) |

Developed and tested with React Native 0.86.3 / Expo SDK 57, Xcode 27,
Kotlin 2.1, AGP 8.12 / Gradle 9.3, Node 24.

## Installation

```sh
npm install react-native-better-pdf react-native-nitro-modules
# or: bun add / yarn add
```

Bare React Native: `cd ios && pod install`.

Expo: no config plugin is needed. Rebuild your development build:

```sh
npx expo prebuild
npx expo run:ios   # or run:android
```

To bundle PDFs with `require('./file.pdf')`, add `pdf` to Metro's asset
extensions:

```js
// metro.config.js
config.resolver.assetExts.push('pdf');
```

## Usage

```tsx
import { useRef } from 'react';
import { PdfView, type PdfViewRef } from 'react-native-better-pdf';

export function Reader() {
  const pdf = useRef<PdfViewRef>(null);

  return (
    <PdfView
      ref={pdf}
      style={{ flex: 1 }}
      source={{ uri: 'https://example.com/report.pdf' }}
      initialPage={0}
      minScale={1}
      maxScale={4}
      pageSpacing={8}
      onLoad={({ pageCount }) => console.log('pages', pageCount)}
      onPageChange={({ page, pageCount }) => console.log(page + 1, '/', pageCount)}
      onError={(error) => console.warn(error.code, error.message)}
    />
  );
}

// Links and taps:
<PdfView
  source={source}
  onLinkPress={({ url }) => {
    if (url.startsWith('https://')) Linking.openURL(url);
  }}
  onPageTap={({ page, x, y }) => toggleToolbar()}
/>;

// Later:
pdf.current?.goToPage(41);                     // zero-based, animated
pdf.current?.goToPage(0, { animated: false });
pdf.current?.zoomTo(2);                        // relative to the fitted page
pdf.current?.zoomTo(1);                        // reset
const toc = await pdf.current?.getOutline();   // [{ title, page?, children }]
const sizes = await pdf.current?.getPageSizes(); // [{ width, height }] per page, PDF points
const image = await pdf.current?.renderPage(0, { width: 600 }); // { uri, width, height }, a PNG file
const printed = await pdf.current?.print();    // { isSubmitted }, the system print dialog
pdf.current?.search('invoice');                // highlights matches, see onSearchChange
pdf.current?.searchNext();                     // wraps around; searchPrevious() too
pdf.current?.clearSearch();                    // or search('')
pdf.current?.goToViewport(saved);              // a position from onViewportChange
```

### Sources

```ts
type PdfSource =
  | PdfUriSource        // { uri, headers? } — https://, http://, file://, content:// (Android)
  | { filePath: string } // absolute path
  | number;              // require('./file.pdf')
```

Remote files are downloaded natively and reused for the rest of the app
session — or kept for offline reading; see
[Offline reading and caching](#offline-reading-and-caching). An inline
`source={{ uri }}` object is fine: the source is compared by value, so
re-renders never reload the document.

### Props

| Prop | Type | Default | |
| --- | --- | --- | --- |
| `source` | `PdfSource` | — | required |
| `password` | `string` | — | changing it re-opens the document (no re-download) |
| `initialPage` | `number` | `0` | zero-based, applied once per loaded document |
| `initialScale` | `number` | `1` | zoom applied once per loaded document, relative to the fitted page |
| `initialViewport` | `PdfViewport` | | position applied once per loaded document, e.g. where the reader left off; takes precedence over `initialPage` / `initialScale`; see [Reading position](#reading-position) |
| `minScale` / `maxScale` | `number` | `1` / `4` | relative to the fitted page (see `fitPolicy`) |
| `pageSpacing` | `number` | `8` | dp / pt |
| `enableDoubleTapZoom` | `boolean` | `true` | double tap toggles zoom; `false` also delivers `onPageTap` without the ~300 ms double-tap wait |
| `scrollEnabled` | `boolean` | `true` | `false`: drags go to parent views (a surrounding `ScrollView` scrolls); zoom, taps and `goToPage` still work |
| `pageAccessibilityLabel` | `string` | `'Page {page} of {pageCount}'` | how screen readers name a page; localise it |
| `showsVerticalScrollIndicator` | `boolean` | `true` | platform scroll bar while scrolling |
| `showsHorizontalScrollIndicator` | `boolean` | `true` | shown only while zoomed wider than the view |
| `enableTextSelection` | `boolean` | `true` | long press selects text, handles adjust it, the system menu copies; `false` turns selection off (Android: API 35+) |
| `horizontal` | `boolean` | `false` | pages side by side |
| `enablePaging` | `boolean` | `false` | one page per swipe, along the `horizontal` axis |
| `enableRTL` | `boolean` | `false` | horizontal layouts run right to left (iOS: with `enablePaging` only) |
| `singlePage` | `boolean` | `false` | one static page (`initialPage`, switched by `goToPage`) for thumbnails; ignores touches |
| `fitPolicy` | `'width' \| 'height' \| 'page'` | `'width'`, or `'page'` with `horizontal` / `enablePaging` | how pages are scaled at zoom 1 |
| `spreadMode` | `'none' \| 'pairs' \| 'book'` | `'none'` | facing pages side by side in the vertical continuous layout; see [Facing pages](#facing-pages) |
| `colorScheme` | `'light' \| 'dark'` (or `useColorScheme()`'s value) | `'light'` | `'dark'` shows pages with lightness inverted and hues kept; see [Dark pages](#dark-pages) |
| `searchHighlightColor` | `ColorValue` | `'rgba(255, 214, 0, 0.4)'` | background of search matches; use a translucent colour |
| `searchCurrentHighlightColor` | `ColorValue` | `'rgba(255, 140, 0, 0.6)'` | background of the current match |
| `onLoad` | `({ pageCount, firstPageWidth, firstPageHeight, filePath?, isStale }) => void` | | first page's size in PDF points, as displayed (crop box, rotation applied); `filePath`: the local file being displayed (downloaded copy, local file or extracted asset; not for `content://`); `isStale`: an outdated stored copy shown because the server could not be reached (see [Offline reading and caching](#offline-reading-and-caching)) |
| `onLoadProgress` | `({ loadedBytes, totalBytes? }) => void` | | remote downloads only (not when a stored copy is used); at most every 100 ms plus once on completion |
| `onPageChange` | `({ page, pageCount }) => void` | | zero-based |
| `onError` | `(error: PdfError) => void` | | |
| `onLinkPress` | `({ url }) => void` | | tap on a URL link; the viewer never opens URLs itself |
| `onPageTap` | `({ page, x, y }) => void` | | tap on a page outside links; zero-based page, `x`/`y` in PDF points from the page's top-left |
| `onScaleChange` | `({ scale }) => void` | | once per settled zoom change (pinch/double-tap end, `zoomTo`); relative to the fitted page |
| `onTextSelectionChange` | `({ text }) => void` | | once per settled selection (long press, handle drag or Select all ends); `text` is `''` when cleared |
| `onViewportChange` | `({ viewport, firstVisiblePage, lastVisiblePage, isAtEnd }) => void` | | once after loading and after every settled scroll, zoom or rotation, never per frame; see [Reading position](#reading-position) |
| `onSearchChange` | `({ query, matchCount, currentMatch?, currentPage?, isComplete, isSupported }) => void` | | search progress and the current match; see [Search](#search) |

Plus all `View` props (`style`, `testID`, …). They apply to the viewer's
box, which clips the document: `borderRadius` rounds it on both platforms
(a shadow needs a surrounding view on iOS).

A document shorter than the view starts at the top in vertical continuous
layouts; horizontal layouts centre pages across, and paged layouts centre
each page. The viewer adds no safe-area insets on either platform, like
`ScrollView`: keep it below the status bar (e.g. in a `SafeAreaView`).

### Errors

`error.code` is stable and identical on both platforms:

`invalid-source` · `file-not-found` · `network-error` · `http-error`
(`error.httpStatus`) · `invalid-pdf` · `password-required` ·
`invalid-password` · `unsupported-pdf` · `page-render-failed` (`error.page`) ·
`out-of-memory`

`error.message` is human-readable and may change; `error.nativeDescription`
carries the platform error for logging.

### Loading state

There is no built-in spinner. Show your own until `onLoad` or `onError`;
for remote documents `onLoadProgress` reports the download:

```tsx
onLoadProgress={({ loadedBytes, totalBytes }) =>
  setProgress(totalBytes === undefined ? undefined : loadedBytes / totalBytes)
}
```

`totalBytes` is `undefined` when the server does not report a size.

## Features

- Local, bundled, `content://` and remote (with headers) documents
- Offline reading: downloads kept for the session or across launches,
  revalidated with `ETag` / `Last-Modified`, shown when the server cannot be
  reached; `PdfCache` downloads ahead of time, reports and deletes copies
- Vertical continuous scrolling, fling, platform scroll indicators
- Card layouts: `borderRadius` clips the document; a short document starts
  at the top on both platforms
- Facing pages (two-page spreads) for tablets, with or without a cover page
- Dark pages for reading at night, colours' hues kept
- Pinch zoom, double-tap zoom, pan while zoomed, min/max scale
- Initial page, imperative page navigation and zoom
- Every page's size (`getPageSizes`), cheap even for long documents
- Page images (`renderPage`): PNG or JPEG files for thumbnails, previews and
  covers, rendered off the main thread
- Printing (`print`) through the system dialog, password-protected
  documents included
- Reading position: settled viewport events, restore on open or on demand,
  end-of-document detection
- Page-change, load and typed error events
- Link presses (URL links reported to JS; links to other pages followed
  natively) and page taps in PDF coordinates
- Text search with highlighted matches and next/previous navigation
  (Android 15+, or Android 12–14 with SDK extension 13 on Android)
- Text selection with the platform's handles and Copy / Select all menu
  (Android 15+, or Android 12–14 with SDK extension 13 on Android)
- Password-protected documents (Android 15+, or Android 12–14 with SDK extension 13 on Android)
- Native page virtualization, render cache, prefetch and cancellation
  (Android; PDFKit handles these on iOS)
- Memory-pressure handling and release of all pixels while a screen is hidden

## Reading position

Reopen a document where the reader left off, and know what they have read:

```tsx
const saved = storage.get(documentId); // a PdfViewport, or undefined

<PdfView
  source={source}
  initialViewport={saved}
  onViewportChange={({ viewport, isAtEnd }) => {
    storage.set(documentId, viewport);
    if (isAtEnd) markAsRead(documentId);
  }}
/>;
```

- A `PdfViewport` is `{ page, x, y, scale }`: the point of `page` (in PDF
  points from its top-left corner) at the view's top-left corner, and the
  zoom. PDF points do not depend on the screen, so a position survives
  rotation, another device or another layout mode. `x` / `y` are negative
  when the corner is in the margin or gap before the page.
- `onViewportChange` fires once after loading and then whenever the view
  stops moving (scroll, fling, zoom, page turn, rotation, `goToPage`…),
  only when the position changed. `firstVisiblePage` / `lastVisiblePage`
  give the pages on screen; `isAtEnd` is true while the end of the last
  page is on screen.
- `ref.goToViewport(viewport)` jumps back to a position without animation,
  e.g. for "back to where I was" after following a table-of-contents entry.
- Paged layouts (`enablePaging`, `singlePage`) restore the page and zoom;
  on iOS the position within a zoomed page is not restored.

## Offline reading and caching

Remote documents are downloaded natively and stored. The source says for
how long, and how often the server is asked for a newer version:

```tsx
const source: PdfUriSource = {
  uri: `https://example.com/documents/${id}.pdf`,
  headers: { Authorization: `Bearer ${token}` },
  cache: 'persistent',        // keep across launches (default 'session')
  cacheKey: `document-${id}`, // the token changes: identify the copy by id
  maxAgeMs: 0,                // ask the server on every open (default: never)
};

<PdfView source={source} onLoad={({ isStale }) => setOfflineBanner(isStale)} />;
```

- **`cache`**: `'session'` keeps the download until the app exits; opening
  the document again does not download it. `'persistent'` keeps it across
  launches where the OS does not purge it and backups do not include it,
  until you remove it.
- **`maxAgeMs`**: once a copy is older, opening the document asks the
  server whether it changed (`If-None-Match` / `If-Modified-Since`). An
  unchanged document is not downloaded again; a changed one replaces the
  copy.
- **Offline**: when the server cannot be reached (no connection, no answer
  within 10 seconds, or a 5xx response), the view shows the stored copy and
  `onLoad` reports `isStale: true`. Without a copy it fails with
  `network-error`. A 4xx response is an error even with a copy.
- **`cacheKey`** identifies the copy instead of `uri` and `headers`. Set it
  when they change between opens — signed URLs, rotating tokens — or the
  copy is not found again. Sources with the same `cacheKey` are the same
  document: a new URL or token does not reload the view.
- A response that is not a PDF (a login page, a captive portal) fails with
  `invalid-pdf` and is never stored.

`PdfCache` manages the copies without a view, with the same source object:

```ts
import { PdfCache, PdfDownloadError } from 'react-native-better-pdf';

// "Download for offline reading", with progress and cancellation.
const controller = new AbortController();
try {
  const entry = await PdfCache.download(source, {
    onProgress: ({ loadedBytes, totalBytes }) => setProgress({ loadedBytes, totalBytes }),
    signal: controller.signal,
  }); // { filePath, byteSize, cache, validatedAtMs }
} catch (error) {
  if (error instanceof PdfDownloadError && error.httpStatus === 401) await refreshToken();
}

await PdfCache.getEntry(source); // the stored copy, or undefined; no network
await PdfCache.remove(source);   // true when there was a copy
await PdfCache.getByteSize();    // all stored copies, in bytes
await PdfCache.clear();          // e.g. on sign-out
```

- `download` resolves at once when the stored copy is fresh enough, and
  joins a download of the same copy already in progress (e.g. by a
  `PdfView`): the file is downloaded once. Unlike the view, it never falls
  back to an old copy — it rejects (`PdfDownloadError` with a `code`, or an
  `AbortError` when cancelled) and the old copy stays.
- Cancelling stops the download unless a `PdfView` is waiting for it too.
- `remove` and `clear` do not disturb documents on screen and do not stop
  downloads in progress (those still store their result). Page images from
  `renderPage` are not affected.
- Session copies from the previous launch are deleted on first use.
- There is no list of stored copies (it would mean keeping URLs and
  headers on disk) and no size limit: keep track of what you download and
  remove what is no longer needed. Downloads run while the app runs; there
  are no background transfers.

## Page images

```tsx
const image = await pdf.current?.renderPage(page, {
  width: 160 * PixelRatio.get(), // pixels; the height follows the page
  format: 'jpeg',                // default 'png'
  quality: 0.8,                  // JPEG only, default 0.9
});
<Image source={{ uri: image.uri }} style={{ width: 160, aspectRatio: image.width / image.height }} />;
```

- The page as displayed (crop box, rotation), on white, with the
  annotations the viewer draws. `width` is 1–4096 pixels, and an image is
  at most 4096 × 4096 pixels' worth (a very tall page needs a smaller
  width).
- Rendered off the main thread on a separate renderer, so the view keeps
  scrolling and its caches are untouched.
- The file is written to the app's cache directory (`BetterPdf/renders`)
  and is yours: move it to keep it, delete it when done. The OS may clear
  cache files when storage is low.
- For thumbnails that stay on screen, a `singlePage` `PdfView` (see the
  example's thumbnail strip) needs no files; `renderPage` is for images
  you pass elsewhere — `<Image>` lists, sharing, uploads.

## Facing pages

```tsx
const { width, height } = useWindowDimensions();
<PdfView source={source} spreadMode={width > height ? 'book' : 'none'} />;
```

- `'pairs'` shows pages 1–2, 3–4, … side by side; `'book'` shows the first
  page alone, like a cover, then 2–3, 4–5, …, like a magazine.
- Each page fits half the view's width; a page without a partner (the
  cover, an odd last page) is centred. Rows scroll vertically.
- `onPageChange` reports the first page of the spread under the viewport
  centre (the last page at the end). Switching `spreadMode` or the layout
  keeps the page the reader was on.
- Vertical continuous layouts only: ignored with `horizontal`,
  `enablePaging` and `singlePage` (iOS's PDFKit lays out facing pages only
  there). Facing pages run left to right.

## Dark pages

```tsx
const scheme = useColorScheme();
<PdfView
  source={source}
  colorScheme={scheme} // 'dark' turns pages dark; anything else keeps them
  style={{ flex: 1, backgroundColor: scheme === 'dark' ? '#27272A' : '#E4E4E7' }}
/>;
```

- Lightness inverted, hues kept: white paper turns black, black text
  white, and a red heading stays red (lighter). Photos are inverted too.
- The area around pages is the view's `backgroundColor`; set a dark one.
- Search and selection highlights keep their colours. Page images
  (`renderPage`) and printing keep the document's colours.
- Annotations: Android darkens whatever its renderer draws. On iOS,
  PDFKit draws annotations apart from the page, so they keep their own
  colours; highlights, underlines and strike-outs are lightened onto the
  dark page instead of disappearing.

## Printing

```tsx
const result = await pdf.current?.print({ jobName: 'Invoice 7' });
if (result?.isSubmitted) showToast('Sent to the printer');
```

- Opens the system print dialog for the document as loaded and resolves
  when it closes: `isSubmitted` is `true` when the user printed or saved
  as PDF, `false` when they closed the dialog.
- Prints password-protected documents too: the print system opens files
  without a password, so it gets an unlocked copy that never touches disk
  (in memory on iOS; written straight to the print spooler on Android).
  Works for `content://` sources, which have no file path.
- `jobName` names the job in the print queue and the file when saving as
  PDF; by default the source's file name, or `'Document'`.
- Rejects before `onLoad`, while the view's print dialog is open, when the
  app is not in the foreground, and on iOS when the device cannot print or
  the document's permissions forbid printing (Android has no API to read
  them and prints anyway). On iPad the dialog opens as a popover from the
  view.
- Printing a file the app already has needs no viewer: `onLoad`'s
  `filePath` works with `expo-print` or `react-native-print`. This method
  adds what they cannot: protected documents, `content://` sources, and the
  dialog anchored to the viewer on iPad.

## Search

```tsx
const [search, setSearch] = useState<PdfSearchChangeEvent>();

<PdfView ref={pdf} source={source} onSearchChange={setSearch} />;

pdf.current?.search(query);   // new query: restarts; '' clears
pdf.current?.searchNext();    // or searchPrevious(); both wrap around

// "3 / 17", or "3 / 17…" while the rest of the document is searched
const label = search?.isSupported === false
  ? 'Search unavailable'
  : `${(search?.currentMatch ?? -1) + 1} / ${search?.matchCount ?? 0}${search?.isComplete ? '' : '…'}`;
```

- Search is native and runs in the background: matches are highlighted as
  pages are searched, starting at the current page, and the first match at
  or after it becomes current and is scrolled into view. Case-insensitive.
- `onSearchChange` fires at most 10 times per second while pages are
  searched, and immediately for the first match, `searchNext`/`searchPrevious`,
  completion and clearing. `currentMatch` counts matches in document order.
- Matches are highlighted in `searchHighlightColor` (translucent yellow)
  and the current one in `searchCurrentHighlightColor` (orange). Any React
  Native colour works except `PlatformColor` / `DynamicColorIOS`, which
  fall back to the defaults.
- Loading another document ends the search (with an event whose `query` is
  `''`).
- Android searches with the platform renderer's text API (Android 15+, or
  12–14 with SDK extension 13); elsewhere the search ends at once with
  `isSupported: false`.

## Accessibility

Screen readers get the document's text and position:

- **Android (TalkBack):** each page on screen is an accessibility node that
  reads "Page 3 of 12" followed by the page's text (Android 15+, or 12–14
  with SDK extension 13; older versions read the label only). The document
  is a scrollable list, so TalkBack's scroll gestures move through it (one
  page at a time with `enablePaging`).
- **iOS (VoiceOver):** PDFKit exposes the page text; the viewer adds a "Page
  3 of 12" announcement when the page changes.
- Localise the label with `pageAccessibilityLabel`, e.g.
  `"Strona {page} z {pageCount}"`.
- Images inside pages have no descriptions on either platform (PDF alt text
  is not read by the platform renderers).

## Limitations

- **Not implemented yet**: editing annotations or forms; facing pages in
  horizontal or paged layouts, or right to left.
- **Dark pages on iOS**: annotations keep their own colours (see
  [Dark pages](#dark-pages)).
- **Text selection**: iOS uses PDFKit's selection, which can span pages.
  Android draws its own: a selection stays on one page, Select all selects
  that page, copying ends the selection (as in a text field), dragging a
  handle to the edge does not scroll, and a handle dragged past the other
  end stops at the first character.
- **Outline (`getOutline`)**: on Android, encrypted documents reject (their
  titles are encrypted). iOS (PDFKit) leaves PDF 1.1 named destinations
  (`/Dest /name`) without a page.
- **Android**: several features come from the platform renderer's newer
  API — on Android 15+, and on Android 12–14 when the device has SDK
  extension 13 (the PDF module updated through Google Play system updates;
  the library then uses `PdfRendererPreV`). "Android 15+" below includes
  those devices.
  - Annotations: the platform renderer draws highlights, sticky notes and
    free text from Android 15+, stamps from Android 17, form fields always —
    and never squares, circles, lines, polygons, ink, underline or
    strike-out (measured; see docs/api-design.md "Annotations"). iOS draws
    all of them.
  - Password-protected PDFs require Android 15+; other devices report
    `unsupported-pdf`.
  - Text selection requires Android 15+; other devices ignore long presses.
  - Search requires Android 15+; other devices report `isSupported: false`.
  - Links require Android 15+. Of the internal-link forms, only
    GoTo actions with an XYZ destination are reported by `PdfRenderer`
    (`/Dest` arrays and `/Fit` destinations are not); undetected links behave
    like the rest of the page and are reported through `onPageTap`.
  - `PdfRenderer` renders one page at a time per *process*, so several
    visible viewers share one render pipeline.
  - Page sizes are discovered as pages are first rendered (reading them up
    front costs memory equal to the file size in `PdfRenderer`). Documents
    with mixed page sizes may re-anchor slightly the first time a page far
    from the viewport is visited.
  - Documents are parsed in-process; the platform recommends an isolated
    process for untrusted input, which this library does not provide.
- **iOS**: PDFKit does not expose cache statistics; `getStats()` reports
  `-1` for them.
- `data:`/base64 sources are intentionally unsupported (they force the file
  through the JS heap).
- Downloads use `GET` only, run only while the app runs, and are not
  limited in total size (see
  [Offline reading and caching](#offline-reading-and-caching)).

## Performance philosophy

1. The main and JS threads never wait on document I/O or rendering.
2. No per-frame JS: no `onScroll`/`onZoom` events, no React-managed pages.
3. Memory is bounded by the viewport, not the document or the zoom level.
4. Claims come from measurements. [docs/benchmarks.md](docs/benchmarks.md)
   describes the method and publishes the numbers — including where this
   library is not faster.

## Documentation

- [docs/migrating-from-react-native-pdf.md](docs/migrating-from-react-native-pdf.md) — prop-by-prop migration guide
- [ARCHITECTURE.md](ARCHITECTURE.md) — threading, virtualization, cache, lifecycle
- [docs/api-design.md](docs/api-design.md) — API decisions vs. react-native-pdf
- [docs/architecture/research.md](docs/architecture/research.md) — platform research
- [docs/benchmarks.md](docs/benchmarks.md) — methodology and results
- [docs/audit.md](docs/audit.md) — engineering audit and open issues
- [docs/community-requests.md](docs/community-requests.md) — what react-native-pdf users ask for, and where this library stands
- [CONTRIBUTING.md](CONTRIBUTING.md)

## License

MIT
