---
title: "P2 — Separação de ambiente do Admin Worker e do D1"
description: "Plano técnico, ainda não executado, para isolar o D1 de desenvolvimento do de produção no signallq-admin-worker."
type: "técnico"
status: "draft"
owner: "Camillo"
last_updated: "2026-10-04"
version: "1.1.0"
---

# P2 — Separação de ambiente do Admin Worker e do D1

**Status:** plano técnico, sem nenhuma operação remota executada; depende de autorização do Luiz (cria recurso Cloudflare).
**Escopo:** `integrations/cloudflare/signallq-admin-worker`.
**Problema:** desenvolvimento e produção compartilham o mesmo D1 (`signallq-admin-db`). `environment`, `dist_channel` e `build_type` nos registros só filtram logicamente; não isolam dados, credenciais, migrations nem o impacto de testes.

## Estado confirmado (2026-10-04)

`wrangler.toml` tem um único `[[d1_databases]]` (binding `DB` → `signallq-admin-db`) e nenhum `[env.development]`. O código TypeScript depende só de `Env.DB`, então a separação é de configuração e de recurso remoto, sem mudança de código.

## Mudança proposta

Criar um D1 de desenvolvimento (por exemplo `signallq-admin-dev-db`) e um ambiente Wrangler `development` cujo `DB` aponte só para ele. Produção mantém binding e `database_id` atuais.

**Pré-requisitos (Luiz):** autorizar a criação do D1 e a convenção de nome; acesso Cloudflare para criar D1, aplicar migrations e publicar em ambiente não produtivo; secrets de desenvolvimento próprios — nunca cópia de `INGEST_KEY`, `ADMIN_SECRET` ou credenciais de produção.

## Sequência após autorização

1. Criar o D1 de desenvolvimento e registrar o `database_id`.
2. Adicionar `[env.development]` e `[[env.development.d1_databases]]` ao `wrangler.toml`, mantendo o binding `DB`.
3. Aplicar todas as migrations (`migrations/001`–`022`) ao novo banco, em ordem, registrando o SHA-256 de cada arquivo aplicado.
4. Configurar só secrets de desenvolvimento no ambiente `development`.
5. Publicar apenas com `--env development`; nunca `wrangler deploy` sem ambiente explícito.
6. Validar `GET /health`, ingest autenticado com a chave de desenvolvimento e contagens no D1 de desenvolvimento.
7. Confirmar que os ids dos dois D1 são diferentes e que nenhuma escrita de teste apareceu em produção.

**Evidências de aceite:** saída da criação do D1 (sem secrets); diff do `wrangler.toml` com bindings distintos; lista e hashes das migrations aplicadas; smoke HTTP do `development`; contagens nos dois bancos.

**Limite:** a separação não pode ser simulada filtrando a coluna `environment`.
