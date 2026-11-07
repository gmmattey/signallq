# Plano de correção — SignallQ Windows Desktop

**Base:** [auditoria E2E de 15/10/2026](E2E_AUDIT_2026-10-15.md).  
**Objetivo:** fazer do Desktop uma superfície confiável de **coleta, medição, exploração e histórico** da conexão, com dados reais, proveniência e instalador verificável. A interpretação guiada, o Assist e a consulta com IA ficam principalmente no app mobile.  
**Estado deste documento:** plano aceito pelo Luiz com reaproveitamento Android obrigatório; não autoriza deploy, publicação ou custo novo.

## Enquadramento

**Impacto:** sistêmico. **Risco:** alto. **Gate Camillo:** sim — speedtest central, contrato de medição/SQLite e integração das fontes de evidência. Camillo revisou a direção para planejamento. **Responsáveis:** Claudete (comportamento/aceite), Marcelo (medição e evidência de rede/gamer), Camillo (contratos e arquitetura), Tiago (validação independente). A implementação Windows/Rust requer responsável técnico explícito na execução; Rian permanece responsável natural pelo Android quando vetores ou compatibilidade Android precisarem mudar.

O plano usa entregas verticais. Cada etapa termina com uma ação observável pelo usuário e evidência em Windows. Build verde, binário gerado ou tela com dados simulados não encerram uma etapa.

## Regra obrigatória de reaproveitamento Android

O [inventário Android → Desktop](REUSE_AUDIT.md#revisão-após-aceite-do-plano--2026-10-15)
é entrada de cada etapa. Antes de criar um componente Windows, a implementação deve apontar o
módulo Android equivalente, o comportamento/contrato que será reaproveitado, a diferença imposta
pelo Windows e os testes de paridade. A ausência de reuso precisa de justificativa concreta.

- **Reuso direto:** contratos de evidência, nomes/unidades, estados de medição, códigos de falha,
  vetores e casos de teste, assets e copy factual compatível.
- **Adaptação:** executor de velocidade, amostragem estatística, payload da sonda gamer,
  sondagens leves de monitoramento e exportação. O I/O é reimplementado em Rust/Windows e validado
  contra fixtures comuns; o comportamento já caracterizado no Android é a referência.
- **Específico da plataforma:** coleta por APIs Windows, processo/tray, agendamento e SQLite
  Desktop. Kotlin, `ConnectivityManager`, Room, DataStore, WorkManager, Compose e tokens do
  `BuildConfig` não são executados nem copiados para o instalador.

Paridade significa mesmos estados, unidades, proveniência e tratamento de falhas para as mesmas
entradas, sem exigir números de Mbps idênticos entre pilhas de rede distintas. Marcelo revisa a
semântica e Tiago verifica fixtures e execução real. O Desktop não duplica thresholds ou regras
de diagnóstico do Android.

## Etapa 0 — Linha de base e execução reproduzível

**Entrega:** identificar sem ambiguidade qual binário está em execução e tornar falhas observáveis.

- Registrar versão, identificador do build e origem do executável no app/diagnóstico local; distinguir Tauri da PWA Chrome.
- Exercitar primeira instalação, abertura, fechamento, reinício, atualização e desinstalação do NSIS em ambiente Windows de teste, preservando o histórico nas atualizações.
- Instrumentar erros de comandos Tauri e HTTP por fase, sem registrar SSID, IP ou payload pessoal em logs. Expor ao usuário mensagem curta com opção de tentar de novo; manter detalhe técnico local para QA.
- Registrar caso de referência da auditoria: HTTP 403 no download de 15 MB, 6 registros `PARTIAL`, download ausente.

**Aceite:** o time reproduz a falha a partir do instalador e consegue apontar fase, status HTTP, endpoint, duração e bytes transferidos, sem depender de leitura manual do SQLite. Nenhum teste passa usando apenas a página Vite fora do Tauri.

## Etapa 1 — Velocidade confiável (P0, independente do NDS)

**Entrega:** o usuário obtém medição de download, upload e latência com status honesto, ou recebe uma falha explicada e recuperável.

- Substituir a transferência única rígida de 15 MB por plano de amostragem com duração/volume mínimos, aquecimento e contagem de bytes efetivamente recebidos/enviados. Definir limites de dados e prazo total para evitar consumo ilimitado.
- Usar fallback controlado para HTTP 403/429, aproveitando o comportamento já caracterizado no Android (`ExecutorSpeedtestCloudflare`: payload de 10 MB, menos streams). Não tratar qualquer resposta 200 ou download curto como Mbps válido sem amostra suficiente.
- Separar erro de transporte, resposta HTTP, timeout, rede trocada e amostra insuficiente; conservar proveniência por fase. Fazer cancelamento cooperativo e atualização de progresso da UI.
- Preservar `null` para métricas não medidas. Usar um status de integridade único, compatível com os casos Android `COMPLETE`, `PARTIAL`, `INCONCLUSIVE`, `CONTAMINATED` e `CANCELLED`. O status descreve a integridade da coleta, sem pontuar a qualidade da conexão.
- Medir latência/jitter/perda estimada de forma caracterizada; manter rótulo de sondagem HTTP. Só apresentar atraso sob carga quando houver probes concorrentes válidos.
- Migrar o SQLite de forma aditiva/versionada e manter legíveis os seis registros antigos; nenhum `NULL` histórico vira zero.
- Antes de fechar o executor Rust, rodar vetores compartilhados com `ExecutorSpeedtestCloudflare`, `MeasurementStatus` e `AnalisadorAmostragemPing` do Android; documentar diferenças de transporte justificadas.

**Aceite:** medição completa repetível em ao menos duas redes reais e Windows 10/11, com valor plausível frente a uma referência independente e diferença explicada; 403/429, bloqueio de upload, offline, timeout, cancelamento e troca de rede resultam em estados próprios. Retestar na mesma rede atualiza resultado e histórico. Testes automatizados cobrem respostas HTTP e vetores de status, sem exigir rede pública na suíte unitária.

## Etapa 2 — Evidência da rede e ferramentas (P1)

**Entrega:** Minha rede mostra a interface realmente usada ou explica precisamente o que o Windows não expõe; ferramentas terminam e mostram resultado verificável.

- Criar adaptador tipado para interface ativa, IP local, gateway, DNS e tipo Ethernet/Wi-Fi. Para Wi-Fi, coletar SSID/sinal/canal apenas quando API, driver e permissão permitirem. Tratar VPN, múltiplos adaptadores, IPv4/IPv6, desconexão e troca de rede sem combinar campos de interfaces distintas.
- Usar o snapshot do início e do fim para detectar teste contaminado por troca de rede. Não enviar inventário local a serviço remoto por padrão.
- Dar prazo total e cancelamento real a Ping e Rota, incluindo encerramento do processo filho; limitar saída. DNS deve diferenciar resposta, NXDOMAIN/falha e timeout. Verificar cada comando acionado pela UI empacotada.
- Construir sequência local mínima para falta de conectividade (gateway → resolução DNS → rota/host externo), apresentando o resultado de cada sondagem e seus limites, sem atribuir causa raiz.
- Usar os contratos e a ordem de sondagens de `android/core/network/connectivity/` como referência; adaptar somente a captura da interface e o vínculo das sondagens à rede Windows.

**Aceite:** rede Wi-Fi e Ethernet corretas em máquinas reais; VPN e múltiplos adaptadores não misturam dados; indisponibilidade continua `null`; Ping/DNS/Rota encerram dentro do prazo e exibem saída/erro coerente. O fluxo sem Internet não pede speedtest impossível nem afirma causa raiz além da evidência observada.

## Etapa 3 — Gamer, monitoramento e histórico (P1)

**Entrega:** as telas permitem investigar e comparar dados reais, sem converter uma sondagem isolada em veredito sobre a conexão ou sobre um jogo.

- **Gamer:** permitir escolher jogo/região do catálogo válido, executar sondagem em rota de referência aprovada e mostrar latência, variação, falhas estimadas, horário e destino. Identificar quando a rota não representa servidor oficial. Sem endpoint confiável, mostrar indisponibilidade específica, sem usar o ping de `1.1.1.1` como substituto de um jogo.
- **Monitoramento:** continuar pela bandeja após minimizar, por decisão do Luiz; fechar o aplicativo encerra a sessão. Usar agendamento nativo de probes leves de disponibilidade/estabilidade em vez de transferências completas de velocidade a cada 15 minutos. Persistir preferência, amostras e interrupções observadas; controlar consumo e avisar ao usuário.
- **Histórico:** detalhar status, método, destino, amostras e motivo de falha; filtrar e comparar medições da mesma rede quando identificável. Mostrar valores e diferenças medidas, sem afirmar causa ou melhora por uma única amostra. Incluir exportação local por ação explícita do usuário com métricas, horários, SSID, IP e identificador da rede, como autorizado pelo Luiz; identificar esses campos no arquivo e não enviá-lo automaticamente. Incluir remoção/retenção local.
- **Contrato de evidência:** manter DTO versionado com `executionId`, horário, unidade, método, origem, destino, duração, bytes/amostras, estado e erro por fase. Não incluir dados indisponíveis como zero. Isso preserva a possibilidade de uso futuro pelo mobile sem criar uma integração agora.
- Validar a sonda gamer contra `SondaGameLiftBeaconTest`, a semântica de monitoramento contra `MonitoramentoWorker*Test` e a exportação contra `ExportadorHistoricoCSVTest`, com fixtures equivalentes para Rust/Desktop.

**Aceite:** Gamer não se resume a ping `1.1.1.1`; monitoramento mantém exatamente o comportamento anunciado após minimizar/reabrir e interrompe quando desligado; histórico permite entender por que um teste foi parcial e exportar os dados que o Desktop realmente coletou.

## Etapa 4 — Interface, instalador e liberação (P1)

- Revisar a linguagem da interface: a seção hoje chamada “Diagnóstico” deve se tornar **Dados da conexão** (ou equivalente factual), com métricas, origem, horário e limites de coleta. Remover classificações locais por thresholds da Visão geral e qualquer promessa de causa, score ou confiança diagnóstica.
- Retirar Assist da navegação principal do Desktop ou apresentá-lo somente como recurso do app mobile, com indicação clara dessa plataforma. Não integrar NDS/IA no escopo desta correção.
- Revisar todos os controles do protótipo: ativar Busca/Ctrl K ou remover a promessa visual; alinhar textos ao estado real das funções; garantir teclado, foco, leitor de tela e escalas 100%, 125% e 150%.
- Automatizar E2E no Windows com aplicativo empacotado: primeira execução, medição, erro HTTP, cancelamento, ferramentas, troca de rede simulada/fixture, histórico após reinício e atualização. Executar smoke manual em redes reais e em Windows 10/11.
- CI: testes Rust/TypeScript, formatação/lint, build e teste do instalador. A assinatura e o canal de distribuição são decisões separadas antes de publicação pública.

**Aceite final:** cada tela apresenta dados verificáveis, ação real e limites explícitos; nenhuma CTA promete diagnóstico/Assist no Desktop. Instalador limpo, atualização e remoção passam; Tiago registra evidência independente do fluxo completo; Camillo revisa aderência dos contratos; Luiz aprova qualquer deploy/publicação.

## Dependências e ordem de trabalho

```text
Etapa 0 → Etapa 1 → Etapa 2 → Etapa 3 → Etapa 4
          medição    rede       exploração  E2E/instalador
```

NDS, autenticação desktop, Assist e IA ficam fora do caminho crítico. Se o produto decidir incluir interpretação no Desktop no futuro, essa iniciativa exigirá novo escopo e os gates da ADR-017/019. O único desbloqueio rápido da falha de 403 é uma fatia da etapa 1; ele não encerra a correção dos demais dados e ferramentas.

## Decisões de produto a fechar antes da execução dependente

1. **Retenção local:** definir prazo padrão e controle de remoção do histórico; até essa decisão, preservar os registros existentes e permitir exclusão explícita.
2. **Distribuição:** instalador privado para testes, instalador assinado ou Microsoft Store? Publicação e assinatura não são parte automática deste plano.

Essas escolhas não impedem a correção do speedtest, das ferramentas e da coleta de rede.
