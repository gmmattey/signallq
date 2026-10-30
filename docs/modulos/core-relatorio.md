---
title: "Módulo :core:relatorio"
description: "Motor genérico de paginação HTML→PDF via WebView, compartilhado entre :app e :featureHistory."
type: "técnico"
status: "ativo"
owner: "Camillo"
last_updated: "2026-10-04"
version: "1.2.0"
---

# `:core:relatorio`

- **Caminho físico:** `android/core/relatorio/`
- **Namespace:** `io.signallq.app.core.relatorio`
- **Tipo:** biblioteca Android (`com.android.library`)

## Responsabilidade

Renderiza uma `String` de HTML em um arquivo PDF paginado, usando
`WebView.createPrintDocumentAdapter()` e o pipeline de impressão do Android. Extraído de
`ExportadorHistoricoPDF.exportarComWebView()` (`:featureHistory`) na issue #1157 Fase 1b para
virar o motor de PDF por HTML com paginação real (o `ExportadorHistoricoPDF.exportar` sem `Context` ainda usa `PdfDocument` manual — dois caminhos convivem).

O módulo não conhece nenhum schema de dado do chamador — não sabe o que é medição, laudo ou
histórico. Montar o HTML (layout, copy, máscara de dado sensível, disclaimer) é responsabilidade
de quem chama: `RelatorioDiagnosticoHtmlBuilder` em `:app` e `gerarHtml` em `:featureHistory`.
Também não é dele decidir onde o arquivo é salvo, compartilhado ou limpo.

## Dependências

### Módulos do projeto

Nenhuma dependência de projeto. É um módulo folha — não depende de nenhum outro módulo do
monorepo, o que é o que permite reuso por qualquer consumidor sem arrastar contexto.

### Bibliotecas externas

| Biblioteca | Para quê |
|---|---|
| `androidx.core.ktx` | Extensões Kotlin do Android |
| `kotlinx.coroutines.android` | `withContext(Dispatchers.Main)`, `suspendCancellableCoroutine`, `withTimeoutOrNull` |
| `junit` (test) | Declarada, sem código de teste correspondente |
| `androidx.junit`, `androidx.espresso.core` (androidTest) | Declaradas, sem código correspondente |

As APIs de fato usadas (`android.webkit.WebView`, `android.print.PrintDocumentAdapter`,
`ParcelFileDescriptor`) vêm do próprio SDK Android, sem biblioteca intermediária.

## Consumidores

| Consumidor | Uso |
|---|---|
| `:app` | `ui/relatorio/RelatorioDiagnosticoExporter.kt` — renderer único de PDF do Consumer (GH#1219), usado pelo resultado de velocidade e pelo laudo do consumidor |
| `:featureHistory` | `ExportadorHistoricoPDF.kt` — exportação do histórico de medições (origem do código extraído) |

Contexto de produto (Histórico e Relatório): [`features/historico-laudo.md`](../features/historico-laudo.md).

## Componentes principais

| Arquivo / classe | Responsabilidade |
|---|---|
| `src/main/kotlin/io/signallq/app/core/relatorio/WebViewHtmlPdfExporter.kt` | Função `suspend exportarHtmlComoPdf(html, arquivo, context): Boolean` — API pública única do módulo. Cria o `WebView` na Main thread com JavaScript desabilitado, carrega o HTML via `loadDataWithBaseURL`, e em `onPageFinished` delega ao helper. Timeout de carregamento de 10s; qualquer falha vira `false` (nunca lança) |
| `src/main/kotlin/io/signallq/app/core/relatorio/PdfPrintHelper.kt` | `internal object` que roda o ciclo `onLayout` → `onWrite` do `PrintDocumentAdapter` escrevendo direto num `ParcelFileDescriptor` sobre o arquivo de destino. A4, 300 dpi, `NO_MARGINS`; timeout próprio de 10s e wrapper que garante callback único |

## Riscos e dívidas

- **Zero testes.** Não existe diretório `src/test` nem
  `src/androidTest`, embora `build.gradle.kts` declare `testImplementation(libs.junit)` e as
  dependências de androidTest. É o módulo com a menor cobertura do repositório, e todo o
  comportamento de erro (timeout, `onWriteFailed`, `onLayoutCancelled`) só é verificado em
  produção.
- **Falha silenciosa por contrato.** Toda a superfície pública retorna `Boolean` e engole exceções
  (`catch (e: Exception) { false }`, sem log). Quem chama não consegue distinguir
  timeout de HTML inválido, de falta de espaço em disco, ou de `WebView` indisponível — nem existe
  Timber no módulo para deixar rastro.
- **Dois timeouts independentes de 10s** (`TIMEOUT_CARREGAMENTO_MS` no exporter e `TIMEOUT_MS` no
  helper) que podem somar até ~20s de espera no pior caso, ambos hardcoded e não configuráveis
  pelo chamador.
- **`@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")`** no topo de `PdfPrintHelper.kt` —
  supressão ampla de visibilidade no arquivo, sem justificativa registrada no código.
- **Exige `Context` e Main thread.** Apesar de ser um `:core:*`, o motor só funciona com um
  `Context` Android vivo e cria um `WebView` — não é testável em JVM puro nem utilizável em
  background sem UI thread. Limitação inerente à escolha de `createPrintDocumentAdapter()`,
  mas vale registrar.
- **Limpeza de PDFs temporários não é de ninguém.** O KDoc de `RelatorioDiagnosticoExporter`
  (`:app`) registra explicitamente que política de limpeza de arquivos acumulados ficou fora de
  escopo; este módulo, por design, também não trata disso.
