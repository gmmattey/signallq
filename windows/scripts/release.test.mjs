import { cpSync, mkdirSync, mkdtempSync, readFileSync, rmSync } from 'node:fs'
import { tmpdir } from 'node:os'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { afterEach, beforeEach, describe, expect, it } from 'vitest'
import { checkVersions, readVersions, setVersion, tagToVersion } from './version.mjs'
import { extractNotes } from './release-notes.mjs'

const real = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..')
const files = ['package.json', 'package-lock.json', 'src-tauri/tauri.conf.json', 'src-tauri/Cargo.toml', 'src-tauri/Cargo.lock']
let root = ''

beforeEach(() => {
  root = mkdtempSync(path.join(tmpdir(), 'sqw-version-'))
  for (const file of files) { mkdirSync(path.dirname(path.join(root, file)), { recursive: true }); cpSync(path.join(real, file), path.join(root, file)) }
})
afterEach(() => rmSync(root, { recursive: true, force: true }))

describe('version.mjs', () => {
  it('aceita tag e versão simples, recusa sufixo de pré-lançamento', () => {
    expect(tagToVersion('windows-v1.2.3')).toBe('1.2.3')
    expect(tagToVersion('0.3.0')).toBe('0.3.0')
    expect(() => tagToVersion('windows-v1.2.3-rc.1')).toThrow(/inválida/)
    expect(() => tagToVersion('v1.2.3x')).toThrow()
  })
  it('lê a mesma versão nos seis pontos do repositório real', () => {
    const result = checkVersions(real)
    expect(result.problems).toEqual([])
  })
  it('set troca só o número nos arquivos e o check passa em seguida', () => {
    const before = readFileSync(path.join(root, 'src-tauri/tauri.conf.json'), 'utf8')
    setVersion(root, 'windows-v9.8.7')
    expect(checkVersions(root, 'windows-v9.8.7').ok).toBe(true)
    expect(Object.values(readVersions(root)).every(value => value === '9.8.7')).toBe(true)
    const after = readFileSync(path.join(root, 'src-tauri/tauri.conf.json'), 'utf8')
    expect(after.replace('9.8.7', '')).toBe(before.replace(readVersions(real)['tauri.conf.json'], ''))
  })
  it('check acusa divergência entre a tag e os arquivos', () => {
    const result = checkVersions(root, 'windows-v9.9.9')
    expect(result.ok).toBe(false)
    expect(result.problems.length).toBeGreaterThan(0)
  })
})

describe('release-notes.mjs', () => {
  const changelog = '# Changelog\n\n## [Não lançado]\n\n- Algo novo.\n\n## [0.3.0] - 2026-11-20\n\n### Adicionado\n- Item A.\n\n## [0.2.0] - 2026-10-01\n\n- Item antigo.\n'
  it('extrai só a seção da versão pedida', () => {
    const notes = extractNotes(changelog, 'windows-v0.3.0')
    expect(notes).toContain('Item A.')
    expect(notes).not.toContain('Item antigo')
    expect(notes).not.toContain('Algo novo')
  })
  it('falha se a seção não existe ou está vazia', () => {
    expect(() => extractNotes(changelog, '0.4.0')).toThrow(/não tem a seção/)
    expect(() => extractNotes('## [0.4.0] - 2026-12-01\n\n## [0.3.0] - x\n- a', '0.4.0')).toThrow(/vazia/)
  })
})
