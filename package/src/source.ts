import type { NativePdfSource } from './types/NativePdfSource';

/**
 * Where to load a PDF from.
 *
 * - `{ uri }` — `http(s)://` (downloaded natively into the app cache),
 *   `file://`, or `content://` on Android.
 * - `{ filePath }` — an absolute path on the device.
 * - `number` — a bundled asset, i.e. `require('./document.pdf')`.
 *   Metro must know the `pdf` extension (`resolver.assetExts`).
 */
export type PdfSource =
  | { uri: string; headers?: Readonly<Record<string, string>> }
  | { filePath: string }
  | number;

/** Resolves `require()` asset ids. Injected so this module stays pure. */
export type AssetResolver = (asset: number) => { uri: string } | null;

export function normalizeSource(
  source: PdfSource,
  resolveAsset: AssetResolver,
): NativePdfSource {
  if (typeof source === 'number') {
    return { uri: resolveAsset(source)?.uri ?? '' };
  }
  if ('filePath' in source) {
    return { uri: source.filePath };
  }
  if (source.headers == null) {
    return { uri: source.uri };
  }
  return { uri: source.uri, headers: { ...source.headers } };
}

/**
 * Value identity of a normalized source. Two sources with the same key load
 * the same bytes, so the native view must not reload between them.
 */
export function sourceKey(source: NativePdfSource): string {
  const headers = Object.keys(source.headers ?? {})
    .sort()
    .map((name) => [name, source.headers?.[name]]);
  return JSON.stringify([source.uri, headers]);
}
