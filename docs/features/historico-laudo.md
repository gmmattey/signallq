---
title: "Histórico e Relatório para sua operadora"
description: "Lista, comparação, filtro e exportação (CSV/PDF) das medições passadas e o Relatório de diagnóstico (Laudo) exportável em PDF: regras, estados, código e testes."
type: "feature"
status: "ativo"
owner: "Marcelo"
last_updated: "2026-10-04"
version: "1.1.1"
feature: "historico-laudo"
tipo: "jornada"
modulos:
  - "android/feature/history"
  - "android/core/database"
  - "android/core/relatorio"
  - "android/app"
arquivos:
  - "android/app/src/main/kotlin/io/signallq/app/ui/screen/HistoricoScreen.kt"
  - "android/app/src/main/kotlin/io/signallq/app/ui/screen/HistoricoConclusaoMapper.kt"
  - "android/app/src/main/kotlin/io/signallq/app/ui/screen/ExportHistoricoBottomSheet.kt"
  - "android/app/src/main/kotlin/io/signallq/app/ui/screen/AppShellHistoricoRoot.kt"
  - "android/app/src/main/kotlin/io/signallq/app/ui/screen/LaudoScreen.kt"
  - "android/app/src/main/kotlin/io/signallq/app/ui/screen/AppShellLaudoOverlay.kt"
  - "android/app/src/main/kotlin/io/signallq/app/ui/relatorio/"
  - "android/feature/history/src/main/kotlin/io/signallq/app/feature/history/"
  - "android/core/relatorio/src/main/kotlin/io/signallq/app/core/relatorio/"
  - "android/core/database/src/main/kotlin/io/signallq/app/core/database/MedicaoEntity.kt"
contratos: []
eventos:
  - "feature_used (feature_id=historico)"
  - "screen_view (screen_name=historico | laudo)"
  - "diagnostico_reteste_iniciado"
  - "diagnostico_comparacao_concluida"
flags:
  - "consumer_history_enabled"
  - "consumer_diagnostico_enabled"
testes:
  - "android/app/src/test/kotlin/io/signallq/app/ui/screen/HistoricoConclusaoMapperTest.kt"
  - "android/app/src/test/kotlin/io/signallq/app/ui/screen/HistoricoConclusaoLayoutTest.kt"
  - "android/app/src/test/kotlin/io/signallq/app/ui/screen/HistoricoScreenHelpersTest.kt"
  - "android/app/src/test/kotlin/io/signallq/app/ui/screen/HistoricoScreenBufferbloatVereditoTest.kt"
  - "android/app/src/test/kotlin/io/signallq/app/ui/screen/HistoricoUptimeWiringCaracterizacaoTest.kt"
  - "android/app/src/test/kotlin/io/signallq/app/ui/screen/LaudoScreenExecutionVersioningTest.kt"
  - "android/app/src/test/kotlin/io/signallq/app/MainViewModelHistoricoTest.kt"
  - "android/feature/history/src/test/kotlin/io/signallq/app/feature/history/ExportadorHistoricoCSVTest.kt"
  - "android/feature/history/src/test/kotlin/io/signallq/app/feature/history/ExportadorHistoricoPDFTest.kt"
  - "android/feature/history/src/test/kotlin/io/signallq/app/feature/history/ExportHistoricoFlowTest.kt"
adrs: []
thresholds_em: "android/feature/history/src/main/kotlin/io/signallq/app/feature/history/VereditoReteste.kt"
---

# Histórico e Relatório para sua operadora

Migrado de `FUNCIONAL.md` §5.8 (Histórico) e do parágrafo "Laudo" de §5.7. O gráfico de uptime do Histórico (§5.9, issues #1666/#1520) pertence a [Monitoramento](monitoramento-alertas.md) e não foi migrado aqui. Conferido no código em 2026-10-04.

## Negócio

### 1. Problema e promessa

Depois de medir, o usuário precisa **confirmar** se melhorou e, em conflito com a operadora, **provar** o que acontece. O Histórico guarda as medições e prioriza conclusão sobre métrica crua; o Relatório (promessa do hub: "Gere um resumo completo da conexão") é o documento para levar à operadora.

### 2. Quando aparece e para quem

Histórico: aba 3 da barra inferior (`HistoricoScreen`). Relatório: `LaudoScreen`, aberto pelo hub Ferramentas (`TipoFerramenta.LAUDO`) ou pelo resultado do diagnóstico. Para quem acompanha a conexão ou está em conflito com a operadora.

### 3. Regras de decisão

- **Linha do Histórico (#1669, Task 2.0.21):** ícone de rede, frase de conclusão em destaque (ex.: "Bom para streaming", "Gargalo identificado: Bufferbloat", "Velocidade lenta"), objetivo inferido ("Diagnóstico por IA" / "Diagnóstico guiado" / "Teste de velocidade") + data, e o Mbps de download como detalhe secundário. Derivação em `HistoricoConclusaoMapper.kt`, função pura, **sem migration** (usa campos já persistidos em `MedicaoEntity`).
- **Comparação ("Comparar medições"):** duas linhas; mostra diferença de download, upload, latência e oscilação da mais antiga para a mais recente, **só com o mesmo `networkId`**.
- **Filtro:** pills Todos / Wi-Fi / Rede móvel e, havendo dados, filtro por operadora. Medições sintéticas do monitoramento em segundo plano ficam fora da lista de testes reais.
- **Exportação:** ícone no TopBar (desabilitado com lista vazia) → `ExportHistoricoBottomSheet`: período (7 dias padrão / 30 dias / Tudo) e formato (CSV padrão / PDF). Exporta sempre a lista já filtrada, recortada pelo período; arquivo no cache, compartilhado via FileProvider. Resumo: total registrado, download médio e latência média.
- **Relatório (`LaudoScreen`):** título exibido "Relatório de diagnóstico" (o nome "laudo técnico" é reservado ao Pro, descontinuado — ADR-016). Banner de status com score, resumo, grade de seis métricas (download, upload, latência, jitter, perda, bufferbloat) e recomendação. PDF pelo ícone do TopBar ou botão no rodapé; **no PDF o nome do usuário é omitido e SSID/IPs vão mascarados**.

### 4. Estados e honestidade

| Estado | Texto / ação |
|---|---|
| Registro legado (colunas novas nulas, status "completed" default) | Fallback determinístico só pela velocidade de download; nunca quebra nem fica ilegível |
| Redes diferentes ou sem identificação de rede | O app explica por que não pode comparar; não inventa equivalência |
| Lista vazia | Exportar desabilitado |
| Detalhe de medição | Download/upload em destaque; latência/oscilação/perda; linhas condicionais (tipo de rede, aviso de resultado contaminado, bufferbloat, vereditos de streaming/games/vídeo chamada, gargalo, texto do diagnóstico) com selo "Gerado por IA" ou "Diagnóstico local" |
| Diagnóstico em memória de outra execução que a medição exibida | O Relatório recusa combinar os dois e avisa |

### 5. Próximo passo e confirmação

Reteste vinculado a uma análise compara contra a medição anterior da mesma rede (veredito em `VereditoReteste.kt`; eventos `diagnostico_reteste_iniciado` / `diagnostico_comparacao_concluida`, ver [`assist-diagnostico.md`](assist-diagnostico.md)). Para a operadora: exportar o PDF do Relatório.

### 6. Fora de escopo, status e flag

Sem envio automático à operadora. Status: entregue. Flags: `consumer_history_enabled` gateia a aba Histórico; `consumer_diagnostico_enabled` gateia o Relatório (`AppShellFerramentasRoot.kt`: `LAUDO -> diagnosticoEnabled`). Slot de anúncio nativo `HISTORICO` existe (contrato em `FUNCIONAL.md` §7.0; não migrado).

## Técnico

### 7. Mapa de código

| Arquivo | Responsabilidade |
|---|---|
| `app/.../ui/screen/HistoricoScreen.kt`, `AppShellHistoricoRoot.kt` | Tela e raiz da aba |
| `app/.../ui/screen/HistoricoConclusaoMapper.kt` | Conclusão/objetivo por linha (pura) |
| `app/.../ui/screen/ExportHistoricoBottomSheet.kt` | Período + formato; chama `ExportadorHistoricoPDF().exportarComWebView` / CSV |
| `feature/history/.../ExportadorHistoricoCSV.kt`, `ExportadorHistoricoPDF.kt` | Exportação (CSV: Data, Hora, Download, Upload, Latência, Jitter, Perda, Bufferbloat, Fonte) |
| `feature/history/.../VereditoReteste.kt`, `TendenciaEstado.kt`, `ResumoHistorico.kt`, `ObservadorHistorico*.kt` | Veredito de reteste, tendência, resumo, observação do Room |
| `app/.../ui/screen/LaudoScreen.kt`, `AppShellLaudoOverlay.kt` | Relatório e overlay |
| `app/.../ui/relatorio/` (`RelatorioDiagnosticoHtmlBuilder`, `RelatorioDiagnosticoExporter`, `RelatorioDiagnosticoSnapshot`, `RelatorioPrivacidade`) | HTML/PDF do Relatório e mascaramento |
| `core/relatorio/` (`PdfPrintHelper`, `WebViewHtmlPdfExporter`) | Geração de PDF via WebView |
| `core/database/.../MedicaoEntity.kt`, `MedicaoDao.kt` | Persistência das medições (Room) |

### 8. Dados e contratos

Fonte: tabela de medições Room (`MedicaoEntity`); filtros e comparação por `networkId`. Exportação gera arquivo no cache e compartilha via FileProvider (`${applicationId}.fileprovider`). Sem contrato OpenAPI.

### 9. Eventos e flags

`feature_used` com `feature_id=historico` (ao mudar filtro de conexão ou operadora, `MainActivity.kt`). `screen_view` com `historico` e `laudo`. Eventos de reteste/comparação: ver Assist. Flags: tabela da seção 6; catálogo em `consumer-catalog.json`.

### 10. Falhas e fallback

Registro legado → fallback por download. Redes diferentes → comparação recusada com explicação. Diagnóstico de execução diferente → Relatório recusa combinar. **Motor de PDF (`:core:relatorio`):** `exportarHtmlComoPdf(html, arquivo, context): Boolean` renderiza HTML via `WebView` (JavaScript desabilitado) e o pipeline de impressão do Android (A4, 300 dpi, sem margens); timeout de 10 s no carregamento e outro de 10 s na impressão (pior caso ~20 s, não configuráveis); **qualquer falha vira `false`, sem log nem distinção de causa** (timeout, HTML inválido, disco, `WebView` indisponível). Exige `Context` vivo e Main thread. O `ExportadorHistoricoPDF.exportar` sem `Context` ainda usa `PdfDocument` manual (dois caminhos convivem). Nenhum componente limpa PDFs temporários acumulados (fora de escopo declarado no KDoc de `RelatorioDiagnosticoExporter`). Falha na geração do CSV: **não verificado**.

### 11. Testes

Lista em `testes:`. `HistoricoUptimeWiringCaracterizacaoTest` cobre a religação do uptime (assunto de Monitoramento).

### 12. Riscos

- Privacidade do PDF: mascaramento de SSID/IP e omissão do nome dependem de `RelatorioPrivacidade`; qualquer campo novo no Relatório exige revisão de Tiago.
- `:core:relatorio` sem nenhum teste (nem `src/test`); erros de timeout/`onWriteFailed`/`onLayoutCancelled` só se verificam em produção. `RelatorioDiagnosticoExporter` (`:app`) é o renderer único de PDF do Consumer (GH#1219), usado também pelo resultado de velocidade.
- Comparação livre entre duas medições da lista do Histórico não existe (conforme FUNCIONAL; não reconferido no código): o único fluxo de comparação é o reteste vinculado do Assist.
- Mapper de conclusão cai em fallback por download para dado legado — texto pode divergir do diagnóstico original.
- Dois exportadores PDF (histórico e Relatório) com HTML builders distintos; risco de divergência de estilo e de regra de privacidade.
