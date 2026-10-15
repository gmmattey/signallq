#!/usr/bin/env node
// Regenera src/brand/logoData.ts a partir de brand/*.png (fonte da verdade — ver brand/README.md).
// Uso: node scripts/generate-logo-data.mjs

import { readFileSync, writeFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { dirname, join } from 'node:path';

const here = dirname(fileURLToPath(import.meta.url));
const pkgRoot = join(here, '..');
const brandDir = join(pkgRoot, '..', '..', 'brand');
const outFile = join(pkgRoot, 'src', 'brand', 'logoData.ts');

const SOURCES = {
  SIGNALLQ_SYMBOL: 'signallq-symbol-512.png',
  SIGNALLQ_LOCKUP_LIGHT: 'signallq-lockup-light-bg.png',
  SIGNALLQ_LOCKUP_DARK: 'signallq-lockup-dark-bg.png',
};

const lines = ['// GERADO do brand/*.png — nao editar a mao, regenerar do brand/.'];
for (const [constName, file] of Object.entries(SOURCES)) {
  const buf = readFileSync(join(brandDir, file));
  lines.push(`export const ${constName} = "data:image/png;base64,${buf.toString('base64')}";`);
}

writeFileSync(outFile, lines.join('\n') + '\n');
console.log(`wrote ${outFile} from ${Object.values(SOURCES).join(', ')}`);
