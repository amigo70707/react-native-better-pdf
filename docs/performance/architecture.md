# Performance architecture

How the design keeps work, allocations and JS↔native traffic to a minimum.
The general architecture is in [`ARCHITECTURE.md`](../../ARCHITECTURE.md);
this page is the performance view of it. Evidence for every claim is in
[results.md](results.md).

## The one rule

```text
JS           configuration in, semantic events out — never per frame
Nitro        transport only; props diffed by identity, sent on change
native view  owns everything that happens per frame
```

Nitro does not make rendering faster; it makes the boundary cheap and typed.
Rendering speed comes from the platform renderers, virtualization, the cache,
scheduling and memory management — all native.

## Where each cost lives

| Cost | Where | How it is kept small |
| --- | --- | --- |
| React renders | JS | `PdfView` has no state; 0 renders while scrolling. Source memoized by value, callbacks identity-stable → a parent re-render sends nothing to native. |
| JS↔native calls | Nitro | no HOT crossings (see audit). While scrolling: 0 JS→native calls; native→JS only `onPageChange`, once per page boundary, and only if the app subscribed. |
| Document open | Android worker thread | constructor + page 0 only; other page sizes discovered on first render (B1). |
| Per-frame work | Android UI thread | O(log n) visible-range lookup, a ≤ 6-element render plan compared with the previous one, drawing ≤ 3 bitmaps. |
| Rendering | Android render thread / PDFKit | one thread per document (the platform allows one page at a time); queue replaced on every viewport change, so obsolete pages never start. |
| Pixels | Android cache | byte-budgeted LRU that always covers the render plan (visible + 2 ahead + 2 behind); visible pages protected; everything else evictable. |
| Allocations | Android | pooled same-size bitmaps (89% of renders reuse a bitmap in the 5-min soak); evicted pixels freed with `recycle()`, not left to the GC. |
| Parsed PDF objects | PDFium (Android) | renderer re-created every 48 page opens, dropping PDFium's per-document object cache. |
| Zoom | Android UI thread | during the gesture: a canvas transform of existing bitmaps. After it settles: one viewport-sized tile per visible page. |
| Hidden screens | Android | detach drops all bitmaps; the document stays open. |

## Hot paths

```text
A  scroll    finger → OverScroller → setOffsets → visibleRange → plan → submit → invalidate → onDraw
B  zoom      ScaleGestureDetector → zoomAround → invalidate → onDraw (scaled bitmaps)   [tiles after 80 ms idle]
C  render    RenderQueue.take → openPage → render(pooled bitmap) → post → cache.put → invalidate
```

None of them touches JS. On iOS all three are PDFKit's.

## Recycling model

The brief asks for page *containers* to be recycled. On Android there are no
per-page views at all — one `View` draws bitmaps — so the recycled unit is
the bitmap:

```text
page 10 bitmap evicted ─▶ BitmapPool ─▶ acquired for page 15 (same size) ─▶ rendered
```

A container-per-page design (RecyclerView of ImageViews) was rejected in the
research phase: it adds view-tree layout, per-page draw calls and makes
horizontal pan while zoomed hard, and it recycles nothing that the bitmap
pool does not already reuse. On iOS, PDFKit recycles its own page views.

## Adaptive rendering

Implemented where the architecture already gives it for free:

- **Zoom**: no PDF rendering during the gesture; high-resolution tiles only
  after the viewport settles.
- **Fling**: during a fling the planner still requests visible pages (they
  must appear), but tiles are never requested while moving, and the queue is
  replaced every frame, so a fast fling renders only pages that are still
  wanted when the render thread becomes free.

Fast flings are the one place where measurement shows a visible cost: in
the 5-minute soak, 121 of 1688 pages (7%) were not rendered yet when they
scrolled into view (white placeholder for a moment). Diagnosis and fixes: results §5 and §8 —
now 2.6% after the budget fix and two pages of look-behind.

## Budgets (Android, measured on the test emulator)

| Budget | Target | Status |
| --- | --- | --- |
| JS work while scrolling | 0 calls into native; ≤ 1 event per page crossed | met (see results) |
| JS work while zooming | 0 | met |
| Render plan size | ≤ visible + 3 | enforced by unit test |
| Render queue length | ≤ latest plan | enforced by unit test |
| Obsolete renders | never started | enforced by unit test |
| Cache memory | ≥ the render plan (no thrashing), ≤ 128 MB; visible pages never evicted | enforced by `PrefetchPlannerTest` and `LruByteCacheTest` |
| Load time | independent of page count | met: 109–134 ms for 10 → 1000 pages |
| Memory | independent of page count and scroll duration | met: 117–144 MB peak for 10 → 1000 pages; flat over a 5-min soak |
