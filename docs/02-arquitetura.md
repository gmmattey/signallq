---
title: "Arquitetura — SignallQ consumer"
description: "Visão de sistema, módulos Gradle e dependências, do código real"
type: "técnico"
status: "ativo"
owner: "Camillo"
last_updated: "2026-10-04"
version: "1.1.0"
---

# Arquitetura — SignallQ consumer

- **Fonte de verdade:** o código. Este documento é derivado dele, não o contrário. Os números do
  bloco de inventário abaixo são **gerados** por `scripts/gerar-inventario-docs.sh`.
- **Escopo:** app consumer Android (`io.signallq.app`) e sua relação com o backend Cloudflare.
  Não cobre SignallQ Pro (descontinuado permanentemente, ver ADR-016), Admin (`buildea-admin`)
  nem o site público (`web/`, com documentação própria em `web/docs/` e `web/AGENTS.md`).
- **Detalhe por módulo:** `MODULOS/` — um documento por módulo Gradle consumer.

<!-- INVENTARIO:INICIO — gerado por scripts/gerar-inventario-docs.sh, nao editar a mao -->

> **Inventário gerado do código.** Não editar manualmente — rode
> `scripts/gerar-inventario-docs.sh`. Cada número abaixo sai da fonte citada.

| Fato | Valor | Fonte |
|---|---|---|
| versionName / versionCode | **1.0.9** / **89** | `android/gradle/libs.versions.toml` |
| compileSdk / minSdk / targetSdk | 37 / 24 / 36 | `android/gradle/libs.versions.toml` |
| Compose BOM · Room · Hilt | 2026.06.01 · 2.8.4 · 2.60.1 | `android/gradle/libs.versions.toml` |
| Módulos Gradle | **22** | `android/settings.gradle.kts` |
| Workers Cloudflare | 5 | `integrations/cloudflare/*/wrangler.toml` |
| Tabelas D1 | 38 — 20 admin + 18 diagnostic | `*/migrations/*.sql`, `*/schema.sql` |
| Contratos OpenAPI | 5 contratos · **108** endpoints | `docs/CONTRATOS/openapi/` |
| Arquivos `.kt` em caminho legado `io/veloo` | 0 (sendo 0 em `src/main`) | dívida conhecida — higiene §4.1 |

**Módulos (22):** :app :core:diagnostico :core:featureflags :core:nds :core:probejogo :core:relatorio :coreDatabase :coreDatastore :coreNetwork :corePermissions :coreRecommendation :coreTelephony :featureDevices :featureDiagnostico :featureDns :featureFibra :featureHistory :featureHome :featureRouter :featureSettings :featureSpeedtest :featureWifi

**Workers:**

| Diretório | `name` no wrangler |
|---|---|
| `ai-diagnosis-worker` | `linka-ai-diagnosis-worker` |
| `game-latency-probe-worker` | `signallq-game-latency-probe` |
| `signallq-admin-worker` | `signallq-admin` |
| `signallq-diagnostic-worker` | `signallq-diagnostic` |
| `signallq-privacy-worker` | `signallq-privacy` |

**Contratos:**

| Arquivo | Versão | Endpoints |
|---|---|---:|
| `ai-diagnosis-worker.yaml` | 2 | 2 |
| `game-latency-probe-worker.yaml` | 1 | 2 |
| `signallq-admin-api.yaml` | 2.2.0 | 59 |
| `signallq-diagnostic-worker.yaml` | 1 | 43 |
| `signallq-privacy-worker.yaml` | 1 | 2 |

<!-- INVENTARIO:FIM -->

---

## 1. Visão geral

App Android nativo de diagnóstico de conectividade. Mede velocidade, analisa Wi-Fi e rede móvel,
lê modem/ONT de fibra e roteador, testa DNS e interpreta tudo isso em veredito humano — por um
motor determinístico local e, opcionalmente, por Workers de IA/diagnóstico.

Quatro camadas:

```
┌─────────────────────────────────────────────────────────────┐
│  :app          UI (Compose), navegação, DI, composição      │
│                ↑ TODA a UI vive aqui — ver §4               │
├─────────────────────────────────────────────────────────────┤
│  :feature*     motores e vocabulário por domínio            │
│                (10 módulos — sem Composable)                │
├─────────────────────────────────────────────────────────────┤
│  :core*        infraestrutura compartilhada                 │
│                (11 módulos — rede, banco, prefs, permissões,│
│                 telefonia, recomendação, diagnóstico, NDS,  │
│                 relatório, feature flags, sonda de jogo)    │
└─────────────────────────────────────────────────────────────┘
                              ↕ HTTPS
┌─────────────────────────────────────────────────────────────┐
│  Cloudflare    5 Workers · 2 bancos D1                      │
└─────────────────────────────────────────────────────────────┘
```

## 2. Regras de dependência

1. `:feature*` **nunca** depende de outra `:feature*` — composição acontece em `:app` ou por
   contrato normalizado em um `core`.
2. `:core*` não depende de `:feature*`.
3. `:app` pode depender de tudo.

Nenhuma violação da regra 1 hoje (a única histórica, `:featureDiagnostico` → `:featureSpeedtest`,
saiu em GH#1682). Reconfirmar com `grep -rn 'project(":feature' android/feature/*/build.gradle.kts`.

Contraexemplo de como fazer certo: `:featureHome` precisa de dados de medição e **não** depende de
`:featureSpeedtest` — define uma struct genérica (`ResolvedorMedicaoHome`) e a adaptação vive em
`HomeMedicaoAdapter.kt`, em `:app`.

## 3. Módulos

Detalhe por módulo em `MODULOS/`.

### `:core*` — infraestrutura (11)

| Módulo | Papel |
|---|---|
| `:coreNetwork` | Sondagens de rede (probes, gateway, scan Wi-Fi, topologia) e contratos de analytics. **Sem lib HTTP** — `HttpURLConnection`/`Socket`/`InetAddress` amarrados à `Network` sob análise. O mais consumido |
| `:coreDatabase` | Room (`SignallQDatabase`) — histórico, chat, outbox de analytics, mapeamento Wi-Fi |
| `:coreDatastore` | Preferências do usuário, credenciais de modem |
| `:corePermissions` | Fluxo de permissões de rede |
| `:coreTelephony` | Rede móvel (RSRP/RSRQ/SINR); exige só `READ_PHONE_STATE` |
| `:coreRecommendation` | Motor de recomendação por tags |
| `:core:diagnostico` | Motor canônico de diagnóstico (ADR-011); destino de substituição pelo NDS (ADR-017) |
| `:core:nds` | Rede e contrato do Network Diagnostics Service (ADR-017) |
| `:core:relatorio` | Paginação HTML→PDF (`WebViewHtmlPdfExporter`) |
| `:core:featureflags` | Catálogo de flags remotas do consumer (`consumer-catalog.json`, 14 flags) |
| `:core:probejogo` | Cliente UDP do beacon regional GameLift (Modo gamer) |

Os seis primeiros são **aliases flat legados** (`:coreNetwork`) com `projectDir` remapeado para
pasta hierárquica (`core/network`); os demais nasceram hierárquicos. Renomear os legados é migração
dedicada — afeta CI, scripts e documentação.

### `:feature*` — domínios (10)

| Módulo | Papel |
|---|---|
| `:featureSpeedtest` | motor de medição (`ExecutorSpeedtestCloudflare`) |
| `:featureDiagnostico` | orquestração + clientes do Worker de IA e do ingest de analytics |
| `:featureDevices` | scanner da rede local |
| `:featureFibra` | leitura de ONT GPON |
| `:featureRouter` | driver de roteador (TP-Link Archer C6) |
| `:featureDns` | comparação de resolvedores; sem ViewModel próprio (estado vai ao `MainViewModel`) |
| `:featureHistory` | histórico e exportação (PDF via `PdfDocument`) |
| `:featureWifi` | vocabulário de Wi-Fi (módulo mínimo; a classificação de redes está em `SinalWifiSection.kt`) |
| `:featureHome` | resolução de medição da Home (módulo mínimo) |
| `:featureSettings` | regras puras de ajustes, sem UI; depende só de `:coreDatabase` — destino natural é um `core` |

## 4. A inconsistência principal: UI fora das features

**Nenhum módulo `:feature*` ou `:core*` contém `@Composable`.** Toda a interface vive em
`android/app/src/main/kotlin/io/signallq/app/ui/`. Consequência: as features viraram bibliotecas de
motor e vocabulário, e `:app` concentra a maior parte do código, incluindo vários arquivos acima do
limiar de 800 linhas (`MainViewModel.kt`, `AppShell.kt`, `HistoricoScreen.kt`,
`LocalDeviceSection.kt`, `SinalWifiSection.kt`, `SinalCanalSection.kt` e outros). Contagem atual:
`find android/app/src/main -name '*.kt' | xargs wc -l | sort -rn`.

Efeitos concretos: `:featureWifi` é minúsculo porque a classificação de redes ainda é montada em
`SinalWifiSection.kt`; `:featureDns` não tem ViewModel porque o `MainViewModel` monolítico assume o
estado.

O destino é mover cada tela para o módulo da sua feature — migração dedicada, por tela, com teste de
caracterização antes. Registro dos arquivos críticos em
`.claude/rules/higiene-e-padronizacao-repositorio.md` §4.

## 5. Fluxo de dados

**Medição:** `:app` dispara → `:featureSpeedtest` executa → resultado classificado por
`:core:diagnostico` → persistido por `:coreDatabase` → recomendação por `:coreRecommendation` →
renderizado por `:app`.

**Diagnóstico com IA:** `:featureDiagnostico` monta payload → `POST` ao `ai-diagnosis-worker` →
resposta v2 → fallback local determinístico em qualquer falha (sem auth, timeout, não-2xx, JSON
inválido). A migração para o NDS (`:core:nds`, ADR-017) está em andamento.

**Analytics:** cada evento vai **simultaneamente** ao Firebase e a uma outbox Room local; um
processador com backoff drena a outbox para `POST /ingest/analytics` no `signallq-admin-worker`,
que grava em D1. Não há Cloudflare Queue.

## 6. Backend Cloudflare

5 Workers (tabela do inventário). Dois têm banco D1 próprio: `signallq-admin` e
`signallq-diagnostic`. O `name` no `wrangler.toml` difere do nome do diretório em quase todos —
conferir a tabela do inventário antes de fazer deploy. Contratos em `../CONTRATOS/openapi/`.

## 7. Riscos arquiteturais

| Risco | Evidência | Efeito |
|---|---|---|
| UI monolítica em `:app` | arquivos grandes em `MainViewModel`, `AppShell` e seções de rede | Features anêmicas; mudança visual exige tocar arquivos centrais |
| Três mecanismos de feature flag | `:core:featureflags` + `FeatureFlagProvider` legado em `:coreNetwork` + Firebase Remote Config | Ambiguidade sobre qual vence |
| Dois motores de PDF | `:featureHistory` (`ExportadorHistoricoPDF`, `PdfDocument`) e HTML→WebView via `:core:relatorio` | Manutenção dupla |
| Versão fora do catálogo | `:featureDevices` fixa `okhttp:5.5.0` no `build.gradle.kts` (catálogo: `libs.okhttp`, hoje a mesma versão) | Diverge no próximo bump |
| `:core:diagnostico` não é Kotlin puro | `topology/correlation/TopologyTracer.kt` executa `/system/bin/ping` via `Runtime.exec` | Contrato "sem `android.*`/sem I/O" do módulo não corresponde ao conteúdo |
| Credencial de modem sem cifra no fallback | `CredenciaisModemStore` cai para `SharedPreferences` comum em `catch (_: Exception)` quando o AndroidKeyStore falha | Exposição de senha de roteador — ver `../TECNICO.md` |
| Módulos sem teste unitário | `:corePermissions`, `:core:relatorio`, `:featureWifi` | Regressão silenciosa |

## 8. Decisões arquiteturais relacionadas

`ADR-003` DispatcherProvider · `ADR-004` estrutura multi-módulo · `ADR-008` features novas D1-only ·
`ADR-011` motor canônico de diagnóstico · `ADR-012` `executionId`/`rulesVersion` · `ADR-013`
unificação de latência/perda/upload · `ADR-017` motor de diagnóstico/IA migra para o NDS. Todos em
`../decisions/`.
