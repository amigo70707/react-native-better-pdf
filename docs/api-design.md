# API design

This records the reasoning behind the public API before it is stabilized.
Input: the react-native-pdf 7.0.5 surface (see
`docs/architecture/research.md`), the Nitro View constraints, and the
performance rules in `ARCHITECTURE.md`.

## Principles

1. **Configuration in, meaningful events out.** Props describe the document
   and interaction limits. Callbacks fire on state changes a UI would act on
   (loaded, failed, page changed) — never per frame.
2. **Zero-based pages everywhere.** Matches JS arrays and both native APIs.
   react-native-pdf's 1-based pages are a steady source of off-by-one bugs.
3. **Scale is relative to the fitted page.** `1` means the page fits the
   view as `fitPolicy` says (fit width by default), regardless of device or
   page size.
4. **Stable error codes, unstable messages.** Match on `error.code`.
5. **Omit what we can't do well natively on both platforms** rather than ship
   platform-specific booleans.

## Public API (v0.1)

```tsx
import { PdfView, type PdfViewRef } from 'react-native-better-pdf';

const ref = useRef<PdfViewRef>(null);

<PdfView
  ref={ref}
  style={{ flex: 1 }}
  source={{ uri: 'https://example.com/file.pdf', headers: { Authorization } }}
  password={password}
  initialPage={0}
  minScale={1}
  maxScale={4}
  pageSpacing={8}
  onLoad={({ pageCount }) => {}}
  onPageChange={({ page, pageCount }) => {}}
  onError={(error) => {
    if (error.code === 'password-required') askForPassword();
  }}
/>;

ref.current?.goToPage(42);            // animated by default
ref.current?.goToPage(42, { animated: false });
ref.current?.zoomTo(2);
ref.current?.zoomTo(1);               // "reset zoom"
```

### Source

```ts
type PdfSource =
  | { uri: string; headers?: Record<string, string> } // http(s)://, file://, content://
  | { filePath: string }                              // absolute path
  | number;                                           // require('./doc.pdf')
```

- Normalized in JS into one native shape `{ uri, headers? }` so native code
  handles a single representation.
- Remote files are downloaded **natively** (URLSession / HttpURLConnection)
  into the app cache, keyed by a hash of the URL. No blob-util, no JS base64.
- The normalized source is memoized **by value** so re-rendering the parent
  with an inline object literal does not reload the document (Nitro diffs
  props by identity — see research notes).
- Base64 / `data:` URIs are intentionally not supported: they force the whole
  file through the JS heap, which is precisely what this library avoids.

### Props

| Prop           | Type                  | Default | Notes                                                                 |
| -------------- | --------------------- | ------- | --------------------------------------------------------------------- |
| `source`       | `PdfSource`           | —       | Required.                                                             |
| `password`     | `string`              | —       | Changing it reloads the document (downloaded file is reused).         |
| `initialPage`  | `number`              | `0`     | Applied once per loaded document. Changing it later has no effect.    |
| `initialScale` | `number`              | `1`     | Like `initialPage`, for zoom (relative to the fitted page). Not an `onScaleChange`. |
| `minScale`     | `number`              | `1`     | Relative to the fitted page (`fitPolicy`).                             |
| `maxScale`     | `number`              | `4`     | Relative to the fitted page (`fitPolicy`).                             |
| `enableRTL`    | `boolean`             | `false` | Right-to-left horizontal layouts; see "Right to left".                |
| `singlePage`   | `boolean`             | `false` | One static page; see "Single page".                                   |
| `fitPolicy`    | `'width' \| 'height' \| 'page'` | see "Fit policy" | How a page is scaled at zoom 1.                    |
| `pageSpacing`  | `number`              | `8`     | dp / pt between pages.                                                |
| `enableDoubleTapZoom` | `boolean` | `true` | Double tap toggles zoom (Android 2×, iOS PDFKit's step); `false` also makes `onPageTap` immediate. |
| `scrollEnabled` | `boolean` | `true` | Drag scrolling; see "Scroll enabled". |
| `pageAccessibilityLabel` | `string` | `'Page {page} of {pageCount}'` | Screen-reader page label; see "Accessibility". |
| `showsVerticalScrollIndicator` | `boolean` | `true` | Same name and meaning as React Native's `ScrollView`.            |
| `showsHorizontalScrollIndicator` | `boolean` | `true` | Only visible while a page is zoomed wider than the view.        |
| `enableTextSelection` | `boolean` | `true` | Long press selects text; `false` turns selection off; see "Text selection". |
| `horizontal`   | `boolean`             | `false` | Pages side by side; see "Layout modes".                               |
| `enablePaging` | `boolean`             | `false` | One page per swipe; see "Layout modes".                               |
| `onLoad`       | `(e: PdfLoadEvent)`   | —       | `{ pageCount, firstPageWidth, firstPageHeight, filePath? }` (PDF points). |
| `onLoadProgress` | `(e: PdfLoadProgressEvent)` | — | `{ loadedBytes, totalBytes? }`; remote only; see "Load progress".      |
| `onPageChange` | `(e: PdfPageChangeEvent)` | —   | `{ page, pageCount }`. The page under the viewport centre (the last page once scrolled to the end). |
| `onError`      | `(e: PdfError)`       | —       | Load and render errors.                                               |
| `onLinkPress`  | `(e: PdfLinkPressEvent)` | —    | `{ url }`. URL links only; see "Links and taps".                       |
| `onPageTap`    | `(e: PdfPageTapEvent)` | —      | `{ page, x, y }` in PDF points from the page's top-left.               |
| `onScaleChange` | `(e: PdfScaleChangeEvent)` | — | `{ scale }` once the zoom settles; see "Scale change".               |
| `onTextSelectionChange` | `(e: PdfTextSelectionChangeEvent)` | — | `{ text }` once the selection settles, `''` when cleared; see "Text selection". |
| `style` and other `ViewProps` | | | Standard React Native view props.                              |

`initialPage` is deliberately *initial*: a controlled `page` prop (as in
react-native-pdf) fights with native scrolling — every page change must round
trip through JS state and back, and a stale render snaps the user back.
Navigation after load is imperative (`goToPage`).

### Imperative API

| Method                                    | Notes                                                       |
| ----------------------------------------- | ----------------------------------------------------------- |
| `goToPage(page, { animated = true })`     | Zero-based; clamped to `[0, pageCount - 1]`.                |
| `zoomTo(scale, { animated = true })`      | Clamped to `[minScale, maxScale]`. `zoomTo(1)` resets zoom. |
| `getStats()`                              | Diagnostics (cache bytes, hits, renders). Synchronous, cheap. |
| `getOutline()`                            | `Promise<PdfOutlineItem[]>`; see "Outline". |
| `search(query)` / `searchNext()` / `searchPrevious()` / `clearSearch()` | See "Search". |

`resetZoom()` was dropped: it is `zoomTo(1)` (or `zoomTo(minScale)`), and
one fewer method keeps the surface small.

### Loading state

There is no built-in spinner (react-native-pdf's `renderActivityIndicator`).
The component does not re-render itself while loading; apps show their own
indicator until `onLoad`/`onError`. A `usePdfLoadState()`-style hook can be
added later without native changes.

### Errors

```ts
type PdfErrorCode =
  | 'invalid-source'      // unsupported scheme / empty uri
  | 'file-not-found'
  | 'network-error'       // transport failure
  | 'http-error'          // non-2xx, see error.httpStatus
  | 'invalid-pdf'         // not a PDF / corrupt
  | 'password-required'
  | 'invalid-password'
  | 'unsupported-pdf'     // e.g. encrypted PDF where Android cannot decrypt it
  | 'page-render-failed'  // see error.page
  | 'out-of-memory';
```

`error.nativeDescription` carries the raw platform message for logs.

## What we omit from react-native-pdf, and why

| react-native-pdf                         | Decision                | Reason                                                                     |
| ---------------------------------------- | ----------------------- | -------------------------------------------------------------------------- |
| `page` (controlled)                      | → `initialPage` + `goToPage` | Controlled page fights native scrolling.                             |
| `scale`                                  | → `zoomTo`              | Same reason as `page`.                                                     |
| `onScaleChanged`                         | → `onScaleChange`       | Once per settled zoom change, never per frame; see "Scale change".        |
| `onLoadProgress`                         | → `onLoadProgress`      | `{ loadedBytes, totalBytes? }` instead of a percentage; throttled natively (see "Load progress"). |
| `fitPolicy: 0 \| 1 \| 2`                 | → `'width' \| 'height' \| 'page'` | Named values instead of numbers; see "Fit policy".             |
| `showsVerticalScrollIndicator`, `showsHorizontalScrollIndicator` | Kept | Same names as React Native's `ScrollView`. Android draws the platform's own fading scroll bars; iOS toggles PDFKit's. |
| `horizontal`, `enablePaging`             | Kept                    | Same names; see "Layout modes".                                           |
| `singlePage`                             | Kept                    | Same name; see "Single page".                                             |
| `enableRTL`                              | Kept                    | Same name; see "Right to left".                                           |
| `enableAntialiasing`                     | Omitted                 | Always on.                                                                 |
| `enableAnnotationRendering`              | Omitted                 | Rendered wherever the platform can; see "Annotations".                   |
| `enableTextSelection`                    | Kept                    | Same name and default; `false` really turns selection off (react-native-pdf only muted the event). See "Text selection". |
| `onTextSelectionChange`                  | → `onTextSelectionChange` | `{ text }` once per settled selection instead of `{ nativeEvent: { type, text } }` per handle move. |
| `onPageSingleTap`                        | → `onPageTap`           | Zero-based page and PDF-point coordinates (react-native-pdf: 1-based page, view coordinates that differ per platform and ignore zoom). |
| `onPressLink`                            | → `onLinkPress`         | Same meaning; internal links are followed natively, URLs never opened by the viewer. |
| `trustAllCerts`                          | Omitted                 | Security foot-gun.                                                         |
| `source.cache`, `cacheFileName`, `expiration`, `method`, `body` | Omitted | Native cache is keyed by URL; `GET` only for now. (`body`, a request body, is read by react-native-pdf's JS but missing from its types.) |
| `renderActivityIndicator`, `progressContainerStyle` | Omitted      | App-owned UI; see "Loading state".                                        |
| base64 `data:` sources                   | Omitted                 | Forces the file through the JS heap.                                       |

## Nitro spec mapping

The public component is a thin wrapper around the generated host component:

- `PdfView` (public) → `BetterPdfView` (native, name chosen to avoid
  colliding with other libraries' `PdfView` component registrations).
- `ref` → a small object implemented over `hybridRef` that converts
  `{ animated }` options into positional booleans and hides `getStats`
  plumbing.
- Callbacks are routed through refs so their identity never changes after
  mount; changing `onPageChange` between renders costs no native prop update.

## Links and taps (v0.2)

```ts
onLinkPress?: (event: { url: string }) => void;
onPageTap?: (event: { page: number; x: number; y: number }) => void;
```

Decisions:

- **URL links are reported, never opened.** A PDF is untrusted input; opening
  `tel:`, `sms:`, custom schemes or phishing URLs is the app's policy
  decision. Without `onLinkPress`, URL links do nothing (react-native-pdf
  behaves the same way).
- **Links to other pages are followed natively** and not reported: JS has
  nothing to decide, and a round trip would add latency to navigation.
- **A tap is either a link press or a page tap**, never both. Taps in gaps
  between pages are not reported.
- **Coordinates are PDF points from the page's top-left**, identical on both
  platforms and at every zoom level, so they can position overlays or
  annotations. Verified end-to-end: a tap at 85% of an A4 page's width
  reports x ≈ 505 on both platforms.
- **Single taps wait for the double-tap window**, so a double tap zooms
  without also producing a page tap. This adds ~300 ms of latency to
  `onPageTap`. With `enableDoubleTapZoom={false}` nothing waits: every tap
  is reported on finger up (a double tap becomes two page taps).
- **Names** follow this library's event naming (`onPageChange`,
  `onPageTap`, `onLinkPress`) rather than react-native-pdf's.

Platform limitations (measured, not assumed):

- Android reads links through `PdfRenderer.Page.getLinkContents()` /
  `getGotoLinks()`, available from Android 15 (API 35) and on Android 12–14
  with SDK extension 13 (`PdfRendererPreV`). On API 35, only
  internal links written as a GoTo action with an XYZ destination are
  reported; `/Dest` arrays and `/Fit` destinations come back empty (probe
  with four link forms). Undetected links fall back to `onPageTap`.
- iOS uses PDFKit, which handles every link form.

## Load progress (v0.2)

```ts
onLoadProgress?: (event: { loadedBytes: number; totalBytes?: number }) => void;
```

- **Bytes, not a percentage.** The server may not report a size
  (`Content-Length` missing, or cleared because the response is
  transparently decompressed); `totalBytes` is then `undefined` and a
  percentage would be a lie. Apps derive the fraction when it exists.
- **Time-based throttle: at most one event per 100 ms, plus one final event
  on completion**, delivered before `onLoad`. Percent-step throttling was
  rejected: with an unknown size there are no percents, and for a fast
  download 1% steps still mean 100 events. The time contract bounds JS work
  at ~10 events per second for any file size or network speed (unit tested:
  400 MB in 2 s → 21 events).
- **Remote downloads only.** Local files open without a download phase;
  documents already downloaded in this app session are reused without
  progress events.
- The throttle runs natively on the download thread
  (`ProgressThrottle` in Kotlin and Swift); JS never sees dropped updates.

## Layout modes (v0.2)

```ts
horizontal?: boolean;   // default false
enablePaging?: boolean; // default false
```

- **Two booleans, not a `layout` union.** They are independent (four valid
  combinations) and match react-native-pdf's and `ScrollView`'s names, which
  keeps migration a no-op.
- **Fit.** By default, vertical continuous fits the page width, as before;
  horizontal and paged layouts fit each page *whole* into the view, so one
  page is exactly one screen. `fitPolicy` overrides this.
- **Paging** snaps to exactly one page per swipe regardless of fling
  velocity (e2e: three fast swipes → three pages). While zoomed in, paging is
  suspended so the user can pan the page; zooming out resumes it.
- **Switching modes keeps the current page** and resets zoom.
- **Same axis on both platforms.** iOS pages with PDFKit's page view
  controller, which pages along `displayDirection` — vertically unless
  `horizontal` (verified in the view hierarchy and by the
  `viewer-layout-modes` e2e flow on both platforms).

## Fit policy (v0.2)

```ts
fitPolicy?: 'width' | 'height' | 'page';
```

- **Named values, same prop name.** react-native-pdf's `0 | 1 | 2` become
  `'width' | 'height' | 'page'`; keeping the name makes the migration a
  search-and-replace.
- **Default depends on the layout** (`'width'` vertical continuous, `'page'`
  with `horizontal` or `enablePaging`) and is resolved by the `PdfView`
  wrapper, so the native view always receives a concrete value.
- **Zoom is relative to the fit**: `minScale`, `maxScale` and `zoomTo(1)`.
- **Pages wider than the view** (e.g. `'height'` with landscape pages) scroll
  sideways; content starts centred. **Pages longer than the view in paged
  mode** (e.g. `'width'` with a receipt) scroll within the page on Android
  before the next swipe turns the page.
- **Platform differences, measured on the `mixed-sizes` fixture:**
  - Android fits every page individually. PDFKit applies one scale to the
    whole document: `'width'` in a vertical layout uses PDFKit's size-to-fit
    (the widest page fits); other fits are computed from the first page. On
    documents with uniform page sizes the platforms match.
  - iOS paging (PDFKit's page view controller) turns the page on any swipe,
    also inside a page longer than the view; such a page is read by zooming.

## Single page (v0.2)

```tsx
<Pressable onPress={() => open(page)}>
  <PdfView source={source} singlePage initialPage={page} style={thumb} />
</Pressable>
```

- **Same meaning as react-native-pdf:** one page, touches disabled — a
  thumbnail or preview. The page is `initialPage` (react-native-pdf used its
  `page` prop); `ref.goToPage()` switches it without animation.
- **Touches pass through** to React Native parents on both platforms, so a
  `Pressable` receives taps and a scrolling list scrolls from on top of the
  thumbnails (e2e: `viewer-single-page` taps a thumbnail and scrolls a strip
  of twelve by dragging on them). Consequently no `onPageTap`/`onLinkPress`,
  no zoom.
- **Only that page is rendered** (Android skips prefetch entirely); measured
  on Android: twelve thumbnails of the 100-page fixture added 0.2 MB PSS and
  4.8 MB native heap (one measurement, emulator). Each view opens the
  document itself, so long thumbnail lists should be virtualized
  (`FlatList`).
- Takes precedence over `horizontal` and `enablePaging`; default fit is
  `'page'`.

## Right to left (v0.2)

- **Meaning:** in a horizontal layout the first page is on the right and
  reading continues to the left (Arabic, Hebrew, manga). Vertical layouts and
  `singlePage` are unaffected.
- **Page numbers keep document order** on both platforms. react-native-pdf's
  Android implementation reversed the page array instead, which also reversed
  vertical layouts and every page number reported to JS; we don't replicate
  that.
- **Android:** the layout mirrors positions only; page indices, render
  requests, links and taps are unchanged. Paging snaps, prefetch direction
  and the "last page at the far end" rule follow scroll order
  (`nextInScrollOrder`), unit tested.
- **iOS (measured):** PDFKit's `displaysRTL` takes effect only with its page
  view controller (`enablePaging`); in a continuous horizontal layout it is
  ignored and pages stay left to right — the same as react-native-pdf on iOS.
  Mirroring the view ourselves would mirror the page content too. The e2e
  flow (`viewer-rtl`) runs the continuous part on Android only.

## Android 12–14: `PdfRendererPreV` (v0.2)

Passwords, links, text selection and annotation flags come from
`PdfRenderer` APIs added in Android 15. The same renderer ships to Android
12–14 as `PdfRendererPreV` in the PDF module that Google Play system updates
deliver (SDK extension 13).

- **Selection:** API 35+ uses `PdfRenderer`; API 31–34 with
  `SdkExtensions.getExtensionVersion(S) >= 13` uses `PdfRendererPreV` for
  every document (not only encrypted ones), so those devices get the whole
  API 35 feature set; anything else uses `PdfRenderer` without passwords.
  Both sit behind `DocumentRenderer`/`DocumentPage`, so the session, the
  renderer recycling and every feature are shared.
- **Error for unsupported devices:** `unsupported-pdf`, "Password-protected
  PDFs require Android 15, or Android 12–14 with the updated PDF module (SDK
  extension 13)".
- **Tested:** no emulator image for API 31–34 carries extension 13 (the
  newest, `android-34-ext12`, stops at 12; the plain API 34 image has 7). The
  `PdfRendererPreV` path was therefore verified by forcing it on the API 35
  emulator (extension 13): password, links, text selection, rendering,
  errors, outline, initial page/zoom and annotations flows all pass, and the
  annotation rendering matches `PdfRenderer`'s. The selection and fallback
  were verified on the API 34 emulator (extension 7): regular documents
  render through `PdfRenderer`, and an encrypted one reports the error
  above (`e2e/android-legacy/encrypted-unsupported.yaml`). A real Android
  12–14 device with extension 13 has not been tested.
- Android 7–11 (API 24–30) cannot be supported with the platform renderer.

## Initial scale and file path (v0.2)

- **`initialScale`** replaces react-native-pdf's `scale` prop the way
  `initialPage` replaces `page`: applied once when a document loads, clamped
  to [`minScale`, `maxScale`], later zoom through `zoomTo`. It is the
  baseline for `onScaleChange`, not an event. The initial page starts at its
  top and, when zoomed wider than the view, horizontally centred — on both
  platforms (e2e `viewer-initial`: at 2× a tap at the screen centre reports
  x ≈ 297 pt, the middle of an A4 page).
- **`onLoad`'s `filePath`** replaces `onLoadComplete`'s `path`: the absolute
  path of the file actually displayed — the source itself for local files,
  the app-cache copy for downloads (valid for the app session), the
  extracted copy for bundled assets. `undefined` when no file exists on disk
  (Android `content://`). No `file://` prefix, matching `{ filePath }`
  sources.
- Found while adding `initialScale`: on iOS, showing a new document went
  back to the page PDFKit considered current after navigating to
  `initialPage` (a "keep the current page" step meant for layout changes ran
  on first presentation). Invisible before, since zooming was what exposed
  the order; fixed.

## Annotations (v0.2)

Annotations are drawn from their appearance streams, always on (no
`enableAnnotationRendering` switch — nothing to gain from hiding them).
Measured with the `annotations` fixture (one annotation of each kind, with
appearance stream and Print flag):

Android 12–14 with SDK extension 13 behaves like Android 15–16 (the same
flags through `PdfRendererPreV`; verified by forcing that renderer on an
API 35 emulator).

| Kind | iOS | Android < 15 | Android 15–16 | Android 17+ |
| --- | --- | --- | --- | --- |
| Form field (widget) | ✅ | ✅ | ✅ | ✅ |
| Highlight | ✅ | ❌ | ✅ | ✅ |
| FreeText | ✅ | ❌ | ✅ | ✅ |
| Text (sticky note) | ✅ | ❌ | ✅ | ✅ |
| Stamp | ✅ | ❌ | ❌ | ✅ |
| Square, Circle, Line, Polygon, Ink, Underline, StrikeOut | ✅ | ❌ | ❌ | ❌ |

- Android's `PdfRenderer` draws no annotations but form fields unless asked
  through `RenderParams` flags (API 35: highlight, text; API 37 adds stamp,
  free text). Print mode draws none either (tested with the Print flag set).
  The flags are passed from API 35 on (`PdfRendererPage+renderForDisplay.kt`;
  API 37's as literals, as the module compiles against API 36; verified on an
  API 37 emulator). Measured cost: none beyond noise (mean page render
  41.2 ms without vs 41.9 ms with the flags, 100-page fixture, 3 runs each).
- API 37's annotation API (`getPageAnnotations`) covers the same three kinds,
  so it cannot fill the gap either. The remaining kinds would need drawing
  the appearance streams ourselves — e.g. by merging them into a rendering
  copy of the page — which is not implemented.
- The earlier claim "annotations always render" was wrong for Android; it
  was found while checking API parity with react-native-pdf (whose bundled
  PDFium draws every kind).

## Scale change (v0.2)

```ts
onScaleChange?: (event: { scale: number }) => void;
```

- **Settled, not per frame.** react-native-pdf's `onScaleChanged` fired
  during gestures, putting JS on the hot path. This event fires once the
  zoom settles — after a pinch or double tap ends or `zoomTo` finishes —
  whatever caused it, and only when the value changed (by more than 0.1%).
  Rotation and fit changes keep the relative zoom and send nothing; the
  initial fit is the baseline, not an event.
- **Same unit as `zoomTo`/`minScale`/`maxScale`**: relative to the fitted
  page.
- **Detection:** Android reports in its existing 80 ms settle step (the one
  that renders zoom tiles). iOS debounces `PDFViewScaleChanged` by 100 ms and
  waits while the scroll view is zooming, bouncing or tracking; PDFKit posts
  that notification only when an animated zoom (its double tap) *starts*, so
  scale changes seen through the scroll offset's per-frame KVO also restart
  the wait.
- **iOS paged layouts:** PDFKit's page view controller fits every page
  itself, with insets that differ from the continuous layout (measured: a
  226×3000 pt page at 0.1939 vs 0.1918 computed — enough for a spurious
  "1.01"). The scale PDFKit shows a page at when it arrives is taken as that
  page's 1.0, and the check waits out page turns (during one, `currentPage`
  and `scaleFactor` briefly disagree — measured as a spurious "3.52").
- **Tested** (`viewer-scale-change`): an animated `zoomTo(2.5)` yields exactly
  one event (a counter in the example app), scrolling while zoomed and
  repeating the same zoom yield none, double tap yields one, and paging
  through pages of different sizes yields none. The Nitro
  boundary test lists it as reviewed (name matches its per-frame guard).
- **Measured difference:** iOS's double tap zooms to PDFKit's own step (1.70
  for an A4 page on an iPhone 17 Pro simulator); Android's to 2×.

## Double-tap zoom (v0.2)

- **Same name and default as react-native-pdf.** `false` disables the
  double-tap zoom only; pinch and `zoomTo` keep working.
- **Taps become immediate when it is off**: there is no second tap to wait
  for. Android detaches GestureDetector's double-tap listener (every tap is
  handled on finger up); iOS disables PDFKit's zoom double-tap recognizer —
  the text interaction's double tap (select a word) stays — and the page-tap
  recognizer stops requiring double taps to fail (decided per gesture in
  `shouldRequireFailureOf`, so recognizers PDFKit creates later, e.g. for
  paging, are covered).
- **Tested through `onPageTap` coordinates**: a tap at 85% of an A4 page's
  width reports x ≈ 505 pt only when not zoomed (`viewer-double-tap`, with a
  zoom-on control). Android runs it through adb (`double-tap/run-android.sh`):
  Maestro's double tap leaves ~17 ms between taps, below GestureDetector's
  40 ms minimum, so it never registers — and a Maestro-only "off" test would
  pass even if the setting did nothing.

## Scroll enabled (v0.2)

```tsx
<ScrollView>
  <PdfView source={terms} scrollEnabled={false} style={{ height: 420 }} />
</ScrollView>
```

- **Same name and default as React Native's `ScrollView`** and
  react-native-pdf (whose iOS side set `UIScrollView.scrollEnabled`, and
  Android side `enableSwipe(false)`).
- **Only dragging is disabled** — flings and paging swipes included. Pinch
  and double-tap zoom, taps, links and `goToPage` / `zoomTo` keep working.
- **The drag belongs to the parents**: the typical use is a document inside
  a scrolling form. Android no longer blocks parent interception on touch
  down (only once a pinch starts); iOS disables panning on every PDFKit
  scroll view (including the page view controller's) and re-applies it when
  the layout mode replaces them. The e2e flow `viewer-scroll-enabled`
  checks both directions: with `false` a drag on the document scrolls the
  form, with `true` the same drag scrolls the document and not the form.

## Text selection (v0.2)

```ts
enableTextSelection?: boolean; // default true
onTextSelectionChange?: (event: { text: string }) => void;
```

- **Same names and default as react-native-pdf**, whose iOS viewer always
  let PDFKit select text: its `enableTextSelection` only gated the event
  (and Android had no selection). Here `false` really turns selection off,
  and both platforms select.
- **Platform UI, as in a read-only text field.** A long press selects the
  word under the finger, handles adjust the selection, and the system menu
  offers Copy and Select all. iOS is PDFKit's own selection (a selection can
  span pages; Select all selects the document). Android draws its own on the
  platform renderer's text APIs, with the theme's highlight colour and
  handles and a floating toolbar; a selection stays on one page and Select
  all selects that page. Copying ends the selection on Android, as in a
  `TextView`; PDFKit keeps it (checked on iOS 26.5).
- **Settled, not per frame.** PDFKit posts a selection change for every
  handle movement, and react-native-pdf forwarded each one. This event fires
  once the selection stops changing and no finger is down — after a long
  press, a handle drag, Select all or a clear — and only when the text
  differs from the last event. `text` is `''` when the selection is cleared
  (a tap elsewhere, Copy on Android, loading another document).
- **Taps**: a tap while text is selected clears the selection and is still
  reported through `onPageTap` (or `onLinkPress`), on both platforms
  (verified end-to-end). On iOS a double tap on text also selects a word
  (PDFKit's text interaction; with double-tap zoom on or off); Android
  selects only on a long press. A long press with selection off does nothing: it
  is not a tap on either platform.
- **Disabling on iOS** uses public API only: the gesture recognizers every
  `UITextInteraction` in PDFKit's view tree lists in
  `gesturesForFailureRequirements` (long press, word taps, handle drags) are
  disabled, and re-disabled when PDFKit creates new page views (layout
  changes, paging). Link taps are not among them. Probed on iOS 26.5:
  PDFKit hosts selection in `UITextInteraction`s on `PDFDocumentView`
  (continuous) and on each page's `PDFTextInputView` (paged).
- **Settle detection on iOS**: PDFKit's handles track their own touches, so
  the scroll view's `isTracking` stays false during a handle drag. A
  passive gesture recognizer on the container (never recognizes, cancels or
  delays anything) reports whether a finger is down; the event waits 100 ms
  after the last change and until the finger lifts.
- **Android threading**: page text is only reachable through an open page,
  and `PdfRenderer` allows one open page at a time, so selections are
  resolved on the document's render thread. They use an urgent slot in the
  render queue: taken before any pending render, never dropped by a viewport
  change, and replaced only by a newer selection (a handle drag sends one
  per move, ~100 per second; only the latest waiting one runs). Results
  older than the one on screen are discarded. These page opens do not count
  towards the renderer recycling (`RECYCLE_AFTER_PAGES`): they re-open the
  same, already parsed page.
- **Android boundaries**: `PageSelection`'s public model keeps a
  boundary's text index only when it has no point, and PdfRenderer always
  resolves boundaries to points (measured on API 35: index `-1`, point on the
  baseline). During a handle drag the fixed end is therefore sent back as
  the point PdfRenderer returned, which resolves to the same character. A
  handle dragged past the other end does not reverse the selection:
  PdfRenderer stops at the first character. Line breaks (`\r\n` in
  PdfRenderer's text) are reported as `\n`, as on iOS.
- **Android 15+ (API 35)**, like links: `PdfRenderer.Page.selectContent` is
  where the platform renderer started exposing text — also on Android 12–14
  with SDK extension 13 (`PdfRendererPreV.Page.selectContent`). Other
  devices ignore long presses.
- **Not yet on Android**: selections across pages, and scrolling while a
  handle is dragged to the view's edge.
- **Tested** (`viewer-text-selection`, both platforms): a long press
  selects "Page" (event #1), dragging the stop handle over the next line
  produces exactly one event (#2), a tap clears (#3) and is still a page
  tap, and with selection off neither a long press in the continuous nor in
  the paged layout selects anything (no Copy menu, no event); turned back
  on in the paged layout, a long press selects again.

## Outline (v0.2)

```ts
const toc: PdfOutlineItem[] = await ref.current.getOutline();
type PdfOutlineItem = { title: string; page?: number; children: PdfOutlineItem[] };
```

- **A method, not an `onLoad` field** (react-native-pdf's 4th
  `onLoadComplete` argument): most apps never show it, and reading it costs
  real time on large outlines (measured below). One-shot async work is a
  `Promise`. Cached after the first call.
- **A tree in JS, flat at the Nitro boundary.** Nitro structs cannot be
  recursive; native sends `{ title, page?, depth }` depth-first and the
  wrapper rebuilds the tree (`buildOutlineTree`, unit tested).
- **`page` is optional**: grouping entries, URL/remote actions and broken
  references have no page in this document.
- **Android has no outline API** (checked `android.jar` for API 35–37:
  `PdfRenderer` exposes links, text and images only). A bounded reader of
  the PDF structure (`outline/`) parses cross-reference tables and streams
  (incl. `/Prev` chains and hybrid files), object streams, the outline,
  explicit/GoTo/named destinations (name tree and PDF 1.1 `/Dests`) and the
  page tree, and rebuilds broken cross-references by scanning like viewers
  do. It reads a duplicate of the file descriptor with positional reads, so
  rendering is not blocked.
- **Untrusted input**: every recursion and loop is bounded (64 levels,
  10,000 entries, nesting, string, stream-expansion and xref limits); cycles
  are broken; a deterministic fuzz test (500 mutated files) must end in a
  result or `PdfFormatException` within 2 s each. Encrypted documents
  reject.
- **Tested against three producers** (the generator in classic and
  object-stream form, PDFKit, pypdf), with PDFKit as the reference.
- **iOS** walks `PDFOutline` with the same limits and title normalization.
  PDFKit objects are not thread-safe and the view's document is used by
  `PDFView`, so the outline is read from a separate `PDFDocument` of the same
  file on a background queue.
- **Measured** (`outline-large`: 1,000 entries, 3 runs; example app's
  timing):

  | | first call | cached |
  | --- | --- | --- |
  | Android emulator | 60–83 ms, background thread | 2–6 ms |
  | iOS simulator | 219–223 ms, background queue | 0–1 ms |

  Reading on the iOS main thread was faster (130 ms) but blocked it for ~8
  frames; rejected.
- **Known difference:** PDFKit leaves PDF 1.1 named destinations
  (`/Dest /name` looked up in the catalog's `/Dests`) without a page; Android
  resolves them.

### Props are never reset to `undefined` natively

Removing a prop (`fitPolicy="page"` → no prop) makes Fabric send `null`,
which Nitro 0.37's optional converters reject with a fatal JS error ("Value
is null, expected a String"). This affected every optional prop and was
found while cycling `fitPolicy` back to its default. The wrapper therefore
sends concrete defaults for every configuration prop and keeps an event
subscribed once it has been (a removed handler becomes a no-op). Covered by a
unit test and the last step of the `viewer-fit-policy` e2e flow.

## Accessibility (v0.2)

Measured first (the accessibility tree, as Maestro reads it on both
platforms — the same tree TalkBack and VoiceOver use):

| Before | iOS (PDFKit) | Android |
| --- | --- | --- |
| Page text | ✅ exposed by PDFKit | ❌ no node at all — the document was invisible to TalkBack |
| Which page | ❌ only the scroll bar ("99 pages" = screens, not PDF pages) | ❌ |

Design:

- **Android:** `PdfAccessibilityHelper` (`ExploreByTouchHelper`) exposes one
  virtual node per visible page — label plus the page's text, bounds clipped
  to the view — inside a host that reports itself as a scrollable
  `ScrollView` with collection info (page count) and handles
  `ACTION_SCROLL_FORWARD/BACKWARD` (a page in paged layouts, 90% of a screen
  otherwise). The host's state description follows the current page.
- **Text is read only while an accessibility service is on**, only for
  visible pages, as `PageTextQuery` work queued after their renders (dropped
  with them when they scroll away) and cached for the 32 pages nearest the
  reader. Without a service nothing changes. It comes from
  `getTextContents` — Android 15+, or 12–14 with SDK extension 13; older
  versions read the label only (verified on API 34: "Page 1 of 100").
- **iOS:** PDFKit already exposes the text; the viewer posts
  `UIAccessibility.pageScrolled` with the label on page changes while
  VoiceOver runs.
- **One string, localisable:** `pageAccessibilityLabel` with `{page}`
  (1-based) and `{pageCount}`. A template rather than a JS callback, so no
  native→JS round trip is needed to announce a page.
- **`testID` keeps working:** React Native maps `testID` to the view's
  resource id in its own accessibility delegate, which it installs only on
  views without one; ours replaces it, so the helper sets the resource id
  itself. Found by the regression run (`viewer-scroll-enabled` could no
  longer find `embedded-pdf`).
- **Tested:** `viewer-accessibility` asserts the page nodes with text on
  Android (first and last page), a localised label, and PDFKit's text on
  iOS. Not tested automatically: speech itself, TalkBack's scroll actions
  and the VoiceOver announcement (they need a real screen-reader session).

## Search (v0.2)

```ts
// ref
search(query: string): void;   // '' clears
searchNext(): void;
searchPrevious(): void;
clearSearch(): void;
// prop
onSearchChange?: (event: PdfSearchChangeEvent) => void;

interface PdfSearchChangeEvent {
  query: string;
  matchCount: number;
  currentMatch?: number;   // zero-based, document order
  currentPage?: number;    // zero-based
  isComplete: boolean;
  isSupported: boolean;
}
```

The most requested missing feature of react-native-pdf (7 issues, see
`community-requests.md`).

- **Methods plus one state event, not a `Promise<Match[]>`.** Searching a
  large document takes time; a Promise would show nothing until the end and
  would hand JS every match's rectangles, which JS cannot use (highlights and
  scrolling are native). The viewer owns the matches; JS gets the state it
  needs for a "3 / 17" label and next/previous buttons.
- **One event with the full state** rather than separate progress / match /
  current events, so a `useState` setter can be passed directly. Throttled
  to ≤ 10/s while pages are searched (a 1000-page document would otherwise
  send 1000 events), immediate for the first match, navigation, completion
  and clearing.
- **Starts at the current page.** Pages are searched from the reader's page
  to the end and then from the start; the first match at or after the
  current page becomes current, as in browser find bars. `currentMatch` is
  still the index in document order, so the label stays meaningful when
  more matches arrive before it.
- **Case-insensitive, no options yet.** PDFKit takes `.caseInsensitive`;
  Android's `PdfRenderer.Page.searchText` always ignores case. Whole-word
  or case-sensitive search would need filtering on one platform only, so it
  waits for a request.
- **`isSupported` is a runtime capability**, not a platform check: Android
  gains text search with the renderer's text API (15+, or 12–14 with SDK
  extension 13), and devices without it end the search at once with
  `isSupported: false`. The flag is the same for every search on a device,
  so an app can hide its search UI after the first search reports `false`.
- **Highlights**: all matches in translucent yellow, the current one in
  orange, on both platforms (PDFKit `highlightedSelections`; Android draws
  them over the page bitmaps, so zoom and tiles keep them aligned).
- **Colours are props** (`searchHighlightColor`, `searchCurrentHighlightColor`,
  `ColorValue`), not arguments of `search()`: they are styling, belong with
  the rest of the view's appearance and apply to a search already shown.
  The wrapper flattens them with `processColor` to the ARGB number the
  native props take; `PlatformColor` and `DynamicColorIOS` cannot be
  flattened, so they fall back to the defaults (a dark-mode app passes a
  colour chosen from `useColorScheme()`). PDFKit keeps pages it has drawn
  when only a highlight's colour changes, so iOS resets
  `highlightedSelections` when colours or the current match change
  (measured: without it, the old colour stayed on screen).
- **Tested:** `viewer-search` (both platforms) checks counts, next,
  previous, wrapping backwards, the page scrolled to, a query in different
  case, a custom highlight colour (checked on the screenshots), and no
  matches. `SearchResultsTest` covers ordering, indexes and
  wrapping.

## Open questions

- A `PdfDocument` Hybrid Object for metadata/page count without mounting a view.
- Supporting more internal-link forms on Android would need a PDF parser of
  our own (no platform API exposes raw annotations).
