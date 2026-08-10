import { readFile } from 'node:fs/promises';

const content = await readFile(new URL('../infra/images.lock', import.meta.url), 'utf8');
const rows = content.split(/\r?\n/).filter((line) => line && !line.startsWith('#'));
for (const row of rows) {
  const [, image] = row.split('=');
  if (!image || !/@sha256:[0-9a-f]{64}$/.test(image)) throw new Error(`image is not digest pinned: ${row}`);
}
for (const dockerfile of ['apps/ai-gateway/Dockerfile','apps/risk-core/Dockerfile','apps/ops-console/Dockerfile','apps/market-adapter/Dockerfile']) {
  const source = await readFile(new URL(`../${dockerfile}`, import.meta.url), 'utf8');
  for (const line of source.split(/\r?\n/).filter((item) => item.startsWith('FROM '))) if (!/@sha256:[0-9a-f]{64}/.test(line)) throw new Error(`unpinned FROM in ${dockerfile}: ${line}`);
}
process.stdout.write(`verified ${rows.length} immutable image locks\n`);
