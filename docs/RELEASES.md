---
title: "Histórico de releases — SignallQ Android"
description: "Releases do Android: versão, versionCode, data e escopo (detalhe de v1.0.2 em diante; versões anteriores resumidas)."
type: "referência"
status: "ativo"
owner: "Marcelo"
last_updated: "2026-10-04"
version: "2.0.0"
---

# Histórico de Releases — SignallQ Android

**Versão atual no código:** `1.0.9` (`versionCode 89`, `android/gradle/libs.versions.toml`).
**Fontes:** tags git `vX.Y.Z`, `android/CHANGELOG.md` e `CHANGELOG.md` (raiz). Em divergência, valem o código e as tags.
Identificadores técnicos preservados: `io.signallq.app`, `buildea-labs/signallq`.

Marca: Linka (até 0.14.x) → Veloo (0.15.0) → SignallQ (0.16.0+).

---

## v1.0.9 (versionCode 89) — 2026-09-27 (tag `v1.0.9`)

- **Wi-Fi Casa:** mapeamento espacial de Wi-Fi com grade 2D e comparação Antes × Depois (#1909; ver `features/wifi-casa.md`).
- **Início:** status de conectividade ao vivo na trilha e no Hero (#1908).
- **Diagnóstico:** confiabilidade estatística de amostragem e de perda de pacotes (#1906).
- **Modo gamer:** sonda UDP real (beacon AWS GameLift) e remoção de hosts mortos do catálogo (#1902, #1904).

## v1.0.8 (versionCode 88) — 2026-09-13

- Suporte ao roteador TP-Link Archer C6 (#1894).
- Aviso de indisponibilidade de serviços externos, sem virar diagnóstico local (#1893).
- Velocímetro e carregamento do Assist mais fluidos; conclusão no Histórico quebra em até duas linhas (#1895).

## v1.0.7 (versionCode 87) — 2026-09-07

Sem mudança funcional no app: ajuste do fluxo de release (tag criada no disparo de produção) e renovação de credencial do Assist.

## v1.0.6 (versionCode 86) — 2026-09-07

Restaurada a autenticação do Assist com o diagnóstico remoto.

## v1.0.5 (versionCode 85) — 2026-09-07

- Resultado do Assist mais objetivo, com evidências medidas e origem da explicação indicada de forma discreta.
- Modo Gamer só reutiliza medição completa, recente e da mesma rede; sem ela, oferece novo teste na própria tela.

## v1.0.4 (versionCode 84) — 2026-09-06

O Assist passa a apresentar título, explicação e recomendações do NDS, sem sugerir ação local não indicada pelo diagnóstico remoto.

## v1.0.3 (versionCode 83) — 2026-08-30

Amplia o timeout do Assist v2.

## v1.0.2 (versionCode 82) — 2026-08-30

Assist com diagnóstico remoto NDS V2 na trilha de teste aberto: envia o contexto relatado, aceita contexto parcial e mantém IDs técnicos internos. A chave remota `consumer_diagnostico_assist_nds_v2_enabled` volta ao contrato V1 sem novo binário. Publicação via `release.yml`.

---

## Versões anteriores (resumo)

Detalhe de cada uma: `android/CHANGELOG.md` e `git show vX.Y.Z`. Versões 0.31–1.0.1 não constam nesta tabela. O texto integral anterior desta seção está em `git show f6f9d437:docs_ai/RELEASES.md`.

| Versão | Build | Data | Escopo |
|---|---|---|---|
| v0.30.1 | 67 | 2026-07-21 | Lote de bug fixes pós-congelamento de escopo (sem funcionalidade nova) |
| v0.30.0 | 66 | 2026-07-21 | Fecha o lote de 14 correções P0 da auditoria de 20/07/2026 (Fases 0-4 completas) |
| v0.29.0 | 65 | 2026-07-20 | Auditoria de design completa contra o Design System vivo + NAT Type na aba Jogos + ferramenta Sinal WiFi |
| v0.28.0 | 64 | 2026-07-18 | Fix de latência do motor de diagnóstico, redesign do Equipamento de internet e limpeza de contraste dark |
| v0.27.0 | 63 | 2026-07-18 | Correções pontuais e padronização de UI sobre o redesign da 0.26.0 |
| v0.26.0 | 61 | 2026-07-17 | Redesign Material 3 To-Be, motor de topologia unificado e monetização nativa |
| v0.25.0 | 60 | 2026-07-10 | Recommendation Engine, avaliação nativa e equipamento local (Nokia GPON) |
| v0.23.0 | 56 | 2026-07-05 | Logos de operadoras, canais oficiais e instrumentação de analytics |
| v0.22.1 | 54 | 2026-07-03 | Primeira publicação na Play Console + correções de topologia |
| v0.22.0 | 53 | 2026-06-29 | Ícone SignallQ, "Fale conosco" e otimizações |
| v0.21.0 | 52 | 2026-06-22 | CI, ícone do app e correções do Admin Panel |
| v0.16.0 | 46 | 2026-06-21 | Rebranding completo para SignallQ + reorganização de documentação |
| v0.15.1 | 45 | — | Correção mesh — nó "Roteador" |
| v0.15.0 | 44 | 2026-05-30 | Rebranding: Linka → Veloo |
| v0.14.4 | 43 | — | Diagnóstico IA — polimento e estabilidade |
| v0.14.2 | 41 | — | ResultadoVelocidade — ações IA e operadora |
| v0.14.0 | 39 | — | Redesign Diagnóstico IA — laudo + LLM |
| v0.13.3 | 38 | — | Correções multi-plataforma |
| v0.13.2 | 37 | — | Sinal e rede móvel — correções |
| v0.13.1 | 36 | — | ISP info — HTTPS |
| v0.13.0 | 35 | — | Redesign UI mockup v2 — fase 1 |
| v0.12.0 | — | — | Chat IA — sessões persistidas e streaming |
| v0.11.4 | 30 | — | Estabilidade |
| v0.11.x | — | — | Features avançadas — Fibra, DNS, Dispositivos, Onboarding |
| v0.9.0 | — | — | Ping/Latência, DNS provedores BR, ExploreToolsRow |
| v0.8.1 | — | — | Thresholds Wi-Fi por banda, DNS-03, FibraScreen, acessibilidade |
