import { readFile, readdir } from 'node:fs/promises';

const workflows = new URL('../.github/workflows/', import.meta.url);
const files = (await readdir(workflows)).filter((file) => /\.ya?ml$/.test(file));
let actionCount = 0;

for (const file of files) {
  const source = await readFile(new URL(file, workflows), 'utf8');
  for (const match of source.matchAll(/^\s*-\s+uses:\s*['"]?([^'"\s#]+)['"]?/gm)) {
    const action = match[1];
    if (action.startsWith('./')) continue;
    actionCount += 1;
    const separator = action.lastIndexOf('@');
    const reference = separator === -1 ? '' : action.slice(separator + 1);
    if (!/^[0-9a-f]{40}$/.test(reference)) {
      throw new Error(`GitHub Action is not commit pinned in ${file}: ${action}`);
    }
  }
}

if (actionCount === 0) throw new Error('no external GitHub Actions found');
process.stdout.write(`verified ${actionCount} immutable GitHub Action references\n`);
