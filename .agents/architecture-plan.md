# Architecture Plan — trabalho corrente

## Status de sites e aplicativos — consumidor da API Linka

O SignallQ consome somente o contrato público de leitura `v1/service-status` já operado pelo Linka (catálogo e incidentes). A escolha de cada serviço e a revisão já notificada ficam locais; `WorkManager` consulta o feed em rede disponível e emite uma notificação Android apenas para incidentes novos ou revisados. O estado externo não alimenta score, finding ou recomendação do diagnóstico da conexão. Falha da API mantém escolhas e mostra atualização indisponível, nunca “operando normalmente”. Não há escrita na API Linka, token push, segredo ou mudança de infraestrutura nesta fatia.

> Use somente quando o gate arquitetural do `AGENTS.md` for acionado. Camillo mantém este artefato curto e proporcional à mudança.

## Problema

O resultado do Assist não informa se sua explicação veio de inferência de IA, do catálogo validado ou de fallback determinístico. A tela também exibe confiança, embora isso não ajude a pessoa a decidir o próximo passo.

## Comportamento esperado

O resultado mostra, de modo discreto e apenas quando conhecido: “Explicação por IA · [modelo público]”, “Explicação validada do Assist” ou “Baseado nas regras do diagnóstico”. Não mostra confiança. Resultado local só é chamado de local quando a fonte de avaliação for realmente local.

## Arquitetura atual relevante

O NDS V1 contém `explanation_source`, `fallback_used` e `ai_model_used` no módulo `ai`. O V2 responde `{raw, explanation}`, mas o Android preserva `raw` genericamente e não projeta essa procedência para `DiagnosticReport`.

## Decisão

Adicionar `explanation.provenance` opcional ao V2, com `source` fechado (`ai`, `copy_catalog`, `deterministic`) e `model_label` público opcional. O Worker deriva o rótulo por allowlist; nunca repassa `ai_model_used` bruto. Android converte V1/V2 para um tipo de domínio opcional em `DiagnosticReport`; a UI não lê JSON bruto.

## Impacto

- Módulos/repositórios: NDS, `:core:nds`, `:core:diagnostico` e `:app`.
- APIs/contratos: extensão aditiva de `explanation.provenance` no V2.
- Fluxo de dados: NDS decide procedência → resposta V1/V2 → mapper tipado → `DiagnosticReport` → indicador opcional.
- Persistência/migração: nenhuma.
- Segurança/privacidade: `model_label` allowlisted; não expor configuração, erro, tokens ou nome bruto do modelo.
- Compatibilidade/fallback: clientes antigos ignoram o campo; ausência não renderiza indicador; fallback remoto não é tratado como aparelho.

## Testes e validação

NDS: contratos V2 para IA, catálogo e fallback, com rótulo desconhecido omitido; OpenAPI regenerado. Android: parser/mapper V1/V2, ausência/malformação segura e composição do indicador; regressão de falha remota do Assist. Validação visual em dispositivo quando disponível.

## Riscos

Nomes de modelos podem expor detalhe operacional ou mudar. O Worker usa allowlist e o campo é opcional. Rollout parcial não quebra clientes porque a procedência é aditiva e sua ausência é silenciosa.

## Não-objetivos

Não muda motor determinístico, severidade, recomendação, persistência de histórico nem a exigência de resposta remota no Assist.

## TP-Link Archer C6/A6 — acesso LAN e leitura real

### Problema e comportamento esperado

O catálogo classifica o Archer C6/A6 como `LAB_VALIDATED`, mas o único `GatewayConnectionService` de produção retorna `Indisponivel`; a única leitura real é a da ONT Nokia. O Archer C6 conectado deve aceitar somente a senha que a sua UI administrativa pede (o usuário de protocolo é internamente `admin`), autenticar de fato e produzir um `LocalNetworkDeviceSnapshot` normalizado de roteador — WAN, LAN, rádios Wi-Fi e clientes, sem fibra. A ONT Nokia continua acessível e legível pelo seu fluxo atual; nenhuma credencial do C6 pode ser aplicada à ONT, ou vice-versa.

### Arquitetura atual relevante

- `:core:network` já é a fronteira dos contratos de gateway, catálogo/fingerprint e `LocalNetworkDeviceSnapshot`; este último foi desenhado para receber TP-Link, mas hoje só `NokiaLocalDeviceMapper` o preenche.
- `:feature:fibra` contém o cliente, sessão e parser específicos da Nokia. Ele não é uma casa semântica para o protocolo de roteador TP-Link.
- A UI chama `GatewayConnectionService`, mas `AppShell` injeta o default indisponível. O formulário exige `Usuário`, embora o firmware `tplink-stok-luci` use `admin` fixo.
- `CredenciaisModemStore` cifra usuário, senha e BSSID, mas mantém apenas um perfil global. Na topologia observada há C6 (`192.168.0.1`) atrás da Nokia (`192.168.1.x`), portanto um único perfil pode sobrescrever o outro.

### Decisão

1. Criar `:feature:router` para o driver TP-Link, sem mover nem reescrever o driver Nokia. O módulo depende de `:core:network` e expõe uma fachada pequena de acesso/leitura; `:app` só orquestra drivers e consome os contratos normalizados.
2. Implementar `TpLinkStokLuciClient` e `TpLinkArcherMapper` nesse módulo. O cliente reproduz exclusivamente o handshake confirmado no field-map (`keys → auth → login`, RSA PKCS#1 v1.5, AES-CBC/PKCS7, cookie `sysauth` e `stok`) e limita a primeira fatia a operações de leitura. O mapper lê `status?form=all`, mais as leituras necessárias de Wi-Fi/OneMesh, e gera `LocalNetworkDeviceSnapshot` com `DeviceType.ROUTER`, `fiber = null`, capabilities reais e warnings tipados para campos parciais.
3. Estender o contrato de acesso em `:core:network` para separar a exigência de credencial da tentativa de login: `PASSWORD_ONLY` (rótulo TP-Link, usuário interno `admin`) e `USERNAME_AND_PASSWORD` (Nokia/desconhecido). A resolução é uma sondagem sem credencial, limitada a IP privado/local e às assinaturas conhecidas; modelo só é promovido a C6/A6 após a leitura autenticada de `get_deviceInfo`/OneMesh. Equipamento desconhecido não recebe senha automaticamente.
4. `:app` injeta um despachante real no lugar do `GatewayConnectionServiceIndisponivelPadrao`: TP-Link reconhecido usa o driver novo; Nokia preserva o caminho existente de `ExecutorFibra`; erro de fingerprint retorna `Indisponivel`/falha amigável sem tentativa de senha. A UI mostra só “Senha” quando o perfil resolvido for TP-Link e não persiste/exibe `admin` como dado digitado.
5. Trocar o armazenamento global por perfis cifrados, indexados por `driverId + host` e vinculados opcionalmente ao BSSID. Migrar o perfil legado uma única vez como `legacy`; após primeira identificação bem-sucedida, reclassificá-lo para o driver/host correspondente. Conservar o perfil Nokia durante a adição do C6. Sessões HTTP ficam apenas em memória e são invalidadas em troca de BSSID, desconexão, erro de autenticação e encerramento do processo.

### Contratos e fluxo

`UI → resolução não autenticada → requisito de login → driver selecionado → sessão efêmera → leitura normalizada → LocalNetworkDeviceSnapshot → UI/filtro seguro`.

O snapshot bruto não ganha senha, `stok`, `sysauth`, chave/IV AES, payload criptografado nem `psk_key`; esses valores não entram em logs, analytics, IA, banco ou mensagens de erro. Para IA/analytics permanece obrigatório `LocalDeviceSafeFilter`.

O resultado de acesso mantém `Sucesso`, `Falha` e `Indisponivel` para consumidores atuais, mas a implementação deve ter causas internas fechadas (host inválido, não suportado, credencial inválida, sessão expirada, timeout, resposta inválida) traduzidas uma vez para cópia segura. `Sucesso` só ocorre depois de login confirmado e de uma leitura autenticada mínima; nunca por socket aberto ou por reconhecimento do IP.

### Compatibilidade, falhas e rollback

- Nokia não passa pelo crypto TP-Link e continua usando `NokiaModemClient`/`ExecutorFibra`; o despachante escolhe por fingerprint, nunca pelo IP canônico isoladamente.
- Firmware TP-Link que responde à família mas não confirma C6/A6 fica `PARSER_IMPORTED`/não suportado para login automático nesta fatia; não recebe o selo de validado por semelhança.
- Timeout, portal cativo, 401/login rejeitado, `stok` expirado, cookie ausente e JSON/AES inválido encerram a sessão e deixam os dados anteriores intactos, com mensagem amigável e nova tentativa manual possível.
- Rollback é retirar o binding do despachante novo: Nokia e o formulário legado continuam operando; perfis cifrados adicionais permanecem inertes e não são apagados automaticamente.

### Testes e validação

- Unidade hermética (fixtures/MockWebServer): handshake completo, `admin` interno sem campo de UI, cifra/decifra, URL/assinatura, cookie+stok, credencial incorreta, token expirado, timeout e resposta malformada sem vazar segredo.
- Mapper: Archer C6 e alias A6 v2, WAN/LAN, 2,4/5 GHz, OneMesh/clientes, campos ausentes, exclusão explícita de `psk_key`, `fiber = null` e capabilities corretas.
- Contrato/app: seleção TP-Link/Nokia/desconhecido, formulário password-only, perfis C6 e Nokia coexistentes, migração do perfil legado, BSSID divergente, `Sucesso` apenas após leitura e regressão dos fluxos Nokia.
- Em hardware: com o C6 conectado, validar login com somente a senha, leitura real de modelo e ao menos WAN/LAN/Wi-Fi; repetir leitura da Nokia na mesma rede para comprovar que os dois perfis não cruzaram. Breno cobre Wi-Fi real, troca de rede, app em background e revisão de logs/armazenamento.

### Riscos e não-objetivos

O firmware stok-luci não é um padrão estável entre modelos; o suporte validado desta fatia é C6/A6 v2 observado, não toda a família TP-Link/Mercusys. Não haverá alteração de configurações, reboot, leitura/exposição de senha Wi-Fi, descoberta ativa ampla, envio de dados brutos ao backend nem promoção de outros modelos a `LAB_VALIDATED`.

## Modo gamer — validade da medição e reteste

### Problema e decisão

Hoje `ModoGamerViewModel` mede somente o ping específico e avalia o restante contra o `DiagnosticInput` disponível. Esse input prefere o resultado em memória, mas pode cair silenciosamente na última `MedicaoEntity`, sem idade ou identidade de rede. Além disso, `ModoGamerScreen` chama `analisarProblema()` ao entrar no resultado.

O veredito gamer só poderá usar uma medição de speedtest concluída, não contaminada, de no máximo 15 minutos e com `networkId` igual ao da rede atual. O ping específico da rota vale no máximo 2 minutos e pertence à mesma tentativa gamer. Sem esses requisitos, não há veredito: a tela mostra o CTA “Fazer um teste rápido para jogar” e, ao toque, inicia o speedtest automaticamente; conserva jogo e aparelho e retorna ao resultado apenas depois da nova medição e do ping específico. A IA não será disparada automaticamente nesse fluxo.

### Arquitetura e responsabilidades

- `:core:database`: reutilizar `MedicaoEntity.timestampEpochMs`, `status` e `networkId`; adicionar uma consulta específica, limitada por tempo/rede/status, em vez de fazer o Modo gamer escolher `observarUltimas(1)`.
- `:app`/`MainViewModel`: oferecer uma operação gamer explícita que resolve a elegibilidade e aciona o pipeline canônico `reiniciarSuite(ModoSpeedtest.fast)`. Não chamar `solicitarDiagnostico()`: ela só reavalia os dados disponíveis e não inicia um speedtest.
- `:app`/`AppShell` e `ModoGamerScreen`: transportar o estado da medição gamer e a ação do CTA sem navegar para a aba Velocidade; o `ModoGamerViewModel` mantém a seleção atual durante espera, falha e retorno.
- `:app`/`ModoGamerViewModel`: receber uma evidência gamer tipada (medição-base identificada + ping com timestamp), nunca um `DiagnosticInput` sem proveniência temporal. A IA deixa de ser dependência do resultado gamer; o veredito vem do `ModoGamerEngine`.

### Falhas, compatibilidade e testes

`networkId` nulo, mudança de rede, medição vencida, contaminada, parcial/inconclusiva ou falha/cancelamento do speedtest não produzem “Bom pra jogar”; preservam jogo/aparelho e oferecem tentar de novo. A confirmação já existente para rede medida continua antes de qualquer teste que possa consumir dados. Nenhuma migração é necessária: medições antigas com `networkId = null` simplesmente não são reutilizáveis.

Davi implementa UI, navegação de estado e testes do ViewModel; Ramon define/valida a elegibilidade da evidência e a integração com o speedtest. Cobrir: 14:59 vs 15:00, mesma rede vs rede trocada/nula, status inválidos, ping com 1:59 vs 2:00, CTA que mantém seleção, término/falha/cancelamento e ausência de chamada à IA. Breno valida em aparelho Wi-Fi e rede medida, incluindo troca de rede durante o teste.

### Riscos e não-objetivos

O principal risco é disparar só o ping ou só uma reavaliação e rotulá-los como novo speedtest; a operação gamer deve aguardar uma execução nova identificada antes de concluir. Não mudamos os thresholds do `ModoGamerEngine`, o histórico como recurso de consulta, nem o contrato NDS/Worker; esta fatia apenas impede seu uso silencioso como veredito atual.

## Offline sem transporte — cópia, CTA e falhas de speedtest

### Problema e comportamento esperado

No cenário sem SIM e sem Wi-Fi, a aba Sinal afirma que há “internet do chip”; o CTA genérico do banner inicia um diagnóstico que só é válido para Wi-Fi e encerra como falha; e exceções internas do speedtest (`download_failed`/`UnknownHostException`) chegam cruas às duas superfícies de Velocidade. O app deve dizer somente que não há conexão ativa, não executar sondagens Wi-Fi quando não existe transporte Wi-Fi e nunca mostrar exceção, hostname ou protocolo ao usuário.

### Arquitetura atual e decisão

- `SinalScreen` escolhe o estado vazio de Wi-Fi apenas por não haver Wi-Fi e fixa a cópia de rede móvel; ela não distingue `movel` de `desconectado`.
- `SignallQOfflineBanner` abre por padrão `DiagnosticoOfflineDialog`; este usa `DiagnosticoOfflineExecutorReal`, cujo contrato é explicitamente Wi-Fi (gateway/DNS/rota/portal) e retorna `SEM_REDE_WIFI` como falha quando não há rede Wi-Fi.
- `ExecutorSpeedtestCloudflare` publica `Throwable.message` em `SnapshotExecucaoSpeedtest.erroMensagem`. `SpeedTestScreen`, `VelocidadeScreen` e `estadoAnaliseGuiada` a consomem diretamente.

Adicionar ao contrato de `:feature:speedtest` uma causa de falha fechada e própria para apresentação (ao menos sem conexão, DNS/hostname inacessível, timeout e falha genérica), mantendo o detalhe técnico somente em log/telemetria. O executor mapeia a exceção uma única vez; as três superfícies recebem a cópia por esse tipo, nunca por `erroMensagem`. Manter `erroMensagem` temporariamente como detalhe interno/compatível até migrar todos os consumidores e então impedir seu uso em UI.

O banner recebe contexto explícito de transporte. Sem Wi-Fi ativo, o CTA não abre o diagnóstico Wi-Fi: exibe orientação curta para conectar-se a uma rede ou tentar de novo quando houver conexão. Com Wi-Fi ativo sem internet, preserva o diagnóstico local existente. A aba Sinal usa a mesma distinção: “internet móvel” somente em `EstadoConexao.movel`; em `desconectado`, estado neutro de sem conexão.

### Impacto, compatibilidade e falhas

- Módulos: `:feature:speedtest` (causa/mapeamento), `:app` (shell/UI do banner, Sinal, Velocidade e fluxo guiado); sem Worker, API remota, banco ou migração.
- O `SnapshotExecucaoSpeedtest` é contrato entre feature e app; a extensão deve ser aditiva, com fallback seguro para snapshots antigos/test doubles sem causa tipada.
- Não confundir ausência de transporte com Wi-Fi conectado sem internet: o primeiro não produz diagnóstico causal; o segundo mantém as sondagens determinísticas e seu nível de confiança.
- Rollback: a causa desconhecida sempre exibe a cópia genérica; a remoção do diagnóstico Wi-Fi sem Wi-Fi é local e reversível.

### Testes e riscos

Cobrir unitariamente o mapeador de exceções (incluindo `UnknownHostException` embrulhada no prefixo `download_failed`), `estadoAnaliseGuiada` e as duas telas contra mensagem pública, sem texto técnico. Cobrir Compose para Sinal em móvel versus desconectado e CTA do banner em: sem transporte (não abre o diálogo), Wi-Fi sem internet (abre e executa o fluxo existente) e Wi-Fi válido. Rodar os testes focados de `:feature:speedtest` e `:app`, depois `test`, `ktlintCheck`, `detekt` e `assembleDebug`; validar em aparelho real sem SIM/Wi-Fi e em Wi-Fi conectado sem internet.

Risco principal: inferir “offline” apenas pelo erro de DNS e esconder uma falha específica de Wi-Fi. A classificação deve usar o estado de conectividade no momento da tentativa e deixar DNS/hostname como causa pública distinta quando houver transporte. Não altera thresholds, motor de diagnóstico, persistência, Worker ou comportamento de rede móvel medida.

## Modo gamer — medição real de rota contra infraestrutura do jogo (UDP)

### Gate de produto antes do gate técnico

Antes de qualquer arquitetura: o comentário de origem do `game-latency-probe-worker` (GH#935) registra uma decisão deliberada — "NENHUMA lógica de jogo, autenticação ou estado aqui, de propósito, para o dado nunca ser confundido com 'ping real da partida'". A proposta desta fatia é o inverso dessa decisão: medir handshake UDP real contra a infraestrutura pública do próprio jogo (AWS GameLift, Valve A2S, Azure PlayFab QoS), como o LagCheck (iOS, mesmo portfólio) já faz. Isso muda a promessa feita ao usuário — de "estimativa regional" para "medição real contra o provedor do jogo" — e usa infraestrutura de terceiro (Amazon/Valve/Microsoft) sem contrato do SignallQ com esses provedores. Essa reversão de promessa e o uso de infra de terceiro exigem aprovação explícita do Luiz (AGENTS.md §5 item 9 "integração entre produtos Buildea" e §10 "infraestrutura que crie custo/exposição" tratam de casos adjacentes; o caso central aqui é mudança de promessa de produto, que é decisão do Luiz, não arquitetural). Este plano cobre a arquitetura **caso a decisão de produto seja aprovada** — não é autorização para implementar.

### Problema e comportamento esperado

O Modo gamer hoje avalia "dá pra jogar" com métricas HTTPS genéricas (latência via `PingExecutor` contra `game-latency-probe-worker`, que é sonda regional sem lógica de jogo) ou com as métricas do speedtest geral. Nenhuma delas mede a rota real até a infraestrutura do jogo específico. O comportamento proposto: quando o jogo selecionado tiver protocolo de sondagem conhecido e documentado (GameLift, A2S, PlayFab), o usuário pode pedir uma medição real da rota até essa infraestrutura, como refinamento opcional — nunca obrigatório, nunca bloqueia o fluxo padrão, mesmo contrato que `pingEspecificoMs` já segue hoje.

### Arquitetura atual relevante

- `ModoGamerEngine.avaliar()` (`:core:diagnostico`) já tem o ponto de extensão certo: `pingEspecificoMs`/`jitterMs`/`perdaPercentual` substituem a leitura de latência do `input` sem mudar contrato de `ResultadoModoGamer` nem a tela (`ModoGamerEngine.kt:39-93`).
- `PingExecutor` (`:feature:speedtest`) mede round-trip HTTPS, não handshake de protocolo de jogo; documenta explicitamente que Android não concede `CAP_NET_RAW` para ICMP bruto — mas isso não bloqueia UDP comum (`DatagramSocket`), que não exige privilégio elevado.
- `CatalogoJogosModoGamer` já tem `specificProbeHost` para 4 jogos (Valorant, CS2, LoL, Dota2) — hoje não consumido por nenhum client real além do host regional genérico; é o esqueleto certo para carregar host/protocolo por jogo.
- `game-latency-probe-worker` é HTTP puro (`GET/HEAD /probe` → `204`), não serve de proxy para handshake UDP — UDP não atravessa esse Worker.
- O catálogo remoto de jogos existente (`signallq-diagnostic-worker/src/game-catalog.ts`, D1) guarda perfis de threshold (`GameProfileRecord`) do fluxo legado "Jogos" (GH#935), hoje não consumido pelo Modo gamer (que usa thresholds fixos do `ModoGamerEngine`). Não reaproveitar esse catálogo sem revisão de Ramon — ele carrega vocabulário e números do motor aposentado.

### Decisão proposta

1. Novo módulo `:core:probeJogo` (nome sujeito a revisão de Davi na convenção de módulos), dependência de `:core:network`, contendo um client UDP mínimo por protocolo (GameLift ping beacon, Valve A2S_INFO, PlayFab QoS) via `DatagramSocket`. Cada protocolo mede apenas round-trip de um handshake público documentado — nunca autentica, nunca entra em partida, nunca envia dado do usuário. Timeout curto e janela fixa de amostras (mesmo padrão do `ProbeAcceptancePolicy` do LagCheck: plano fixo, sem fallback silencioso de protocolo).
2. `CatalogoJogosModoGamer` ganha um campo fechado opcional `sondaRota: SondaRotaJogo?` (host, porta, protocolo) só para os jogos com endpoint público confirmado e documentado por Ramon — começar pelos 4 que já têm `specificProbeHost`. Jogo sem sonda continua exatamente como hoje (sem regressão).
3. `ModoGamerViewModel`/`ModoGamerConfigResultadoSection` ganham uma ação opcional "Medir rota real até o servidor do jogo" ao lado da existente "Medir ping específico agora" — dispara o client do novo módulo, produz `pingEspecificoMs`/`jitterMs`/`perdaPercentual` pelo mesmo caminho que já existe, com uma evidência adicional identificando a fonte como "medição real de rota" (distinta da evidência atual de `pingEspecificoMs`, que não deve ser confundida com esta). Nenhuma mudança em `ModoGamerScreen.kt` (scaffold) nem no contrato de `ResultadoModoGamer`.
4. Alternativas rejeitadas: (a) estender `game-latency-probe-worker` para proxyar a sondagem — descartada, UDP não atravessa Cloudflare Worker HTTP sem Spectrum (custo novo, já fora de escopo por decisão anterior do próprio Worker); (b) reaproveitar `GameProfileRecord`/D1 do catálogo legado como fonte de threshold — descartada, é vocabulário do motor aposentado (`JogoConexaoEngine`), reintroduzi-lo contraria a fusão da issue #1487.

### Contratos, segurança e custo

Nenhum novo Worker, nenhuma credencial, nenhum dado do usuário trafegado — a sondagem é IP/porta público do provedor de infraestrutura do jogo, mesmo modelo do LagCheck. Não é "novo fornecedor com custo recorrente" (AGENTS.md §10): os endpoints (GameLift, A2S, PlayFab) são públicos e gratuitos, sem contrato do SignallQ com Amazon/Valve/Microsoft — mas justamente por não haver contrato, a lista de hosts é fechada, curada e documentada por Ramon, nunca descoberta dinamicamente. Lista de hosts fica em `:core:probeJogo`, nunca hardcoded solta em `:app`.

### Compatibilidade, falhas e rollback

Ausência de `sondaRota` no catálogo preserva o comportamento atual (jogo sem sonda nunca oferece a nova ação). Timeout, resposta malformada ou protocolo sem resposta produzem "sem dados" nessa medição específica — nunca fallback para latência HTTPS travestida de medição real, nunca trata timeout como sucesso (regra §8 do AGENTS.md). Rollback é remover a entrada de UI que dispara a ação nova; o resto do Modo gamer continua idêntico.

### Testes e validação

Unidade hermética por protocolo (fixtures de handshake GameLift/A2S/PlayFab, resposta válida/inválida/timeout, sem vazar payload). Regressão do `ModoGamerEngine` com e sem a nova evidência, garantindo que jogos sem `sondaRota` não regridem. Teste de rede real em device (Wi-Fi e móvel) para confirmar que `DatagramSocket` UDP funciona nas condições de OEM/firewall observadas — risco conhecido de operadoras/roteadores bloquearem UDP de saída para portas não padrão, precisa validação de Breno antes de qualquer rollout.

### Riscos e não-objetivos

Risco técnico principal: bloqueio de UDP por operadora móvel, CGNAT ou firewall doméstico é comum e indistinguível de indisponibilidade real do provedor — a UI deve comunicar isso como incerteza, nunca como "servidor do jogo fora do ar". Risco de produto: qualquer normalização dessa medição como "ping real da partida" (em vez de "rota até a infraestrutura pública do provedor") repete o erro que o `game-latency-probe-worker` foi desenhado para evitar — a cópia da evidência precisa deixar isso explícito, revisão de Cora obrigatória antes do rollout.

Não-objetivos desta fatia: não importa o `GameEditorialRegistry` do LagCheck (notas, requisitos de sistema) — é conteúdo editorial de outro produto, sem curadoria própria do SignallQ; não altera thresholds do `ModoGamerEngine`; não substitui a estratégia regional existente (`game-latency-probe-worker` continua como está); não cobre todos os 21 jogos do catálogo, só os que tiverem endpoint público documentado.

**Status: aguardando aprovação do Luiz sobre a mudança de promessa de produto antes de qualquer implementação por Davi/Ramon.**
