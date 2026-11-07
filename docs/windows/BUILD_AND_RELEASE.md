# Build e distribuição — Windows Desktop

## Desenvolvimento

```bash
cd windows
npm ci
npm run test
npm run build
npm run tauri:dev
```

`npm run tauri:build` gera o bundle no Windows. Em macOS, um build local apenas valida o host para
o sistema local; ele não comprova o instalador Windows.

## CI

`.github/workflows/windows-ci.yml` executa testes, build da UI e bundle no `windows-latest`,
publicando o instalador como artefato. O workflow não publica, assina ou envia à Microsoft Store.

## Release e distribuição

O processo de versão, tag, instalador e publicação no `signallq.com/windows` está em
[`RELEASE_PROCESS.md`](RELEASE_PROCESS.md). Resumo: tag `windows-vX.Y.Z` → `windows-release.yml`
constrói e cria a release como **rascunho** → o Luiz publica → o site passa a oferecer o download.

## Assinatura

O instalador ainda não é assinado. Assinatura de código (custo recorrente) e Microsoft Store/MSIX
dependem de decisão do Luiz e não fazem parte da fundação.
