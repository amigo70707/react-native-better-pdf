import { describe, expect, test } from 'bun:test';
import { buildOutlineTree } from './outline';

describe('buildOutlineTree', () => {
  test('rebuilds nesting from depth-first entries', () => {
    expect(
      buildOutlineTree([
        { title: 'A', page: 0, depth: 0 },
        { title: 'A.1', page: 1, depth: 1 },
        { title: 'A.1.a', depth: 2 },
        { title: 'A.2', page: 3, depth: 1 },
        { title: 'B', page: 4, depth: 0 },
      ]),
    ).toEqual([
      {
        title: 'A',
        page: 0,
        children: [
          {
            title: 'A.1',
            page: 1,
            children: [{ title: 'A.1.a', children: [] }],
          },
          { title: 'A.2', page: 3, children: [] },
        ],
      },
      { title: 'B', page: 4, children: [] },
    ]);
  });

  test('omits page for entries without a destination', () => {
    const [item] = buildOutlineTree([{ title: 'Heading', depth: 0 }]);
    expect(item).toEqual({ title: 'Heading', children: [] });
    expect(item != null && 'page' in item).toBe(false);
  });

  test('attaches a depth jump to the deepest open entry', () => {
    expect(
      buildOutlineTree([
        { title: 'A', depth: 0 },
        { title: 'deep', depth: 5 },
      ]),
    ).toEqual([{ title: 'A', children: [{ title: 'deep', children: [] }] }]);
  });

  test('empty outline', () => expect(buildOutlineTree([])).toEqual([]));
});
