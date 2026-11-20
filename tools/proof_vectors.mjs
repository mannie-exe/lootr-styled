import Model from '../build/proofs/model.mjs';
import { mkdir, writeFile, readFile } from 'node:fs/promises';

// Expected values come from the compiled, kernel-checked Bend model. Java tests
// independently construct actual compiler inputs and inspect their output.
const rows = ['enabled,custom,explicit,styled,inherited,source'];
for (let bits = 0; bits < 32; bits++) {
  const flags = Array.from({ length: 5 }, (_, n) => Boolean(bits & (1 << n)));
  rows.push([...flags, Model.source(...flags)].join(','));
}
await mkdir(new URL('../build/proofs/', import.meta.url), { recursive: true });
const content = rows.join('\n') + '\n';
await writeFile(new URL('../build/proofs/source-vectors.csv', import.meta.url), content);
const canonical = new URL('../proofs/source-vectors.csv', import.meta.url);
if (process.argv.includes('--write')) await writeFile(canonical, content);
else if (await readFile(canonical, 'utf8') !== content) throw new Error('Committed Java vectors differ from the compiled model; review before --write');
console.log('32 decision vectors evaluated by the compiled Bend model');
