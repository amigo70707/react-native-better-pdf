# Outline fixtures from other producers

The Android outline reader (`package/android/.../outline/`) is tested against
files from three producers. `generate-fixtures.mjs` writes `outline`,
`outline-compressed`, `outline-large` and the `malformed-outline-*` files;
these scripts write the other two from `outline.pdf`:

```sh
node benchmarks/generate-fixtures.mjs outline
swiftc -O benchmarks/outline-fixtures/pdfkit-outline.swift -o /tmp/pdfkit-outline
(cd benchmarks/fixtures && /tmp/pdfkit-outline --write-pdfkit outline.pdf outline-pdfkit.pdf)
python3 benchmarks/outline-fixtures/pypdf-outline.py
```

`/tmp/pdfkit-outline file.pdf …` prints each file's outline as PDFKit reads
it (depth, page, title) — the reference the expected values in
`PdfOutlineReaderTest` were checked against. Copies of the small files live
in `package/android/src/test/resources/outline/`.
