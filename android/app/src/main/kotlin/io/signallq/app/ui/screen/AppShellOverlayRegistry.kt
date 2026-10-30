package io.signallq.app.ui.screen

import androidx.compose.runtime.Composable
import io.signallq.app.core.network.SnapshotRede
import io.signallq.app.core.network.contracts.localdevice.LocalNetworkDeviceSnapshot
import io.signallq.app.feature.dns.SnapshotBenchmarkDns
import io.signallq.app.feature.speedtest.ResultadoSpeedtest

/**
 * Ponto de extensão de overlays do [AppShell] (issue #1695, épico #1647).
 *
 * `AppShell.kt` cresceu em 5 das últimas 6 fatias do épico mesmo com cada uma cumprindo a
 * letra da regra de extração (higiene §4.3). Mas medir de onde vieram as ~226 linhas que essas
 * 5 fatias devolveram ao arquivo mostra que os blocos `AnimatedVisibility` de overlay — a única
 * coisa que este registro resolve — respondem por só ~30-40 linhas (~15%). O resto (100% de
 * wiring de root content em duas fatias, lambda de regra de negócio numa terceira, estado
 * hoisted numa quarta) não passa por aqui e continua sendo risco de crescimento do arquivo — ver
 * `docs/technical/appshell-overlay-registry.md`, seção "O que este registro não resolve".
 * Dentro do que resolve, generaliza o padrão de overlays extraídos (issue #1656) já
 * provou — overlay com Composable e callbacks próprios, em arquivo dedicado — para os demais
 * overlays do [AppShellOverlay.Companion] migrados aqui, e cria o único ponto de agregação: este
 * arquivo.
 *
 * ## Como plugar um overlay novo quase sempre sem editar `AppShell.kt`
 *
 * Cobre apenas overlays empilhados via [AppShellOverlay]/`overlayStack`. Não cobre rota — a
 * navegação entre as raízes (tabs) segue com `when`/`Screen(` inline em `AppShell.kt`, fora do
 * escopo deste registro.
 *
 * 1. adicione o valor em `AppShellOverlay` (`AppShellNavigation.kt`) — continua sendo a
 *    ÚNICA fonte de verdade de quais overlays existem e como a pilha por raiz
 *    (push/pop/back/restauração) se comporta. Este arquivo NÃO compete com aquele: aqui só
 *    se decide "qual Composable desenha o overlay X", não "quando X entra/sai da pilha";
 * 2. crie `AppShellXxxOverlay.kt` com um `@Composable internal fun` que recebe **só o que
 *    precisa** (nunca a lista inteira de parâmetros do [AppShell]) — `overlayStack` mais um
 *    punhado de callbacks/dados estreitos;
 * 3. adicione uma chamada para ele dentro de [AppShellOverlayRegistry] abaixo — a ordem de
 *    declaração aqui NÃO decide o z-index visual (isso é [rememberOverlayZIndex], baseado na
 *    posição real em `overlayStack`; ver KDoc dela em `AppShell.kt`), então a entrada nova pode
 *    ir em qualquer lugar da lista;
 * 4. se o overlay precisar de um dado que `AppShell.kt` **ainda não expõe** (não está em nenhum
 *    dos grupos `AppShellXxxState` de `AppShellState.kt`, `overlayStack`, `navigator` etc.),
 *    esse dado precisa circular por `AppShell.kt` primeiro — repasse-o como parâmetro novo de
 *    [AppShellOverlayRegistry] e no único call site em `AppShell.kt`. Nesse caso o arquivo
 *    central muda uma linha; se o overlay só precisa do que já está exposto, não muda nada.
 *
 * Overlays com estado hoisted fora da pilha (`showMonitoramentoSheet`,
 * `showEquipamentoCredenciaisSheet` etc. — sheets sem back-stack) ficam fora deste registro por
 * enquanto; migrá-los é trabalho futuro das 17 fatias restantes, não escopo desta issue.
 *
 * Migrados nesta issue (7 overlays): diagnóstico guiado e os demais overlays registrados,
 * [AppShellOverlay.Termos], [AppShellOverlay.Novidades], [AppShellOverlay.Privacidade],
 * [AppShellOverlay.DetalhesTecnicos], [AppShellOverlay.SinalWifi], [AppShellOverlay.Ping] e
 * [AppShellOverlay.Dns]. Os demais overlays (Ajustes, Perfil, Ferramentas, Dispositivos,
 * Fibra/EquipamentoInternet, Laudo, SinalCanais, ResultadoVelocidade, DiagnosticoGuiado,
 * ModoGamer) continuam inline em `AppShell.kt` — migração de cada um é responsabilidade de
 * quem tocar essa área numa fatia futura (ver `docs/technical/appshell-overlay-registry.md`).
 */
@Composable
internal fun AppShellOverlayRegistry(
    overlayStack: MutableList<AppShellOverlay>,
    // issue #1720 — só o overlay de diagnóstico guiado precisa do `navigator` (não da lista crua)
    // porque é o único que registra interceptador de back (`RegistrarBackDoOverlay`). Os demais
    // continuam recebendo `overlayStack` — trocar a assinatura deles também não teria nenhum
    // consumidor agora, e a regra de higiene (§11, "código sem consumidor") é exatamente o que
    // esta issue está corrigindo, não repetindo.
    navigator: AppShellNavigator,
    // Termos / Novidades / Privacidade — Perfil (GH#1358).
    appVersion: String,
    onAbrirGerenciarDados: () -> Unit,
    // Detalhes técnicos — pós resultado de velocidade (Feature #550, issue #1475).
    resultadoSpeedtest: ResultadoSpeedtest?,
    localizacaoServidor: String?,
    localDevice: LocalNetworkDeviceSnapshot?,
    onGerarLaudo: () -> Unit = {},
    // Sinal Wi-Fi — hub Ferramentas (GH#1201).
    temPermissaoLocalizacao: Boolean,
    localizacaoBloqueadaPermanentemente: Boolean,
    onSolicitarPermissaoLocalizacao: () -> Unit,
    // Dns — benchmark de resolvedor (GH#933, Fase 4).
    snapshotDns: SnapshotBenchmarkDns,
    dnsResolverIp: String?,
    snapshotRede: SnapshotRede,
    onIniciarBenchmarkDns: () -> Unit,
    // Diagnóstico guiado (issue #1704, fatia 2.0.09b) — PRIMEIRO overlay a entrar no formato de
    // entrada agrupada, cumprindo a ressalva 3 de Caio (PR #1697). Os 24 parâmetros da tela
    // chegam como UM parâmetro; os 17 campos soltos acima são o legado a converter conforme
    // cada overlay for tocado.
    diagnosticoGuiado: AppShellDiagnosticoGuiadoEntry,
) {
    AppShellTermosOverlay(overlayStack = overlayStack)
    AppShellNovidadesOverlay(overlayStack = overlayStack, appVersion = appVersion)
    AppShellPrivacidadeOverlay(overlayStack = overlayStack, onAbrirGerenciarDados = onAbrirGerenciarDados)
    AppShellDetalhesTecnicosOverlay(
        overlayStack = overlayStack,
        resultadoSpeedtest = resultadoSpeedtest,
        localizacaoServidor = localizacaoServidor,
        localDevice = localDevice,
        onGerarLaudo = onGerarLaudo,
    )
    AppShellSinalWifiOverlay(
        overlayStack = overlayStack,
        temPermissaoLocalizacao = temPermissaoLocalizacao,
        localizacaoBloqueadaPermanentemente = localizacaoBloqueadaPermanentemente,
        onSolicitarPermissaoLocalizacao = onSolicitarPermissaoLocalizacao,
    )
    AppShellDiagnosticoGuiadoOverlay(navigator = navigator, entry = diagnosticoGuiado)
    AppShellPingOverlay(overlayStack = overlayStack)
    AppShellDnsOverlay(
        overlayStack = overlayStack,
        snapshotDns = snapshotDns,
        dnsResolverIp = dnsResolverIp,
        snapshotRede = snapshotRede,
        onIniciarBenchmark = onIniciarBenchmarkDns,
    )
}
