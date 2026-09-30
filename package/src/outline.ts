import type { PdfOutlineEntry } from './types/PdfOutlineEntry';
import type { PdfOutlineItem } from './types/PdfOutlineItem';

/**
 * Rebuilds the outline tree from depth-first entries. A depth that jumps by
 * more than one level (malformed input) attaches to the deepest open item.
 */
export function buildOutlineTree(
  entries: readonly PdfOutlineEntry[],
): PdfOutlineItem[] {
  const roots: PdfOutlineItem[] = [];
  const open: PdfOutlineItem[] = [];
  for (const entry of entries) {
    const item: PdfOutlineItem = { title: entry.title, children: [] };
    if (entry.page !== undefined) item.page = entry.page;
    const depth = Math.max(0, Math.min(entry.depth, open.length));
    open.length = depth;
    const parent = open[depth - 1];
    if (parent === undefined) {
      roots.push(item);
    } else {
      parent.children.push(item);
    }
    open.push(item);
  }
  return roots;
}
