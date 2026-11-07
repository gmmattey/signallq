# QA — Windows Desktop

## Correção de duração do teste completo — 2026-10-15

O executor Windows foi alterado para exigir janelas de 18 s de download e 18 s de upload, como o modo `complete` do Android. O limite é 2,5 GB por fase; alcançar o limite antes da janela produz `INCONCLUSIVE`, sem Mbps da fase. A medição instalada descrita abaixo foi feita **antes** desta correção e não valida sua duração. Ainda é necessário executar o candidato atualizado no Windows e conferir tempos e volumes no histórico. Nenhum teste completo de rede foi iniciado nesta correção.

## Execução local em 2026-10-15

| Gate | Resultado |
|---|---|
| Testes TypeScript | `npm test`: 4 passaram. |
| Build UI | `npm run build`: TypeScript e Vite passaram. |
| Rust | `cargo test`: 16 testes passaram após integração. |
| Formatação Rust | `cargo fmt --check` passou após instalação local do componente. |
| Lint Rust | `cargo clippy --all-targets -- -D warnings` passou. |
| Bundle/instalador | `npm run tauri:build` gerou MSI e NSIS x64 pt-BR do candidato `0.2.0`. MSI instalado em `C:\Program Files\SignallQ`; Windows Installer retornou código 0, registro de desinstalação e atalho do menu Iniciar apontam para a versão `0.2.0`. Banco local preservado com backup íntegro antes da instalação. |
| Runtime Windows | Executável instalado `0.2.0` abriu; migração manteve os seis registros v1. Rede Ethernet, DNS, Ping, rota, histórico e rejeição de alvo inválido passaram via WebView2/CDP local. Cancelar rota retornou `CANCELLED`. |
| Medição real | No instalado, uma execução `COMPLETE` nesta Ethernet: 385,5 Mbps download, 155,5 Mbps upload, 47,2 ms latência. Cancelamento nativo aceito sem persistir registro falso. Ainda falta referência independente e segunda rede. |
| Gamer | Rota UDP do instalado retornou `PARTIAL`, mediana 11,0 ms e 28,6% de sondas sem eco; sem atribuição a servidor de jogo. |
| Bandeja e monitor | Minimizar ocultou janela sem encerrar processo; monitor fez sonda `COMPLETE` com janela oculta; restauração funcionou. |
| CSV nativo | Diálogo salvou 9 registros em arquivo temporário; UTF-8 BOM, `schema_version=2`, detalhes de fase e campos de rede com opt-in confirmados. |
| Fechamento | Smoke inicial encontrou processo residual após WM_CLOSE; no build final o mesmo evento encerrou o PID em até 2 segundos. |

Tiago fez revisão independente do código nesta rodada. Os achados de identidade de rede,
troca durante sonda (inclusive `None`→valor), alvo de ferramenta, cancelamento entre etapas,
DNS vazio, histórico, Gamer e documentação foram corrigidos. O script
`windows/scripts/runtime-smoke.mjs` reproduz o smoke via CDP local quando o processo de teste
é aberto com a porta de depuração. O atalho Chrome App anterior permanece separado. Ainda não há
aceite de distribuição geral.

## Matriz E2E antes de distribuição

1. Primeira execução e migração de uma cópia do banco v1; seis registros antigos legíveis.
2. Download/upload/latência em duas redes reais; 403/429, offline, cancelamento e troca de rede.
3. Wi-Fi, Ethernet, VPN e múltiplos adaptadores: interface, IP, gateway, DNS e identidade coerentes.
4. Ping, DNS, rota e sequência local com falha e timeout dentro do prazo.
5. Gamer UDP com rota acessível e indisponível, sem atribuição a servidor de jogo.
6. Monitor ligado/desligado, minimizar/bandeja, fechar/reabrir e histórico persistente.
7. CSV com SSID/IP/identificador, escolha de arquivo, substituição, fórmula/aspas, exclusão.
8. Teclado, foco, escala 100/125/150%, Windows 10/11, atualização e desinstalação.

O relatório anterior do protótipo, de 2026-10-14, está preservado no histórico Git.
