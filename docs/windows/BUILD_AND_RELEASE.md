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

## Assinatura e distribuição

Antes de qualquer distribuição pública é necessário fornecer certificado de code signing e decidir
o canal (MSIX/Microsoft Store ou instalador tradicional). Esses passos exigem autorização explícita
e ficam fora desta fundação.
