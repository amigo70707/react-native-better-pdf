# Changelog

All notable changes to this project are documented here. The project
follows [Semantic Versioning](https://semver.org/).

## 1.0.0 — unreleased

First public release: a native PDF viewer for React Native's New
Architecture, on PDFKit (iOS) and `PdfRenderer` (Android), with no JS in
the scroll, zoom and render path.

- **Viewing**: local files, bundled assets (`require()`) and downloads;
  vertical continuous, horizontal and paged layouts, single page, right to
  left, facing pages; fit policies, pinch and double-tap zoom, initial page
  and scale; `contentInset` for translucent bars; rounded cards; dark
  pages.
- **Navigation and events**: page changes, settled viewport events with
  restore on open or on demand, end-of-document detection; link presses
  and page taps in PDF points; the outline (`getOutline`).
- **Text**: search with highlighted matches; text selection with the
  platform's handles and Copy / Select all.
- **Forms**: filled in by touch or from code, read back by field name,
  exported as a filled-in PDF.
- **Page overlays**: React views attached to points of a page
  (`PdfPageOverlay`), moved natively while it scrolls and zooms.
- **Documents without a view** (`PdfDocument`): page count, metadata, page
  sizes, outline and page images.
- **Offline**: downloads kept for the session or across launches,
  revalidated with `ETag` / `Last-Modified`, shown when offline;
  `PdfCache` downloads ahead of time and manages copies.
- **More**: page images (`renderPage`), printing, password-protected
  documents, screen reader support, typed errors, load progress.
- **Android below 15**: links in every form are read from the file on
  every Android version; Android 12–14 with SDK extension 13 also get
  passwords, search, text selection and forms through `PdfRendererPreV`.

Requirements: React Native 0.81+ (New Architecture),
`react-native-nitro-modules` 0.37+, iOS 15.1, Android API 24, Expo SDK 54+
development builds. What depends on the Android version is listed under
[Limitations](README.md#limitations).
