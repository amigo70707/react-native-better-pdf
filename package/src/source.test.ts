import { describe, expect, test } from 'bun:test';
import { normalizeSource, sourceKey } from './source';

const resolveAsset = (asset: number) =>
  asset === 7 ? { uri: 'http://localhost:8081/assets/doc.pdf' } : null;

describe('normalizeSource', () => {
  test('passes uri sources through', () => {
    expect(
      normalizeSource({ uri: 'https://x.test/a.pdf' }, resolveAsset),
    ).toEqual({
      uri: 'https://x.test/a.pdf',
    });
  });

  test('copies headers so later mutation of the caller object cannot leak in', () => {
    const headers: Record<string, string> = { Authorization: 'Bearer 1' };
    const normalized = normalizeSource(
      { uri: 'https://x.test/a.pdf', headers },
      resolveAsset,
    );
    headers.Authorization = 'Bearer 2';
    expect(normalized.headers).toEqual({ Authorization: 'Bearer 1' });
  });

  test('maps filePath to a plain path uri', () => {
    expect(
      normalizeSource({ filePath: '/data/doc.pdf' }, resolveAsset),
    ).toEqual({
      uri: '/data/doc.pdf',
    });
  });

  test('resolves required assets', () => {
    expect(normalizeSource(7, resolveAsset)).toEqual({
      uri: 'http://localhost:8081/assets/doc.pdf',
    });
  });

  test('unresolvable assets become an empty uri, which native reports as invalid-source', () => {
    expect(normalizeSource(99, resolveAsset)).toEqual({ uri: '' });
  });
});

describe('sourceKey', () => {
  test('is equal for equal values', () => {
    expect(sourceKey({ uri: 'a' })).toBe(sourceKey({ uri: 'a' }));
  });

  test('ignores header order', () => {
    expect(sourceKey({ uri: 'a', headers: { A: '1', B: '2' } })).toBe(
      sourceKey({ uri: 'a', headers: { B: '2', A: '1' } }),
    );
  });

  test('differs when the uri or any header differs', () => {
    const base = sourceKey({ uri: 'a', headers: { A: '1' } });
    expect(sourceKey({ uri: 'b', headers: { A: '1' } })).not.toBe(base);
    expect(sourceKey({ uri: 'a', headers: { A: '2' } })).not.toBe(base);
    expect(sourceKey({ uri: 'a' })).not.toBe(base);
  });

  test('cannot be spoofed by a uri containing the separator', () => {
    expect(sourceKey({ uri: 'a\n[["A","1"]]' })).not.toBe(
      sourceKey({ uri: 'a', headers: { A: '1' } }),
    );
  });
});
