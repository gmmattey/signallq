# SignallQ para Windows

App desktop do SignallQ (Tauri 2 + React/Vite + Rust). Isolado: não acopla `android/` nem `web/`.
Governança e decisões: [`AGENTS.md`](../AGENTS.md) e [`docs/DECISOES.md`](../docs/DECISOES.md) (ADR-019 e ADR-023/024).

## Estrutura

```text
windows/
├── README.md             este arquivo
├── CHANGELOG.md          notas por versão; alimenta o texto da release
├── package.json          scripts npm (dev, build, test, tauri:*, version:*, release:notes)
├── index.html, vite.config.ts, tsconfig.json
├── src/                  interface React (App.tsx, ui.tsx, native.ts, metrics.ts, styles.css, tokens.css)
│   ├── assets/           fonte Google Sans Flex (SIL OFL) e símbolo da marca
│   └── devMock.ts        backend simulado, só com VITE_MOCK=1; fora do build de produção
├── src-tauri/            backend Rust: comandos, medição, monitor, histórico (SQLite), tauri.conf.json
├── scripts/              automação: version.mjs, release-notes.mjs, runtime-smoke.mjs e testes
└── design/               referência visual (protótipo, tokens, spec do DS); não entra no app
```

Não versionado (ver `.gitignore`): `node_modules/`, `dist/`, `src-tauri/target/`.
Documentação funcional e técnica fica em [`docs/windows/`](../docs/windows/).

## Comandos (a partir de `windows/`)

| Para | Comando |
| --- | --- |
| Instalar | `npm ci` |
| Rodar a interface no navegador com backend simulado | `VITE_MOCK=1 npm run dev` (PowerShell: `$env:VITE_MOCK=1; npm run dev`) |
| Rodar o app de verdade | `npm run tauri:dev` |
| Testes (interface e scripts de release) | `npm test` |
| Build da interface | `npm run build` |
| Checar Rust | `cargo check --manifest-path src-tauri/Cargo.toml` |
| Gerar o instalador (só no Windows) | `npm run tauri:build` |
| Conferir a versão nos 5 arquivos | `npm run version:check` |
| Mudar a versão nos 5 arquivos | `npm run version:set -- X.Y.Z` |
| Ver o texto da release de uma versão | `npm run release:notes -- X.Y.Z` |

## Versão e release

A versão vive em cinco arquivos (`package.json`, `package-lock.json`, `src-tauri/tauri.conf.json`,
`src-tauri/Cargo.toml`, `src-tauri/Cargo.lock`) e **só muda por `version:set`**. A tag de release é
`windows-vX.Y.Z`. O passo a passo, quem autoriza e como o instalador chega ao `signallq.com/windows`
estão em [`docs/windows/RELEASE_PROCESS.md`](../docs/windows/RELEASE_PROCESS.md).
