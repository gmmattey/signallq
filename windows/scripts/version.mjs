#!/usr/bin/env node
// Versão única do app Windows. Fonte de verdade: a tag `windows-vX.Y.Z`; os cinco arquivos abaixo
// precisam concordar com ela. Uso (a partir de windows/):
//   node scripts/version.mjs show
//   node scripts/version.mjs check [windows-vX.Y.Z | X.Y.Z]
//   node scripts/version.mjs set X.Y.Z
import { readFileSync, writeFileSync } from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

export const SEMVER = /^(0|[1-9]\d*)\.(0|[1-9]\d*)\.(0|[1-9]\d*)$/
export const TAG_PREFIX = 'windows-v'

const FILES = {
  'package.json': 'package.json',
  'package-lock.json': 'package-lock.json',
  'tauri.conf.json': 'src-tauri/tauri.conf.json',
  'Cargo.toml': 'src-tauri/Cargo.toml',
  'Cargo.lock': 'src-tauri/Cargo.lock',
}

// Cada padrão captura (prefixo)(versão)(sufixo) para trocar só o número, preservando o resto do arquivo.
const PATTERNS = {
  'package.json': /^(\s*"version":\s*")([^"]+)(")/m,
  'tauri.conf.json': /^(\s*"version":\s*")([^"]+)(")/m,
  'Cargo.toml': /^(version\s*=\s*")([^"]+)(")/m,
  'Cargo.lock': /(\[\[package\]\]\r?\nname = "signallq-windows"\r?\nversion = ")([^"]+)(")/,
}
// package-lock.json guarda a versão duas vezes: na raiz e em packages[""].
const LOCK_PATTERN = /^(\s*"version":\s*")([^"]+)(")/gm

export function tagToVersion(value) {
  const version = value.startsWith(TAG_PREFIX) ? value.slice(TAG_PREFIX.length) : value
  if (!SEMVER.test(version)) throw new Error(`Versão inválida: "${value}". Use ${TAG_PREFIX}X.Y.Z ou X.Y.Z (sem sufixo de pré-lançamento: o instalador MSI só aceita números).`)
  return version
}

function read(root, name) { return readFileSync(path.join(root, FILES[name]), 'utf8') }

export function readVersions(root) {
  const versions = {}
  for (const name of ['package.json', 'tauri.conf.json', 'Cargo.toml', 'Cargo.lock']) {
    const match = read(root, name).match(PATTERNS[name])
    versions[name] = match ? match[2] : null
  }
  const lock = JSON.parse(read(root, 'package-lock.json'))
  versions['package-lock.json'] = lock.version ?? null
  versions['package-lock.json (packages)'] = lock.packages?.['']?.version ?? null
  return versions
}

export function checkVersions(root, expected) {
  const versions = readVersions(root)
  const target = expected ? tagToVersion(expected) : versions['package.json']
  const problems = Object.entries(versions).filter(([, value]) => value !== target).map(([name, value]) => `${name}: ${value ?? 'não encontrado'} (esperado ${target})`)
  return { ok: problems.length === 0, target, versions, problems }
}

export function setVersion(root, value) {
  const version = tagToVersion(value)
  for (const name of ['package.json', 'tauri.conf.json', 'Cargo.toml', 'Cargo.lock']) {
    const file = path.join(root, FILES[name])
    const text = readFileSync(file, 'utf8')
    if (!PATTERNS[name].test(text)) throw new Error(`Não encontrei a versão em ${FILES[name]}.`)
    writeFileSync(file, text.replace(PATTERNS[name], `$1${version}$3`))
  }
  const lockFile = path.join(root, FILES['package-lock.json'])
  let count = 0
  const lock = readFileSync(lockFile, 'utf8').replace(LOCK_PATTERN, (all, a, b, c) => (++count <= 2 ? `${a}${version}${c}` : all))
  if (count < 2) throw new Error('Não encontrei as duas versões em package-lock.json.')
  writeFileSync(lockFile, lock)
  return version
}

function main(argv) {
  const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..')
  const [command, argument] = argv
  if (command === 'show') { console.log(JSON.stringify(readVersions(root), null, 2)); return 0 }
  if (command === 'check') {
    const result = checkVersions(root, argument)
    if (result.ok) { console.log(`OK: todos os arquivos estão em ${result.target}.`); return 0 }
    console.error(`Versões divergentes:\n- ${result.problems.join('\n- ')}`); return 1
  }
  if (command === 'set' && argument) { console.log(`Versão ${setVersion(root, argument)} gravada em 5 arquivos. Rode "node scripts/version.mjs check" e atualize o CHANGELOG.md.`); return 0 }
  console.error('Uso: node scripts/version.mjs show | check [tag] | set X.Y.Z'); return 2
}

if (process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  try { process.exitCode = main(process.argv.slice(2)) } catch (error) { console.error(error.message); process.exitCode = 1 }
}
