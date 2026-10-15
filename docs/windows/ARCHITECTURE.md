# Arquitetura — SignallQ Windows Desktop

## Decisão

Tauri 2 hospeda uma UI React/Vite local e um núcleo Rust. A escolha preserva o HTML do protótipo,
mantém o instalador menor que Electron e isola qualquer acesso ao sistema atrás de comandos
tipados.

```text
React/Vite UI local
        │ comandos e eventos DTO
        ▼
Tauri boundary / Rust
 ├── WindowsNetworkAdapter (interface, IP, gateway, DNS, Wi-Fi/Ethernet)
 ├── MeasurementAdapter (HTTP throughput, latência, jitter, cancelamento)
 ├── Tools sandbox (DNS, ping e rota com allowlist e timeout)
 ├── Monitor + tray (somente com preferência explícita)
 ├── SQLite local (histórico, incidentes, preferências e migrations)
 └── Remote adapters
      ├── NDS/Assist: bloqueados até auth desktop
      └── sonda gamer: rota regional, com fallback limitado
```

## Dados e falhas

Todo resultado tem `executionId`, timestamp, status e proveniência. Campos que o Windows não
expõe são `null` com uma capability/limitação; nunca `0`. Rede trocada, timeout, cancelamento,
falha de upload e resultado parcial não constituem teste concluído. A UI diferencia fato medido,
inferência determinística e interpretação remota.

## Segurança

A UI não abre shell. Operações nativas usam destinos validados e argumentos fechados, com timeout
e encerramento. Não há segredo no bundle, execução arbitrária ou transmissão automática do
inventário de rede.

## Distribuição

O build Windows roda no GitHub Actions em `windows-latest`; macOS não é evidência de instalador,
WebView2, tray ou DPI Windows. Assinatura, MSIX e Microsoft Store são preparação futura, não
publicação.
