# Architecture Plan — Assist Direto com Firebase AI Logic nativo e App Check (ADR-020)

## 1. Problema e Comportamento Esperado

O Assist do SignallQ utilizava um funil engessado de quatro etapas (seleção de objetivo, questionário estruturado, processamento e laudo), acoplado ao NDS / Cloudflare Worker. Essa estrutura causava atrito e alto índice de abandono.
A proposta aprovada (ADR-020) reproduz a jornada do **Assist Direto** do Linka: o usuário acessa diretamente uma tela de conversa em linguagem natural, com as métricas da última medição visíveis no topo, chips com perguntas frequentes e respostas em streaming progressivo. A conversa é efêmera (encerra ao fechar a tela).

## 2. Decisão Arquitetural e Limites

- **IA e Transporte:** Firebase AI Logic nativo (`com.google.firebase:firebase-vertexai`) consumindo Gemini Flash, com `startChat()` e `sendMessageStream()`. Elimina transporte SSE manual e proxy em Cloudflare Worker para esta jornada conversacional.
- **Segurança de Requisição:** Firebase App Check via Play Integrity em produção e Debug Provider em build de desenvolvimento.
- **Rigor Diagnóstico:** Prompt de sistema alimenta o modelo exclusivamente com medições reais observadas no dispositivo (download, upload, latência, jitter, perda, tecnologia Wi-Fi/móvel, RSSI, ISP). Sem medição recente, o assistente declara explicitamente a ausência e convida ao teste antes de opinar sobre velocidade.
- **Sessão Efêmera:** Histórico de conversa mantido apenas no `StateFlow` do `AssistChatViewModel`. Sem escrita em banco Room ou persistência local nesta etapa de MVP.
- **Rollout e Fallback:** Configuração controlada via Firebase Remote Config (`assist_chat_enabled`, `assist_model_name`), com valor padrão seguro `gemini-2.0-flash` (ou `gemini-1.5-flash`).

## 3. Componentes e Contratos

- **`io.signallq.app.assist.model`**:
  - `AssistMensagem(id, role: AssistRole, texto: String, timestamp: Long, emAndamento: Boolean)`
  - `AssistContextoRede(downloadMbps, uploadMbps, latenciaMs, jitterMs, perdaPct, tipoConexao, sinalDbm, frequenciaWifi, nomeOperadora, semMedicao)`
- **`io.signallq.app.assist.data.AssistChatRepository`**:
  - Interface e implementação responsável por inicializar o `GenerativeModel` com system prompt contextualizado e expor `enviarMensagemStream(chat, prompt): Flow<String>`.
- **`io.signallq.app.assist.ui.AssistChatViewModel`**:
  - Gerencia o estado da sessão de chat, injeta o contexto da última medição no início, despacha mensagens para o repositório, acumula os chunks de streaming de forma progressiva e trata cancelamento e erros.
- **`io.signallq.app.assist.ui.AssistChatScreen`**:
  - Tela Material 3 com Header (fechar sessão), Card de Última Medição, Chips de Perguntas Frequentes, lista com balões de conversa (rolagem automática para nova resposta) e campo de texto fixo no rodapé com `imePadding`.
- **`AppShellDiagnosticoGuiadoOverlay.kt`**:
  - Ponto de acoplamento no shell: quando o overlay `DiagnosticoGuiado` é exibido, renderiza `AssistChatScreen` com o contexto da medição atual.

## 4. Segurança, Privacidade e Falhas

- **Privacidade:** Nenhum dado sensível de identificação de rede pessoal (BSSID, IP público exato, MAC) é injetado no prompt. Apenas métricas de desempenho e características físicas da interface.
- **Falha de Rede / App Check:** Se o streaming falhar ou App Check rejeitar, exibe feedback amigável no balão do assistente com opção "Tentar novamente", sem travar a interface.
- **Cancelamento:** Se o usuário fechar a tela ou disparar ação durante a geração, o job da coroutine é cancelado imediatamente, cessando o consumo de rede/tokens.

## 5. Testes e Critérios de Aceite

- Testes de ViewModel cobrindo:
  1. Inicialização com e sem contexto de medição;
  2. Envio de mensagem com agregação de chunks de streaming;
  3. Tratamento de erro de rede / exceção de IA;
  4. Cancelamento de resposta em andamento;
  5. Limpeza de sessão ao encerrar.
- Testes unitários do formatador de contexto do prompt.
- Build, ktlint, detekt e verificação de integridade sem regressões.
