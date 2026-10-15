---
title: "ADR-019 — SignallQ Windows Desktop"
description: "Autoriza a plataforma Windows do SignallQ e define uma fundação desktop isolada."
type: "adr"
status: "ativo"
owner: "Luiz (CEO)"
last_updated: "2026-10-14"
version: "1.0.0"
---

# ADR-019 — SignallQ Windows Desktop

- **Status:** Aceito
- **Data:** 2026-10-14
- **Autor:** Luiz (CEO), por solicitação explícita de entrega Windows E2E
- **Supersede parcialmente:** ADR-016, somente a exclusão de Windows Desktop para SignallQ.

## Contexto

O SignallQ precisa de uma superfície desktop Windows instalada, visualmente fiel ao protótipo e
honesta sobre o que mede. O Android continua a fonte de regras existentes; Web continua sendo o
site público. Nenhum deles é um host desktop executável.

## Decisão

Windows passa a ser plataforma permitida do SignallQ. A implementação vive em `windows/`, usa
Tauri 2, React/Vite e um núcleo Rust. Ela é independente de Android e Web, mas reaproveita
contratos, vetores de teste, assets oficiais e serviços remotos compatíveis.

O desktop não incorpora tokens de NDS/Assist, não executa Kotlin Android e não declara como medida
um dado indisponível. Assist e diagnóstico remoto permanecem explicitamente indisponíveis até que
um contrato de autenticação próprio de cliente desktop seja aprovado.

## Consequências

- A exclusão de Windows em ADR-016 deixa de se aplicar ao SignallQ.
- `web/` continua sendo o site público; não será empacotado como aplicativo desktop.
- Histórico desktop usa SQLite novo e versionado; não migra o banco Room.
- Instalador e validação de DPI/tray exigem runner ou equipamento Windows e não podem ser
  comprovados em macOS.

## Não decide

Não autoriza publicação na Microsoft Store, deploy de Workers, novo fornecedor pago, mudança na
autenticação do NDS ou coleta/transmissão adicional de dados sem decisão específica.
