# Benchmarks

`react-native-better-pdf` vs `react-native-pdf` 7.0.5.

No number in this document is estimated. Every figure comes from a run of the
scripts below; the raw JSON for each run is in `benchmarks/results/`
(gitignored — regenerate locally). Where a number could not be measured on
the available hardware, it says so.

## Fixtures

Generated deterministically by `node benchmarks/generate-fixtures.mjs`
(seeded PRNG, no external inputs):

| Fixture   | Pages | Size    | Content                                                                 |
| --------- | ----: | ------: | ----------------------------------------------------------------------- |
| `small`   |    10 | 0.76 MB | dense text, per-character kerned runs, uncompressed streams             |
| `medium`  |   100 | 19 MB   | text + one 900×600 colour JPEG per page                                 |
| `large`   |   500 | 225 MB  | full-page 150 dpi greyscale scans (1240×1754 JPEG), one image object per page |
| `extreme` |  1000 | 395 MB  | full-page 150 dpi colour scans (1240×1754 JPEG), one image object per page |

Every page has its own image object, so neither renderer can reuse a decoded
image across pages. Additional fixtures for correctness tests: `mixed-sizes`
(portrait, landscape, a 226×3000 pt receipt, square), `encrypted` (RC4-40,
password `secret`), and malformed files (empty, not a PDF, truncated,
corrupt xref).

## Harness

The example app's **Benchmark** tab runs the same scenario for either
library (`example/bench/scenario.ts`). Both viewers are configured as
equivalently as their APIs allow: vertical continuous scroll, fit width,
`minScale 1`, `maxScale 4`, 8 pt spacing; react-native-pdf with
`cache: false` and `trustAllCerts={false}` (its default breaks HTTP loads on
Android, see results §11). By default both open the same local `file://`
copy and **no network time is included**; the remote variant opens the
fixture from the fixture server under a fresh URL per run, so the load time
includes each library's download.

Scenario, per run (fresh mount, app cold-started by the host driver):

1. **Load** — mount → `onLoad` / `onLoadComplete`.
2. **Idle** — 1.5 s, JS frame monitor running.
3. **Page jumps** — 5 non-animated jumps (50%, 90%, 10%, 75%, 25% of the
   document), each timed from the call to the page-change event for that
   page; 0.7 s dwell after each.
4. **Zoom** — to 2.5× and back to 1× (react-native-pdf: via its `scale`
   prop, the only zoom API it has), JS frame monitor running.
5. **Scroll window** — 10 s during which the host performs swipes
   (Android: `adb shell input swipe`, 80% → 30% of the screen, 120 ms,
   every fifth swipe reversed).

### What each metric means

| Metric | How | Caveat |
| --- | --- | --- |
| Time to load | JS: mount → load event | Not "first pixel". Better PDF emits `onLoad` once the document is open (Android: after page 0's size, before the first render; iOS: after `PDFDocument` parsing). react-native-pdf emits after its own load. |
| Page jump | JS: `goToPage`/`setPage` → matching page-change event | Measures navigation + event delivery, **not** time until the target page's pixels are on screen. On iOS PDFKit posts the page-change notification synchronously inside `go(to:)`. |
| JS dropped frames | `requestAnimationFrame` intervals > 1.5× median | Measures JS-thread availability, not UI smoothness |
| JS thread CPU | Android: `/proc/<pid>/task/<tid>/stat` of `mqt_v_js`/`mqt_js` during the scroll window | percent of one core |
| Total / render-thread CPU | same source, all threads / `BetterPdf-render` | |
| Janky frames, p90 frame time | Android: `dumpsys gfxinfo` reset at scroll start, read at end | Emulator GPU is not representative of devices |
| Peak PSS | Android: `dumpsys meminfo` every 500 ms over the whole run | Includes the React Native runtime and the example app |
| Native render time | Android: Perfetto `BetterPdf#renderPage` trace sections; mean also in `getStats().meanRenderMs` | react-native-pdf has no equivalent markers |
| Cache hit rate, bitmap allocations, cancelled renders | `getStats()` at the end of the run (Better PDF, Android only) | "Hit" = page was already rendered when it scrolled into view |

## Running

```sh
bun run fixtures
node benchmarks/serve-fixtures.mjs &
```

**Android** (release build, one device connected or `ANDROID_SERIAL` set):

```sh
adb reverse tcp:8090 tcp:8090
cd example/android && ./gradlew :app:installRelease -PreactNativeArchitectures=arm64-v8a
cd ../.. && node benchmarks/run-android.mjs --runs 3
```

Each run copies its fixture onto the device first if needed; the copy happens
before the viewer mounts and is not part of any measurement.

Useful switches: `--libraries better`, `--fixtures extreme`,
`--scroll-seconds 320` (memory soak with PSS at 1 and 5 minutes),
`--reverse-every 0` (scroll in one direction only), `--source remote`
(open from the fixture server; load includes the download).

**iOS simulator** (Release build installed):

```sh
benchmarks/run-ios-sim.sh <simulator-udid> [fixtures…]
SOURCE=remote benchmarks/run-ios-sim.sh <simulator-udid> small medium large
```

Drives the same scenario through Maestro and reads the simulated app's
physical footprint with `footprint`. That is a host-process proxy — fine
for comparisons and leak detection, not a device number. Device memory and
CPU need Instruments (Allocations, Leaks, Time Profiler) on hardware.

**Native traces (Android)**:

```sh
adb shell perfetto -o /data/misc/perfetto-traces/pdf.pftrace -t 20s \
  -a software.gmi.betterpdf.example gfx view sched
```

Look for `BetterPdf#open`, `BetterPdf#renderPage` and `BetterPdf#recycleRenderer` slices.

## Size

Measured on release builds of the example app, which contains both
libraries, by attributing each file and each linked object to its library
(2026-09-30, after text selection was added). react-native-pdf includes the
dependencies it requires (AndroidPdfViewer, pdfium-android, Gson,
react-native-blob-util). react-native-nitro-modules is listed separately: it
is shared by every Nitro module in an app.

**Android** (`app-release.apk`, arm64-v8a only; `.so` files are stored
uncompressed, so the stored size is the installed size; "gzip" estimates the
download; dex sizes are unminified, the example builds without R8):

| | react-native-better-pdf | react-native-pdf 7.0.5 |
| --- | ---: | ---: |
| Native code, per ABI | `libNitroBetterPdf.so` 1,308 KB (gzip 268 KB) | `libpdfium.so` 4,660 KB + `libpdfiumandroid.so` 422 KB = 5,082 KB (gzip 2,546 KB) |
| Dex | `com.margelo.nitro.betterpdf` 125 KB | `org.wonday.pdf` 8 KB + AndroidPdfViewer 45 KB + pdfium-android 77 KB + Gson 122 KB + blob-util 52 KB = 304 KB |
| **Total, one ABI** | **≈ 1.43 MB** | **≈ 5.39 MB** |
| Shared prerequisite | react-native-nitro-modules: `libNitroModules.so` 958 KB + 12 KB dex | — |

Native code is per ABI: an APK with all four ABIs carries PDFium four times
(≈ 20 MB); an app bundle delivers one ABI per device. Of
`libNitroBetterPdf.so`, `.dynstr` + `.dynsym` (2,430 exported symbols) took
414 KB in a build from earlier the same day; building with hidden
visibility could remove most of that (not measured).

**iOS** (Release, simulator arm64 slice; linker map of the app binary; both
libraries render with the system's PDFKit, which costs the app nothing):

| | react-native-better-pdf | react-native-pdf 7.0.5 |
| --- | ---: | ---: |
| Linked code | `NitroBetterPdf` 526 KB: 147 KB hand-written Swift, 378 KB Nitrogen-generated bindings | `react-native-pdf` 42 KB + `react-native-blob-util` 120 KB = 161 KB |
| Shared prerequisite | `NitroModules` 226 KB | — |

On iOS this library is larger: its generated JSI/Swift bindings outweigh
react-native-pdf's Objective-C view.

**npm** (unpacked): react-native-better-pdf 678 KB in 282 files (tarball
133 KB) + react-native-nitro-modules 581 KB; react-native-pdf 303 KB +
react-native-blob-util 893 KB + crypto-js 487 KB +
deprecated-react-native-prop-types 46 KB.

## Results

Full tables, baselines, every experiment (including the ones that were
reverted) and raw file names: [performance/results.md](performance/results.md).

Summary, Android emulator, release builds, medians of 3 runs (2026-09-30,
results §11):

| Fixture | Load: Better / rn-pdf | Load from HTTP (incl. download): Better / rn-pdf | Peak PSS: Better / rn-pdf | Total CPU while scrolling: Better / rn-pdf |
| --- | --- | --- | --- | --- |
| small (10 p) | 122 / 162 ms | 145 / 332 ms | **118.2** / **91.0** MB (rn-pdf lower) | 31 / 60% |
| medium (100 p) | 90 / 288 ms | 298 / 666 ms | 126.8 / 132.1 MB | 48 / 120% |
| large (500 p) | 91 / 900 ms | 2028 / 4057 ms | 141.2 / 546.7 MB | 58 / 116% |
| extreme (1000 p) | 91 / 1207 ms | — | 146.8 / 900.5 MB | 72 / 116% |

Page jumps: 0–1 ms vs 34–50 ms to the page-change event. JS-thread CPU
while scrolling is equal (~2%) and so is the number of JS callbacks.
Janky-frame percentages vary between runs by more than the difference
between the libraries on the emulator (results §6 and §11). With its
default `trustAllCerts: true`, react-native-pdf could not open any HTTP
document on Android (results §11); the numbers above use `false`.

On iOS both libraries render with PDFKit: loads are equal or faster
(medium 56 vs 99 ms), peak footprints equal or lower, and HTTP loads equal
(both download natively); see results §9 and §11.
