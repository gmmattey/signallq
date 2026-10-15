# QA report — Windows Desktop

## Execução local — 2026-10-14

| Gate | Resultado | Evidência |
|---|---|---|
| Testes TypeScript | concluído | `npm test`: 4 testes passaram. |
| Build UI | concluído | `npm run build`: TypeScript e Vite passaram. |
| Formatação Rust | concluído | `cargo fmt --check` passou. |
| Testes Rust | concluído | `cargo test`: 2 testes passaram. |
| Bundle nativo macOS | concluído | Tauri gerou `SignallQ.app` e `SignallQ_0.1.0_aarch64.dmg`. |
| Bundle/instalador Windows | não executado | requer o workflow em `windows-latest`; nenhum `.exe`/`.msi` foi alegado ou produzido neste host. |
| Runtime Windows, DPI, WebView2 e tray | não executado | requer máquina ou runner Windows com evidência de tela. |

## Gates previstos

- testes unitários TypeScript e Rust;
- build da UI e bundle Tauri no runner `windows-latest`;
- E2E de primeira execução, speed test, cancelamento, monitoramento, histórico, offline e restart;
- teclado e foco;
- screenshots de paridade visual em escalas Windows;
- revisão independente de diagnóstico, segurança e privacidade.

Resultados reais devem ser anexados nesta página após cada execução; não substituir evidência por
estado de fixture ou teste local.
