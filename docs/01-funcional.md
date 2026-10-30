---
title: "SignallQ Consumer — Documentação Funcional"
description: "O que o app Android SignallQ (io.signallq.app) entrega ao usuário final: navegação, mapa de features, permissões e limitações transversais. O detalhe por feature vive em docs/features/."
type: "funcional"
status: "ativo"
owner: "Marcelo"
last_updated: "2026-10-04"
version: "2.2.0"
---

- **Fonte de verdade:** o código do app consumer em `android/app/src/main/kotlin/io/signallq/app/`
  (package `io.signallq.app`), com os módulos `:core*` e `:feature*` consumidos por ele. Referências
  no texto apontam arquivo e símbolo, sem número de linha (o `AppShell` foi fatiado e as linhas deslocam a cada
  mudança). Reescrito em 2026-08-06 lendo o código; revalidado em 2026-10-04 contra a v1.0.9 (Wi-Fi Casa, status ao vivo da Início, sonda UDP do Modo gamer).
- **Escopo:** app consumer Android `io.signallq.app` — telas, navegação, funcionalidades,
  permissões e limitações visíveis ao usuário final.
- **Fora do escopo:** SignallQ Pro (descontinuado permanentemente, ver ADR-016), painel
  Admin (repositório `buildea-admin`), site/PWA (repositório `signallq-web`) e arquitetura interna
  (ver `docs/03-especificacao-tecnica.md`).
- **Responsável:** Claudete (documentação funcional).
- **Onde está o detalhe:** comportamento, regras, estados e mapa de código de cada feature vivem em
  [`features/`](features/README.md) (uma página por feature). Aqui ficam só navegação, permissões,
  limitações transversais e o mapa de ponteiros da seção 5.
- **Versão validada:** versionName `1.0.9`, versionCode `89` (`android/gradle/libs.versions.toml:5-6`);
  `applicationId = "io.signallq.app"` (`android/app/build.gradle.kts:89`).

---

## 1. Objetivo

O SignallQ é um app Android gratuito que mede a internet do usuário (velocidade, latência, jitter,
estabilidade, bufferbloat), analisa a rede em volta dele (Wi-Fi, canais, rede móvel, dispositivos
conectados, DNS, equipamento de fibra) e traduz tudo em veredito humano e próximo passo concreto —
sem exigir que a pessoa saiba interpretar Mbps, dBm ou milissegundos. Toda decisão de veredito vem
de um motor local determinístico rodando no aparelho; a IA remota só escreve a explicação em prosa
do que o motor já concluiu, nunca decide o status.

---

## 2. Contexto e problema

Quando a internet está ruim, o usuário residencial não tem como saber onde está a falha: pode ser o
Wi-Fi do cômodo, o canal congestionado do prédio, o roteador, a ONT da operadora, o DNS, a rede
móvel ou o próprio provedor. Os apps de velocímetro entregam números crus e param aí — o usuário
fica com "42 Mbps" e nenhuma conclusão. O resultado prático é que ele não consegue nem melhorar a
própria rede, nem abrir um chamado embasado com a operadora.

O SignallQ ataca esse vazio combinando três coisas na mesma jornada: medição real feita no aparelho,
um motor de diagnóstico determinístico que classifica cada dimensão e escolhe a causa dominante, e
uma camada de linguagem simples (rótulos, explicação por IA, ações recomendadas) que diz o que
fazer. O app é deliberadamente honesto sobre os próprios limites — rotula estimativa como
estimativa, recusa declarar vencedor em empate técnico de DNS e diz explicitamente quando não
conseguiu medir algo.

---

## 3. Personas e casos de uso

**Usuário residencial não técnico** — é o público-alvo padrão. Abre o app quando algo está ruim
("está lento", "o vídeo trava") e quer uma resposta em português. Usa: teste de velocidade →
resultado com veredito → diagnóstico guiado por objetivo.

**Usuário técnico / entusiasta** — quer o número cru além do veredito: RSSI por BSSID, ocupação de
canal, RSRP/SINR do chip, potência óptica Rx/Tx da ONT, latência por servidor DNS. Usa: aba Sinal
(as três abas), Detalhes técnicos, Equipamento de internet, DNS, Ping, Dispositivos.

**Jogador** — não quer saber de Mbps genérico; quer saber se dá para jogar aquele jogo específico
naquele aparelho. Usa: Modo gamer.

**Usuário em conflito com a operadora** — precisa de documento. Usa: Laudo (relatório de diagnóstico
exportável em PDF) e exportação do histórico (CSV/PDF).

Casos de uso cobertos hoje: medir velocidade e entender o resultado; descobrir a causa provável de
um sintoma específico; escolher canal Wi-Fi; ver quem está na rede local; ler o status da ONT de
fibra; comparar servidores DNS; avaliar a conexão para um jogo; consultar e exportar histórico;
deixar a conexão monitorada em segundo plano com alertas.

---

## 4. Navegação

Não existe Navigation Compose graph. `AppShellNavigation.kt` mantém a raiz selecionada e uma pilha
de overlays independente por raiz, salva por `rememberSaveable`; `AppShell.kt` faz apenas o wiring
das telas e callbacks existentes. O estado restaurado sobrevive à recriação do processo sem mover
regras de negócio ou ViewModels para a shell.

### 4.1 Barra inferior — jornada única

`AppShellBottomBar.kt` implementa uma única navegação com quatro raízes. O aplicativo sempre abre em
`Inicio2Screen`; não existe flag, fallback ou segunda barra de navegação.

| Índice compatível | Rótulo | Tela | Jornada única |
|---|---|---|---|
| 0 | Início | `Inicio2Screen` | cold start |
| 1 | Velocidade | `SpeedTestScreen` | raiz |
| 2 | Histórico | `HistoricoScreen` | raiz; back volta para Início |
| 3 | Ferramentas | `FerramentasScreen` | raiz; nunca bloqueada por feature flag |

Na jornada única, a barra fica oculta em qualquer overlay e durante a execução do speedtest. Trocar de
raiz preserva a pilha da raiz anterior; Voltar desempilha apenas a raiz atual. Ajustes, que contém
a edição do Perfil, é acessado pela ação da app bar nas quatro raízes. Sinal, Wi-Fi e canais continuam disponíveis por Ferramentas
e pelos atalhos contextuais da Início. Os nomes de analytics das superfícies mantidas foram
preservados (`home`, `speedtest`, `historico` e `ferramentas`).

### 4.2 Overlays

Lista exata de `AppShellOverlay` (`AppShellNavigation.kt`) — 19 valores, todos empilháveis (a coluna "Tela" é o nome técnico; o slug de produto está na seção 5):

| Valor do enum | Tela renderizada | Aberto a partir de |
|---|---|---|
| `Laudo` | `LaudoScreen` | Ferramentas; Ajustes; CTA "Executar diagnóstico" no Equipamento de internet |
| `Ping` | `PingScreen` | Ferramentas; SpeedTestScreen |
| `Privacidade` | `PrivacidadeScreen` | Perfil; Ajustes |
| `Novidades` | `NovidadesScreen` | Perfil; Ajustes |
| `ResultadoVelocidade` | `ResultadoVelocidadeScreen` | automático ao concluir um teste (`AppShell.kt`); link "Ver resultado" em Velocidade |
| `Fibra` | `EquipamentoInternetScreen` | nó do gateway na Início; linha do roteador em Ajustes (`onAbrirGatewayDetalhe`, `AppShell.kt`) |
| `Dispositivos` | `DispositivosScreen` | Ferramentas; CTA dentro do Equipamento de internet |
| `EquipamentoConectar` | `EquipamentoConectarScreen` | card "Equipamento de internet" quando ainda não há endereço do roteador salvo (#1806): mostra o que foi detectado, o catálogo de modelos compatíveis e o formulário de conexão; ao conectar (ou "Pular por enquanto") troca para `EquipamentoInternet` |
| `EquipamentoInternet` | `EquipamentoInternetScreen` | Ferramentas (card "Equipamento de internet") |
| `Ferramentas` | `FerramentasScreen` | apenas o card contextual do diagnóstico guiado (`onAbrirFerramentaSugeridaOverlay`, `AppShell.kt`) — a aba 4 usa a tela direto, sem passar por este overlay |
| `Dns` | `DnsScreen` | Ferramentas; SpeedTestScreen |
| `Perfil` | — | não é um overlay separado; a edição fica dentro de Ajustes |
| `Ajustes` | `AjustesScreen` | ação de perfil na app bar das quatro raízes; preserva conexão, monitoramento e dados locais existentes |
| `SinalCanais` | `SinalScreen` | Ferramentas; Wi-Fi, canais e rede móvel em fluxo profundo 2.0 |
| `SinalWifi` | `WifiCasaScreen` | Ferramentas (card "WiFi Casa"; o nome técnico do overlay não mudou) |
| `Termos` | `TermosDeUsoScreen` | Perfil; Sobre o SignallQ |
| `DiagnosticoGuiado` | `DiagnosticoGuiadoScreen` | CTA "Descobrir o que está acontecendo" no resultado do teste e CTA principal da Início; não exige medição anterior. Regras em [`assist-diagnostico`](features/assist-diagnostico.md) |
| `DetalhesTecnicos` | `DetalhesTecnicosScreen` | CTA "Ver detalhes da conexão" no resultado do teste |
| `ModoGamer` | `ModoGamerScreen` | 3 entradas: card "Modo Jogos" em Ferramentas, CTA no resultado do teste, botão no resultado do diagnóstico guiado (objetivo "Jogos atrasam ou travam") |

Notas de comportamento:

- `Fibra` e `EquipamentoInternet` renderizam **a mesma** `EquipamentoInternetScreen`
  (os dois blocos `AnimatedVisibility` em `AppShell.kt` que compõem `EquipamentoInternetScreen`) — são dois pontos de entrada históricos para o mesmo
  destino, não duas telas.
- Back físico desempilha um overlay por vez (`AppShellBackHandlers`, `AppShellNavigation.kt`); fechar `Laudo` por back conta
  como "laudo fechado" para elegibilidade do prompt de avaliação da Play Store.
- O z-order de desenho segue a posição real na pilha, não a ordem no arquivo
  (`rememberOverlayZIndex`, `AppShell.kt`).
- `ResultadoVelocidade` e `DetalhesTecnicos` sem resultado de speedtest em memória mostram um estado
  indisponível (`ResultadoIndisponivelScreen`, GH#1714), não ficam em branco. `DiagnosticoGuiado` pode
  começar sem resultado anterior: a rota `Analise` produz o que falta.
- Uma tela **existe no diretório mas não é roteada**: `MinhaConexaoScreen.kt` — seu conteúdo é
  consumido como bottom sheet dentro de `AjustesScreen`, não como destino próprio.


### 4.3 Acesso ao perfil e Início

Não há menu lateral nem tela intermediária de Perfil: a ação da app bar abre diretamente Ajustes, onde a
edição do nome fica na seção Perfil. `Inicio2Screen` é a única Home. Regras e estados em
[`inicio-status`](features/inicio-status.md) (Hero, trilha de nós, status ao vivo, regra do nó mesh,
ciclo single-flight do diagnóstico) e [`perfil-ajustes-legal`](features/perfil-ajustes-legal.md).

### 4.4 Hub de Ferramentas

`FerramentasScreen.kt`. A jornada única apresenta os nove destinos de `CatalogoFerramentas.todos`
como lista aberta, em um toque, sem grid ou catálogo visual concorrente:

| Card | Descrição exibida | Destino |
|---|---|---|
| Wi-Fi e rede móvel | "Veja o sinal e os canais da sua rede" | `SinalScreen` em `Overlay.SinalCanais` |
| WiFi Casa | "Mapeie o sinal em cada cômodo e compare antes e depois de mudar o roteador" | `WifiCasaScreen` |
| Quem está usando sua rede | "Veja os aparelhos conectados" | `DispositivosScreen` |
| Seu equipamento | "Veja o estado do modem ou da ONT" | `EquipamentoInternetScreen` |
| Tempo de resposta | "Veja se há atraso até um endereço" | `PingScreen` |
| Abertura de sites | "Compare servidores que ajudam a encontrar sites" | `DnsScreen` |
| Relatório para sua operadora | "Gere um resumo completo da conexão" | `LaudoScreen` |
| Acompanhar conexão | "Receba alertas quando algo mudar" | `MonitoramentoSheet` |
| Jogos online | "Veja se sua conexão pode causar atrasos" | `ModoGamerScreen` |

Quando o usuário chega ao hub pelo card contextual do diagnóstico guiado, a ferramenta apontada
recebe o prefixo textual "Recomendado para você" — que é limpo ao sair da tela, nunca fica
estático. Permissão ausente navega para a superfície que explica/solicita a permissão; flag remota
desligada mantém o gate canônico e seu evento `feature_blocked_remote`; offline não abre engine e
informa reconexão como próximo passo; ferramentas ocultas são filtradas antes da composição, sem
UI, semântica, foco ou callback. Uma abertura permitida registra exatamente um `screen_view` pela
taxonomia existente (os dois destinos de sinal usam o nome canônico `sinal_wifi`); remoto, offline
e oculto não registram abertura. Nenhuma dessas condições produz affordance inerte. O placement nativo de Jogos permanece no mesmo destino funcional.

### 4.5 Sheets modais fora da pilha

Controlados por flag local no `AppShell`, não empilhados: `MonitoramentoSheet`, `DadosLocaisSheet`,
`GatewayConnectionSheet` (credenciais do equipamento), `SimpleInfoSheet` (ajuda), `SobreSheet`, mais
dois diálogos — `ForaDoWifiDialog` (aviso de consumo em rede móvel, `AppShell.kt`) e
`DiagnosticoConectividadeDialog` (speedtest interrompido por Wi-Fi sem internet,
`AppShell.kt`).

### 4.6 Antes do shell: onboarding e consentimento

`rotaInicialApp` (`RotaInicialApp.kt`, consumida em `MainActivity.kt`) decide, nesta ordem: tela vazia
enquanto o DataStore não responde; `OnboardingScreen` se não concluído; `LgpdConsentDialog` se não há
resposta de LGPD; só então o `AppShell`. Onboarding de 1 tela, aceite dos Termos e permissões
contextuais: [`perfil-ajustes-legal`](features/perfil-ajustes-legal.md).

### 4.7 Bloqueio remoto de rotas

Nove módulos do consumer podem ser desligados remotamente por Firebase Remote Config
(`ConsumerFeatureModuleIds`, `AppShellFeatureGating.kt`): home, speedtest, wifi, devices, dns,
fibra, diagnostico, history, settings. **Todas as flags nascem ligadas** (fail-open,
`consumer-catalog.json`). Com a flag desligada, a aba fica não clicável e o overlay não abre — o
usuário vê o snackbar neutro "Recurso temporariamente indisponível." (`AppShell.kt`). Ferramentas
(hub), Privacidade e Termos nunca passam pelo gate, por decisão explícita de não esconder obrigação
legal.

---


---

## 5. Funcionalidades por domínio

Cada feature tem uma página em `features/` com negócio (problema, regras, estados, próximo passo) e
técnico (mapa de código, dados, eventos, flags, falhas, testes). Esta tabela liga a antiga numeração
desta seção às páginas.

| Antiga seção | Feature | Nome técnico / entrada | Página |
|---|---|---|---|
| 5.1 | Velocidade e resultado | `SpeedTestScreen`, `VelocidadeScreen`, `ResultadoVelocidade` | [`velocidade-resultado`](features/velocidade-resultado.md) |
| 5.2, 5.3 | Wi-Fi, canais e sinal móvel | `SinalScreen` (`Overlay.SinalCanais`) | [`wifi-canais-sinal`](features/wifi-canais-sinal.md) |
| 5.13 | WiFi Casa | `WifiCasaScreen` (`Overlay.SinalWifi`) | [`wifi-casa`](features/wifi-casa.md) |
| 5.4 | Quem está usando sua rede | `DispositivosScreen` | [`dispositivos-rede`](features/dispositivos-rede.md) |
| 5.6 | Seu equipamento (modem/ONT) | `EquipamentoInternetScreen`, `EquipamentoConectarScreen` | [`equipamento-internet`](features/equipamento-internet.md) |
| 5.4b, 5.5 | Tempo de resposta (Ping) e DNS | `PingScreen`, `DnsScreen` | [`dns-ping`](features/dns-ping.md) |
| 5.5b, 5.7 | SignallQ Assist e diagnóstico offline | `DiagnosticoGuiadoScreen`, `DetalhesTecnicosScreen` | [`assist-diagnostico`](features/assist-diagnostico.md) |
| 5.11 | Jogos online (Modo gamer) | `ModoGamerScreen` | [`modo-gamer`](features/modo-gamer.md) |
| 5.8, Laudo de 5.7 | Histórico e relatório para a operadora | `HistoricoScreen`, `LaudoScreen` | [`historico-laudo`](features/historico-laudo.md) |
| 5.9 | Acompanhar conexão (monitoramento) | `MonitoramentoSheet` | [`monitoramento-alertas`](features/monitoramento-alertas.md) |
| 5.12 | Início | `Inicio2Screen` | [`inicio-status`](features/inicio-status.md) |
| 5.10, 4.6 | Ajustes, perfil, privacidade, termos, onboarding, LGPD | `AjustesScreen`, `OnboardingScreen` | [`perfil-ajustes-legal`](features/perfil-ajustes-legal.md) |

Índice gerado com arquivos, flags e eventos por feature: [`features/INDEX.md`](features/INDEX.md).
Este documento não tinha glossário; os rótulos ao usuário de cada feature estão nas respectivas páginas.

---

## 6. Permissões

Todas declaradas em `android/app/src/main/AndroidManifest.xml:4-19`. **Nenhuma permissão bloqueia o
uso do app** — a ausência oculta ou degrada o dado dependente, nunca produz tela de erro.

| Permissão | Para quê | Se negada |
|---|---|---|
| `INTERNET` | Todo o produto: speedtest, DNS, IP público, IA remota, analytics | Normal (concedida na instalação, não é runtime) |
| `ACCESS_NETWORK_STATE` | Detectar tipo de conexão (Wi-Fi/móvel/Ethernet), validação de internet, capabilities | Normal (não é runtime) |
| `ACCESS_WIFI_STATE` | Ler a rede conectada (SSID, RSSI, link speed, banda) e listar redes vizinhas | Normal (não é runtime) |
| `ACCESS_FINE_LOCATION` | Exigência do Android para ler `ScanResult`/`WifiInfo`: listar redes vizinhas e analisar canais. Pedida de forma contextual, na primeira entrada na aba Wi-Fi/Canal (issue #1671) | Abas Wi-Fi e Canal mostram sheet contextual e banner; sem ela não há varredura de redes nem análise de canal. Bloqueio permanente troca o CTA por "Abrir ajustes do Android" |
| `ACCESS_COARSE_LOCATION` | Pedida junto com a anterior no mesmo grupo, no mesmo ponto de uso contextual | Mesmo efeito acima |
| `NEARBY_WIFI_DEVICES` (`neverForLocation`, API 33+) | Pedida junto com localização (mesmo ponto de uso — issue #1671): identificar aparelhos na rede local sem usar localização | Descoberta de dispositivos fica degradada; a tela Dispositivos continua abrindo |
| `READ_PHONE_STATE` | Coletar operadora, tecnologia (4G/5G), RSRP/SINR/banda/cellId do chip. Pedida de forma contextual, na primeira entrada na aba Móvel (`AndroidManifest.xml`, issue #1671) | Aba Móvel mostra estado vazio explicativo e sheet contextual ("Não acessamos chamadas, mensagens ou dados pessoais") |
| `POST_NOTIFICATIONS` (API 33+) | Alertas do monitoramento passivo e de dispositivo novo na rede. Pedida de forma contextual, no momento em que o usuário liga o monitoramento (issue #1671) | O monitoramento continua medindo e gravando no histórico, mas nenhum alerta chega ao usuário |
| `CHANGE_WIFI_MULTICAST_STATE` | Descoberta de dispositivos por mDNS na varredura da rede local | Não determinado nesta revisão qual é o comportamento degradado exato |

Fora do manifesto, existe um consentimento de **LGPD** exibido depois do onboarding
(`MainActivity.kt`): a coleta de analytics nasce desabilitada e só é ligada quando o consentimento é
positivo (`SignallQApplication.kt`).

---


## 7. Limitações conhecidas

Limitações específicas de uma feature estão em "Fora de escopo" e "Riscos" da página dela. Aqui ficam só
as transversais.

### 7.0 Contrato central de anúncios nativos

Os cinco slots existentes permanecem `VELOCIDADE`, `RESULTADO`, `DISPOSITIVOS`, `HISTORICO` e
`JOGOS`; não há anúncio na Início. `NativeAdLoadState` distingue inelegível por flag ou
consentimento, loading, fill, no-fill, erro recuperável e offline. Somente `Fill` produz UI:
loading/no-fill/erro/offline ocupam zero espaço e nunca bloqueiam conteúdo ou CTA.

`rememberNativeAdState` preserva uma sessão por chave estável de slot/configuração, evita novo
request por recomposição e destrói o `NativeAd` quando a composição sai ou a chave muda. Não há
cache global nem preload: um fill pertence somente ao lifecycle do placement que o solicitou. Os
cinco callsites (`ResultadoVelocidadeScreen.kt`, `DispositivosLista.kt`, `HistoricoScreen.kt`,
`ModoGamerConfigResultadoSection.kt`, `SpeedTestScreen.kt`) usam `rememberNativeAdState`
diretamente; o wrapper `rememberNativeAd` (API mais simples que colapsava todo o contrato num
único `NativeAd?` nulo) foi removido na issue #1785 por não ter mais consumidor de produção.

Remote Config continua fail-safe desligado e `canRequestAds` da UMP precede todo request. O estado
sem consentimento mantém a funcionalidade integral. Sinais contextuais continuam limitados ao slot
e vocabulário fechado sanitizado, sem SSID, IP, identificador de dispositivo ou texto livre.

Não existe consumidor autorizado de paid event/revenue no contrato atual. A especificação
preliminar seria `anuncio_receita_registrada` (`valor_micros: Long`, `moeda: String`,
`precisao: String`), disparada uma vez pelo callback pago do SDK e nunca em request/load/impressão.
Como não há tracker/contrato aprovado para esse dado, o evento **não foi implementado**; não se
inventou backend, conversão monetária ou propriedade identificadora.

**O app não altera nada no sistema.** Não troca o DNS, não muda canal do Wi-Fi, não reconfigura o
roteador. Ele mede, classifica e orienta.

**A IA não diagnostica.** A explicação por IA é uma camada de prosa sobre uma decisão que o motor
local já tomou. Se o serviço remoto falhar, o veredito continua válido e o app diz isso. Não existe
chat livre nem conversa multi-turno.

**Métricas rotuladas como estimativa.** "Falhas estimadas na conexão" é taxa de timeout de probes
HTTP, não perda de pacotes IP; o Ping mede latência HTTPS, não ICMP. A topologia Wi-Fi é estimada por
fabricante e sinal; com confiança abaixo de alta o app fica em silêncio em vez de afirmar incerteza.

**Sobre métricas de sucesso.** Este documento não define KPIs de negócio. O que existe no código são
eventos de analytics: `app_aberto`, `app_session_start`, `app_session_end`, `screen_view`,
`feature_used`, `feature_crash`, `feature_blocked_remote`, `battery_snapshot`,
`speedtest_iniciado`, `speedtest_concluido`, `diag_iniciado`, `diag_concluido`,
`ia_laudo_solicitado`, `ia_laudo_recebido`, `analytics_outbox_delivery`, e a família
`recommendation_*` (`eligible`, `shown`, `clicked`, `dismissed`, `feedback`,
`fallback_ad_shown`). Fontes: `analytics/FirebaseAnalyticsHelper.kt`,
`analytics/FirebaseAnalyticsTracker.kt`, `analytics/AnalyticsOutboxFunnelTracker.kt`,
`core/recommendation/.../RecommendationAnalytics.kt`.

---

## 8. Fora de escopo

- **SignallQ Pro** — produto **descontinuado permanentemente** (ADR-016). Nada do Pro é
  descrito aqui, mesmo quando uma ferramenta do consumer é declarada no código como "versão contida"
  de um recurso que existiu no Pro (caso do Sinal WiFi / Walk Test).
- **Painel Admin** — repositório `buildea-admin`. O worker `signallq-admin-worker` é deste
  repositório, mas o painel que o consome não é.
- **Site e PWA** — repositório `signallq-web`.
- **Arquitetura interna, contratos e engines** — ver `docs/03-especificacao-tecnica.md`,
  `docs/02-arquitetura.md` e `docs/CONTRATOS/`.
- **Design system e tokens** — ver `docs/04-design-system.md`.
