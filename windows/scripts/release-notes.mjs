#!/usr/bin/env node
// Extrai de windows/CHANGELOG.md a seção da versão e imprime em Markdown, para o corpo da release.
// Uso (a partir de windows/): node scripts/release-notes.mjs X.Y.Z
import { readFileSync } from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { tagToVersion } from './version.mjs'

export function extractNotes(changelog, versionInput) {
  const version = tagToVersion(versionInput)
  const lines = changelog.split(/\r?\n/)
  const start = lines.findIndex(line => new RegExp(`^## \\[${version.replace(/\./g, '\\.')}\\]`).test(line))
  if (start < 0) throw new Error(`CHANGELOG.md não tem a seção "## [${version}] - AAAA-MM-DD". Mova as notas de "Não lançado" para ela antes de criar a tag.`)
  const end = lines.findIndex((line, index) => index > start && /^## /.test(line))
  const body = lines.slice(start + 1, end < 0 ? undefined : end).join('\n').trim()
  if (!body) throw new Error(`A seção ${version} do CHANGELOG.md está vazia.`)
  return body
}

if (process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  try {
    const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..')
    if (!process.argv[2]) throw new Error('Uso: node scripts/release-notes.mjs X.Y.Z')
    process.stdout.write(`${extractNotes(readFileSync(path.join(root, 'CHANGELOG.md'), 'utf8'), process.argv[2])}\n`)
  } catch (error) { console.error(error.message); process.exitCode = 1 }
}
