---
title: "Feature — DNS e Ping (Tempo de resposta)"
description: "Ferramentas DNS (benchmark DoH de resolvedores) e Ping (latência HTTPS): regras, estados, mapa de código, eventos, flags e testes."
type: "feature"
status: "ativo"
owner: "Camillo"
last_updated: "2026-10-04"
version: "1.1.0"
feature: "dns-ping"
tipo: "transversal"
modulos:
  - "android/feature/dns"
  - "android/feature/speedtest"
  - "android/core/network"
  - "android/core/diagnostico"
  - "android/core/featureflags"
  - "android/app"
arquivos:
  - "android/feature/dns/src/main/kotlin/io/signallq/app/feature/dns/"
  - "android/feature/speedtest/src/main/kotlin/io/signallq/app/feature/speedtest/PingExecutor.kt"
  - "android/app/src/main/kotlin/io/signallq/app/ui/screen/DnsScreen.kt"
  - "android/app/src/main/kotlin/io/signallq/app/ui/screen/PingScreen.kt"
  - "android/app/src/main/kotlin/io/signallq/app/ui/screen/AppShellDnsOverlay.kt"
  - "android/app/src/main/kotlin/io/signallq/app/ui/screen/AppShellPingOverlay.kt"
  - "android/core/network/src/main/kotlin/io/signallq/app/core/network/connectivity/"
contratos: []
eventos:
  - "feature_used (feature_id=dns)"
  - "screen_view (screen_name=dns | ping)"
  - "feature_blocked_remote (feature_id=dns)"
flags:
  - "consumer_dns_enabled"
  - "feature_dns"
testes:
  - "android/feature/dns/src/test/kotlin/io/signallq/app/feature/dns/AvaliadorRecomendacaoDnsTest.kt"
  - "android/feature/dns/src/test/kotlin/io/signallq/app/feature/dns/BenchmarkDnsDohTest.kt"
  - "android/feature/dns/src/test/kotlin/io/signallq/app/feature/dns/DetectorEnderecoIpPrivadoTest.kt"
  - "android/feature/speedtest/src/test/kotlin/io/signallq/app/feature/speedtest/PingExecutorTest.kt"
  - "android/app/src/test/kotlin/io/signallq/app/DnsScreenTest.kt"
  - "android/app/src/test/kotlin/io/signallq/app/ui/screen/DnsResumoTest.kt"
  - "android/app/src/test/kotlin/io/signallq/app/ui/screen/PingScreenViewModelTest.kt"
  - "android/core/diagnostico/src/test/kotlin/io/signallq/app/core/diagnostico/DnsDiagnosticEngineTest.kt"
  - "android/core/network/src/test/kotlin/io/signallq/app/core/network/connectivity/DnsReachabilityProbeTest.kt"
adrs: []
thresholds_em: "android/feature/dns/src/main/kotlin/io/signallq/app/feature/dns/AvaliadorRecomendacaoDns.kt"
---

# Feature — DNS e Ping

Migrado de `FUNCIONAL.md` (§5.4b e §5.5), `ARQUITETURA/MODULOS/feature-dns.md`, `core-network.md` e `core-diagnostico.md`. Fatos conferidos no código em 2026-10-04.

## Negócio

### 1. Problema e promessa

Usuário com "site lento" ou "internet instável" não sabe se o culpado é a velocidade, o atraso até o destino ou o DNS. As duas ferramentas respondem separadamente: **Tempo de resposta (Ping)** — "veja se há atraso até um endereço"; **DNS** — "compare servidores que ajudam a encontrar sites" (títulos em `FUNCIONAL.md` §4). Ambas lideram com significado, não com o nome do protocolo ("Tempo de resposta" em vez de "Teste de Latência"; "DNS afeta a abertura de sites, não a velocidade da sua conexão"). **O app não troca o DNS**: mede, classifica e orienta.

### 2. Quando aparece e para quem

Telas cheias roteadas (`DnsScreen`, `PingScreen`), abertas como overlay pelo hub **Ferramentas**; o DNS também abre a partir de Velocidade. Para qualquer usuário, sem permissão de runtime além de `INTERNET`. O DNS sugere-se quando o motivo é "sites lentos" ou velocidade abaixo do plano; o Ping, quando a internet está instável (`TipoFerramenta.kt`, `PlanoDeAnalise.kt`).

### 3. Regras de decisão

- **Ping:** latência **HTTPS**, nunca ICMP; o resultado declara "Via HTTPS · <host>". Destino padrão `speed.cloudflare.com`, sempre visível. "Testar outro endereço" é opção avançada, escondida por padrão (decisão de produto do Luiz, 2026-08-19). Host digitado passa por `DetectorEnderecoIpPrivado` (RFC 1918, loopback, link-local, ULA IPv6 são recusados antes de qualquer teste). `destinoContextual` opcional existe, mas hoje nenhum chamador o preenche (fica `null`).
- **DNS:** compara resolvedores conhecidos, não testa host escolhido pelo usuário (sem opção avançada). Sete provedores públicos via DNS-over-HTTPS: Cloudflare, Google DNS, Quad9, OpenDNS, AdGuard, Control D, CleanBrowsing, mais o DNS do sistema. Cada linha: tempo em ms, nota A/B/C/D, badges "atual"/"mais rápido".
- **Recomendação** (`AvaliadorRecomendacaoDns`): `Vencedor`, `EmpateTecnico` ou `SemDadosSuficientes`. Valores de margem de empate e taxa de sucesso mínima: ver `thresholds_em` (não copiados aqui). O benchmark faz 6 rounds por provedor (1 de aquecimento descartado + 5 avaliados), com timeout global de 25 s (`ROUNDS_POR_MEDICAO` e `TIMEOUT_SUITE_DNS_MS` em `BenchmarkDnsDoh.kt`). Em empate, a tela recusa declarar vencedor.
- Quatro blocos na tela DNS: **Seu DNS atual** (latência omitida quando o DNS é o roteador, que só repassa consultas); **Benchmark** (botão "Comparar servidores DNS"); **Recomendação**; **Guia** colapsável "Quando vale a pena trocar DNS?" com passo a passo em duas abas (Dispositivo, 5 passos; Roteador, 6 passos), cada uma declarando o escopo do efeito.

### 4. Estados e honestidade

| Estado | Texto / ação |
|---|---|
| DNS atual é o roteador | Latência omitida; explica que o roteador só repassa as consultas |
| Melhores dentro da margem de empate | "Empate técnico entre os servidores mais rápidos nesta conexão." — sem vencedor |
| Dados insuficientes (`SemDadosSuficientes`) | Não recomenda trocar; não converte ausência em tempo zero |
| Host do Ping privado/local | Recusado antes de qualquer teste |
| Ping: 3 falhas de rede consecutivas | Aborta cedo em vez de esgotar as tentativas (rede caída ≠ instabilidade do destino) |
| Feature DNS bloqueada por flag remota | Rota não abre; evento `feature_blocked_remote` |
| Troca de DNS | "Isso não troca o DNS automaticamente. Para alterar, você precisa configurar no Android ou no roteador." |

### 5. Próximo passo e confirmação

DNS: se há recomendação, o guia mostra como configurar no dispositivo ou no roteador; o app não confirma a troca — o usuário repete "Comparar servidores DNS" para ver o efeito. Ping: refazer o teste. Cancelar (voltar) encerra a coleta via ciclo de vida do Compose.

Reuso: o **diagnóstico offline guiado** (`FUNCIONAL.md` §5.5b) usa a etapa DNS do motor de conectividade e `OrientadorConfiguracaoDns` para recomendar DNS público quando o da rede falha e o público (Cloudflare, DoH) resolve; não recomenda o DNS que a rede já usa.

### 6. Fora de escopo, status e flag

Não troca DNS, não mede ICMP, não persiste resultados do benchmark. Status: entregue. Flag: `consumer_dns_enabled` gateia só o DNS (tab/overlay); o Ping **não** tem flag (`AppShellFerramentasRoot.kt`: `PING -> true`).

## Técnico

### 7. Mapa de código

| Arquivo | Responsabilidade |
|---|---|
| `feature/dns/.../BenchmarkDnsDoh.kt` | Suíte DoH (RFC 8484, GET base64url `application/dns-message`): warm-up + rounds avaliados, timeout global, detecta resolvedor ativo, emite `snapshotFlow` |
| `feature/dns/.../AvaliadorRecomendacaoDns.kt` | Vencedor / empate técnico / dados insuficientes |
| `feature/dns/.../AvaliadorCoerenciaDns.kt` | Janela deslizante de divergências DNS esperado × observado → `NivelAlertaCoerenciaDns` (`none`/`attention`/`critical`) |
| `feature/dns/.../OrientadorConfiguracaoDns.kt` | Primário/secundário/hostname de DNS privado por provedor; `null` quando o ativo já é o melhor e não há alerta de coerência |
| `feature/dns/.../DetectorEnderecoIpPrivado.kt` | IPv4 RFC 1918/link-local/loopback; IPv6 `::1`, `fe80::/10`, `fc00::/7`. Fonte única (substituiu duplicata em `DnsScreen.kt`, GH#1212 item 10) |
| `feature/dns/.../ResultadoBenchmarkDns.kt`, `BenchmarkDns.kt`, `SnapshotBenchmarkDns.kt`, `EstadoBenchmarkDns.kt`, `FeatureDnsModulo.kt` | Contrato por provedor, interface, snapshot/estados (`idle`/`executando`/`concluido`/`erro`), factory `criarBenchmarkDns()` |
| `feature/speedtest/.../PingExecutor.kt` | Motor do Ping (HTTPS; arquitetura, timeouts e erros em [`PING_EXECUTOR_ARCHITECTURE.md`](../technical/PING_EXECUTOR_ARCHITECTURE.md)): 20 tentativas por padrão, timeout global, aborto após falhas consecutivas; também reaproveitado pelo teste de jogo apontando outro `targetUrl` |
| `app/.../ui/screen/DnsScreen.kt`, `PingScreen.kt` | Telas (Scaffold + `CenterAlignedTopAppBar` + Voltar); Ping migrou de `ModalBottomSheet` na issue #1665 (épico #1647), DNS desde GH#933 Fase 4 |
| `app/.../ui/screen/AppShellDnsOverlay.kt`, `AppShellPingOverlay.kt`, `AppShellFeatureGating.kt` | Overlays e gating (`ConsumerFeatureModuleIds.DNS = "dns"`) |
| `core/network/.../connectivity/` (`DnsReachabilityProbe`, `DohFallbackProbe`, `ConnectivityDiagnosisEngine`) | Sonda DNS e fallback DoH do diagnóstico de conectividade (gateway → DNS → rota externa → hostname/captive portal) |
| `core/diagnostico/.../DnsDiagnosticEngine.kt` | Engine determinística de DNS dentro do diagnóstico |

Dependências do módulo `:featureDns`: Android library, `core-ktx`, coroutines, `okhttp` (transporte DoH), `timber`; **nenhuma dependência de projeto** (nem `:core*`, nem `feature`). Único consumidor Gradle: `:app` (`di/AppModule.kt`, `MainViewModel.kt`, `AppShell.kt`, `DnsScreen.kt`). Detalhe em [`feature-dns.md`](../modulos/feature-dns.md).

### 8. Dados e contratos

Sem contrato OpenAPI/schema próprio. `ResultadoBenchmarkDns` por provedor: tempo, amostras, tentativas × tentativas avaliadas, taxa de sucesso, nota A–D, `isGatewayLocal`, `respostaInvalida`. Estado do benchmark é observado direto do `snapshotFlow` pelo `MainViewModel` (sem ViewModel de feature). Ping: ViewModel da tela (`PingScreenViewModelTest`). Nada é persistido.

### 9. Eventos e flags

- `feature_used` com `feature_id="dns"`, disparado em `onDispararBenchmarkDns` (`MainActivity.kt`). O Ping **não** dispara `feature_used`.
- `screen_view` com `screen_name` `dns` e `ping` (`analytics-events-schema.md`).
- `feature_blocked_remote` com `feature_id="dns"` quando a flag bloqueia a rota (`AppShell.kt`).
- Flags: `consumer_dns_enabled` (`FeatureFlagKeys.kt`, Remote Config, gateia tab/overlay); `feature_dns` (default `true` em `FeatureFlagRepository.kt`, legado SIG-13). Detalhe: `docs/technical/feature-flags-remote-config.md`.
- Monitoramento em segundo plano reusa DNS/latência com histerese em `monitoramento/HisteresiHelper.kt` — pertence à feature Monitoramento; valores no próprio arquivo.

### 10. Falhas e fallback

- Benchmark DoH: timeout global da suíte (`TIMEOUT_SUITE_DNS_MS`), resposta inválida marcada em `respostaInvalida`, provedor com baixa taxa de sucesso não é candidato a vencedor.
- Cada round consulta um nome distinto para não cair no cache do resolvedor Android/roteador (KDoc de `BenchmarkDnsDoh.kt`).
- Ping: timeout global e aborto após 3 falhas de rede consecutivas (`PingExecutor.kt`).
- Sondas de conectividade (`DnsReachabilityProbe`): timeout por etapa no motor (`ConnectivityDiagnosisEngine`). O diagnóstico offline para na primeira falha.

### 11. Testes

Lista em `testes:` no frontmatter. Lacuna conhecida: `AvaliadorCoerenciaDns` e `OrientadorConfiguracaoDns` sem teste unitário (lógica pura, trivial de cobrir). `DnsScreen.kt` e `PingScreen.kt` têm teste de ViewModel/resumo, sem teste de UI instrumentado.

### 12. Riscos

- **Margem de empate duplicada:** a constante de 10 ms existe em `BenchmarkDnsDoh.kt` **e** em `AvaliadorRecomendacaoDns.kt` — viola "não duplicar thresholds" (AGENTS.md §8); consolidar numa fonte única.
- `BenchmarkDnsDoh.kt` concentra rede, encoding de pacote DNS, política de rounds e agregação.
- Sem ViewModel de feature: o `MainViewModel` (dívida crítica, `higiene` §4.2) segura o estado do benchmark.
- Regra de negócio perto de Composable: `DnsScreen.kt` é consumidor direto dos resultados; vigiar reincidência de lógica na tela.
- `destinoContextual` do Ping sem produtor — plumbing adiado até existir fonte real de contexto.
