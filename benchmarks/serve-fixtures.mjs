#!/usr/bin/env node
// Serves benchmarks/fixtures over HTTP so the example app can copy them onto
// the device once. Android: run `adb reverse tcp:8090 tcp:8090` first.
//
//   node benchmarks/serve-fixtures.mjs [port]
//
// `?bytesPerSecond=N` streams the file at roughly N bytes per second, to make
// download progress observable on a local network.

import { createReadStream, statSync } from 'node:fs';
import { createServer } from 'node:http';
import { basename, dirname, join } from 'node:path';
import { Transform } from 'node:stream';
import { fileURLToPath } from 'node:url';

const root = join(dirname(fileURLToPath(import.meta.url)), 'fixtures');
const port = Number(process.argv[2] ?? 8090);

function throttle(bytesPerSecond) {
  const chunkBytes = Math.max(1024, Math.floor(bytesPerSecond / 10));
  return new Transform({
    highWaterMark: chunkBytes,
    transform(chunk, _encoding, done) {
      let offset = 0;
      const push = () => {
        if (offset >= chunk.length) return done();
        this.push(chunk.subarray(offset, offset + chunkBytes));
        offset += chunkBytes;
        setTimeout(push, 100);
      };
      push();
    },
  });
}

createServer((request, response) => {
  const url = new URL(request.url ?? '/', 'http://x');
  const name = basename(decodeURIComponent(url.pathname));
  const file = join(root, name);
  try {
    const { size } = statSync(file);
    response.writeHead(200, {
      'Content-Type': 'application/pdf',
      'Content-Length': size,
    });
    const bytesPerSecond = Number(url.searchParams.get('bytesPerSecond'));
    const stream = createReadStream(file);
    if (bytesPerSecond > 0) stream.pipe(throttle(bytesPerSecond)).pipe(response);
    else stream.pipe(response);
  } catch {
    response.writeHead(404).end();
  }
  console.log(`${request.method} ${url.pathname}${url.search}`);
}).listen(port, () =>
  console.log(`Serving ${root} on http://localhost:${port}`),
);
