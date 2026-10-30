# ADR-009 — Vocabulário canônico de issue de diagnóstico (Android x Admin)

**Data:** 2026-07-16
**Status:** Aceito

## Contexto

A auditoria de 2026-07-10 encontrou 3 vocabulários diferentes coexistindo para o mesmo conceito
("motivo de falha" / issue de diagnóstico) no SignallQ Admin (ver issue GH#881):

1. O tipo `DiagnosisIssue` em `SignallQ Admin/src/types/diagnostics.ts` — vocabulário em inglês
   (`wifi_signal_weak`, `bufferbloat_upload`, `dns_latency_high`, `mobile_congestion_suspected`,
   `gateway_slow`, `packet_loss`, `upload_bottleneck`, `unknown`), nunca correspondeu ao que o
   Android envia.
2. O dicionário `ISSUE_LABELS` em
   `SignallQ Admin/src/features/diagnostics/components/FailureReasonsPanel.tsx` — vocabulário
   em português snake_case (`sinal_fraco`, `alta_latencia`, `falha_dns`, `jitter_alto`,
   `perda_de_pacotes`, `upload_lento`, `download_lento`, `problema_fibra`,
   `gateway_inacessivel`, `bufferbloat`, `interferencia_canal_wifi`, `problema_banda`).
3. O que o Android realmente envia via `idParaIssueLabel()`
   (`android/feature/diagnostico/src/main/kotlin/io/veloo/app/kotlin/feature/diagnostico/ingest/AdminIngestPayloads.kt`).

## Decisão

**O vocabulário canônico é o vocabulário 2** (`ISSUE_LABELS` do `FailureReasonsPanel.tsx`), porque
já é exatamente o output real de `idParaIssueLabel()` no Android — confirmado por leitura direta
do código-fonte (`AdminIngestPayloads.kt:31-45`), não por suposição.

Vocabulário canônico (12 categorias + 2 estados especiais):

| Tag | Label PT-BR | Origem (Android) |
|---|---|---|
| `sinal_fraco` | Sinal fraco | id contém `RSSI`/`SINAL` |
| `alta_latencia` | Alta latência | id contém `LATENCIA`/`LATENCY` |
| `falha_dns` | Falha de DNS | id contém `DNS` |
| `jitter_alto` | Jitter alto | id contém `JITTER` |
| `perda_de_pacotes` | Perda de pacotes | id contém `PACKET`/`PERDA` |
| `upload_lento` | Upload lento | id contém `UPLOAD` |
| `download_lento` | Download lento | id contém `DOWNLOAD` |
| `problema_fibra` | Problema de fibra | id contém `FIBRA`/`GPON` |
| `gateway_inacessivel` | Gateway inacessível | id contém `GATEWAY` |
| `bufferbloat` | Bufferbloat | id contém `BUFFERBLOAT` |
| `interferencia_canal_wifi` | Interferência de canal Wi-Fi | id contém `CANAL`/`CHANNEL` |
| `problema_banda` | Problema de banda | id contém `BAND` |
| `none` | (não é falha — excluído do ranking) | sessão sem issue crítico/atenção |
| `unknown` | Outro problema não classificado | id sem keyword reconhecida, ou dado legado pré-normalização (ex: tags cruas históricas como `"Resposta"`, presentes em produção antes desta padronização) |

`types/diagnostics.ts` (`DiagnosisIssue`) foi atualizado para bater 1:1 com esta tabela.

`SignallQ Admin/src/types/ads.ts` (`DiagnosisIssue` de `ContextualAdOpportunity`, feature de
monetização/ads contextual ainda não implementada com dado real) usa um vocabulário próprio,
diferente deste. Fora do escopo desta decisão — é um contrato especulativo de uma feature futura,
não um espelho do dado real de diagnóstico. Fica registrado aqui para quem for implementar
monetização contextual não reusar esse tipo sem revisar.

## Consequências

- `FailureReasonsPanel.tsx` nunca mais exibe tag crua: qualquer valor fora da tabela acima cai no
  bucket `unknown` ("Outro problema não classificado").
- `none` é excluído do ranking e do total usado no cálculo de percentual — não conta mais como
  "motivo de falha" (correção do double-counting documentado também no backend, GH#765,
  `signallq-admin-worker/src/index.ts` `NON_ISSUE_LABELS`).
- Se o Android introduzir uma nova categoria de issue local (novo `id` de regra em
  `FindingEngine.kt`) sem keyword reconhecida por `idParaIssueLabel()`, ela cai em `unknown` dos
  dois lados — não quebra a UI, mas fica sem rótulo específico até alguém atualizar
  `idParaIssueLabel()` (Android) e `ISSUE_LABELS`/`DiagnosisIssue` (Admin) juntos.
- Mudança de contrato de tipo (`DiagnosisIssue`) é breaking apenas para consumidores TypeScript
  dentro do próprio Admin — não afeta o payload enviado pelo Android nem o schema D1 (o worker já
  armazena `issues` como JSON de strings livres, sem enum/CHECK constraint).
