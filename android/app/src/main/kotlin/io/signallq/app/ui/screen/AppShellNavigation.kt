package io.signallq.app.ui.screen

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue

internal enum class AppShellOverlay {
    Laudo,
    Ping,
    Privacidade,
    Novidades,
    ResultadoVelocidade,
    Fibra,
    Dispositivos,
    EquipamentoConectar,
    EquipamentoInternet,
    Ferramentas,
    Dns,
    Perfil,
    Ajustes,
    SinalCanais,
    SinalWifi,
    Termos,
    DiagnosticoGuiado,
    DetalhesTecnicos,
    ModoGamer,
}

internal enum class AppShellRoot(
    val index: Int,
) {
    Home(0),
    Speed(1),
    History(2),
    Tools(3),
    ;

    companion object {
        fun fromIndex(index: Int): AppShellRoot = entries.firstOrNull { it.index == index } ?: Tools
    }
}

internal fun AppShellRoot.screenName(): String =
    when (this) {
        AppShellRoot.Home -> "home"
        AppShellRoot.Speed -> "speedtest"
        AppShellRoot.History -> "historico"
        AppShellRoot.Tools -> "ferramentas"
    }

internal fun shouldShowAppShellBottomBar(
    isAtRoot: Boolean,
    speedtestRunning: Boolean,
): Boolean =
    !speedtestRunning && isAtRoot

@Composable
internal fun AppShellBackHandlers(
    navigator: AppShellNavigator,
    onOverlayRemoved: (AppShellOverlay) -> Unit = {},
) {
    BackHandler(enabled = navigator.isAtRoot && navigator.selectedRoot == AppShellRoot.History) {
        navigator.select(AppShellRoot.Home)
    }
    // Registrado por último para ter prioridade LIFO quando uma mudança de estado e a
    // recomposição dos handlers acontecem no mesmo frame.
    BackHandler(enabled = !navigator.isAtRoot) {
        // GH#1704 — o overlay do topo tem a primeira palavra: um fluxo interno de vários passos
        // (o diagnóstico guiado 2.0) precisa recuar UM passo antes de o overlay inteiro sair da
        // pilha. Só quando ele declara que não consumiu é que o navigator faz `pop`.
        //
        // Isto NÃO é um segundo motor de navegação: segue havendo um dispatcher, uma pilha de
        // overlays e um dono do back. O overlay não empilha nada aqui — só responde "consumi" ou
        // "não consumi". Ver `docs/technical/appshell-overlay-registry.md`, seção "Delegação de
        // back".
        if (navigator.consumirBackDoOverlayTopo()) return@BackHandler
        navigator.pop()?.let(onOverlayRemoved)
    }
}

/**
 * Registra um interceptador de back para [overlay] enquanto ele estiver no **topo** da pilha da
 * raiz atual — issue #1704 (2.0.09b), decisão de arquitetura de Caio.
 *
 * `onBack` devolve `true` quando consumiu o evento (recuou um passo interno) e `false` quando o
 * fluxo chegou ao início e o overlay inteiro deve sair.
 *
 * ## Onde chamar — muda o comportamento, não é detalhe
 *
 * Chame no nível do `AppShellXxxOverlay`, **fora** do conteúdo do `AnimatedVisibility`. Dentro do
 * conteúdo (o lugar aparentemente natural, porque é onde a tela mora) o desregistro fica preso à
 * animação de saída, e o overlay continua respondendo pelo back durante a transição. Fora, a troca
 * de topo resolve no mesmo frame.
 *
 * ## Por que "estar no topo" e não "estar na pilha"
 *
 * Duas razões, e a segunda não é óbvia:
 *
 * 1. com Perfil aberto por cima do diagnóstico guiado, o back tem que fechar o Perfil, não recuar
 *    um passo do fluxo escondido atrás;
 * 2. **é este predicado que desfaz o registro** quando o overlay deixa o topo sem sair de
 *    composição — e isso é o caso normal, porque `AppShellOverlayRegistry` compõe todos os
 *    overlays sempre, controlando visibilidade por `AnimatedVisibility`. Sem ele, o mapa acumularia
 *    interceptadores de overlays que não estão mais na pilha. Vale também para troca de raiz:
 *    `overlayStack` lê `selectedTab` e a lista por snapshot, então a recomposição — e o desregistro
 *    — acontecem sozinhos.
 *
 * ## Invariante do mapa: um interceptador por overlay, global
 *
 * A pilha é por raiz, o mapa não. Como `open()` é por pilha, o mesmo [AppShellOverlay] pode existir
 * em Home e em Tools ao mesmo tempo, e a chave é uma só — o último a registrar ganha. Isso não
 * quebra hoje justamente porque o predicado de topo desregistra na troca de raiz. Se algum dia dois
 * fluxos independentes do mesmo overlay precisarem coexistir com estado próprio, a chave vira
 * `Pair<AppShellRoot, AppShellOverlay>`; hoje seria abstração sem consumidor.
 */
@Composable
internal fun RegistrarBackDoOverlay(
    navigator: AppShellNavigator,
    overlay: AppShellOverlay,
    onBack: () -> Boolean,
) {
    // `rememberUpdatedState` em vez de `onBack` na lista de chaves: o interceptador quase sempre
    // captura estado do fluxo, então seria lambda nova a cada recomposição. Mantê-la como chave
    // funciona (o registro fica sempre fresco), mas transfere a armadilha para quem consumir —
    // quem tirasse `onBack` das chaves depois congelaria o interceptador na primeira lambda e
    // travaria o fluxo no passo em que estava, sem erro de compilação. Com o estado atualizado, a
    // primitiva não depende do consumidor acertar. Ressalva R2 de Caio na PR #1710.
    val onBackAtual by rememberUpdatedState(onBack)
    val estaNoTopo = navigator.overlayStack.lastOrNull() == overlay
    DisposableEffect(navigator, overlay, estaNoTopo) {
        if (estaNoTopo) navigator.registrarBackDoOverlay(overlay) { onBackAtual() }
        onDispose { navigator.desregistrarBackDoOverlay(overlay) }
    }
}

@Stable
internal class AppShellNavigator internal constructor(
    initialTab: Int,
    restoredStacks: Map<AppShellRoot, List<AppShellOverlay>> = emptyMap(),
) {
    var selectedTab by mutableIntStateOf(initialTab)

    private val stacks =
        AppShellRoot.entries.associateWith { root ->
            mutableStateListOf<AppShellOverlay>().apply {
                addAll(restoredStacks[root].orEmpty())
            }
        }

    val selectedRoot: AppShellRoot
        get() = AppShellRoot.fromIndex(selectedTab)

    val overlayStack: MutableList<AppShellOverlay>
        get() = stacks.getValue(selectedRoot)

    val isAtRoot: Boolean
        get() = overlayStack.isEmpty()

    fun select(root: AppShellRoot) {
        selectedTab = root.index
    }

    fun open(overlay: AppShellOverlay) {
        if (overlay !in overlayStack) overlayStack.add(overlay)
    }

    fun pop(): AppShellOverlay? = overlayStack.removeLastOrNull()

    /**
     * Interceptadores de back por overlay (GH#1704). Não é uma segunda pilha de navegação: é um
     * mapa de "quem responde por si" consultado ANTES do [pop]. Deliberadamente **não** entra em
     * [saveState]/[restoreState] — lambda não é serializável, e cada overlay re-registra o seu ao
     * recompor. O que precisa sobreviver à recriação de processo é o estado do fluxo, que é
     * responsabilidade de quem o mantém, não deste mapa.
     */
    private val interceptadoresBack = mutableMapOf<AppShellOverlay, () -> Boolean>()

    internal fun registrarBackDoOverlay(
        overlay: AppShellOverlay,
        onBack: () -> Boolean,
    ) {
        interceptadoresBack[overlay] = onBack
    }

    internal fun desregistrarBackDoOverlay(overlay: AppShellOverlay) {
        interceptadoresBack.remove(overlay)
    }

    /**
     * Quais overlays têm interceptador registrado agora.
     *
     * Existe para tornar **assertável** a invariante "só o topo fica registrado" — que é o que a
     * guarda `estaNoTopo` de [RegistrarBackDoOverlay] garante. Descoberto rodando a mutação da
     * ressalva B2 (PR #1710): remover aquela guarda **não muda o comportamento do back**, porque
     * [consumirBackDoOverlayTopo] consulta por chave do topo e um registro de overlay soterrado
     * simplesmente dá miss. O efeito de removê-la é o mapa acumular entradas de overlays que não
     * estão no topo — vazamento silencioso, invisível por qualquer asserção de navegação.
     *
     * Ou seja: sem esta janela, a guarda seria deletável sem nenhum teste vermelho, que é
     * exatamente o que o parecer apontou.
     */
    internal fun overlaysComInterceptador(): Set<AppShellOverlay> = interceptadoresBack.keys.toSet()

    /**
     * Dá ao overlay do topo a chance de consumir o back. `false` quando não há topo, quando o topo
     * não registrou interceptador, ou quando ele declarou que não consumiu — nos três casos o
     * chamador segue para [pop], que é o comportamento que existia antes desta issue.
     *
     * Consulta só o **topo**: um interceptador de overlay soterrado não pode sequestrar o back de
     * quem está por cima.
     *
     * Propriedade que vem de consultar por chave em vez de iterar o mapa: se a pilha mudar e o back
     * chegar antes de a recomposição aplicar o desregistro, o lookup usa o topo **novo** contra um
     * mapa que ainda tem a chave **antiga** — dá miss, cai no `false` e o back segue para o [pop].
     * Ou seja, a janela de corrida degrada para o comportamento anterior à issue, que é o seguro.
     */
    internal fun consumirBackDoOverlayTopo(): Boolean {
        val topo = overlayStack.lastOrNull() ?: return false
        return interceptadoresBack[topo]?.invoke() ?: false
    }

    internal fun snapshotStacks(): Map<AppShellRoot, List<AppShellOverlay>> =
        stacks.mapValues { (_, stack) -> stack.toList() }

    companion object {
        internal fun saveState(navigator: AppShellNavigator): List<String> =
            buildList {
                add(navigator.selectedTab.toString())
                AppShellRoot.entries.forEach { root ->
                    add(navigator.snapshotStacks().getValue(root).joinToString(",") { it.name })
                }
            }

        internal fun restoreState(values: List<*>): AppShellNavigator {
            val selectedTab = values.firstOrNull()?.toString()?.toIntOrNull() ?: 0
            val stacks =
                AppShellRoot.entries
                    .mapIndexed { index, root ->
                        val overlays =
                            values
                                .getOrNull(index + 1)
                                ?.toString()
                                ?.split(',')
                                ?.filter(String::isNotBlank)
                                ?.mapNotNull { name -> AppShellOverlay.entries.firstOrNull { it.name == name } }
                                .orEmpty()
                        root to overlays
                    }.toMap()
            return AppShellNavigator(selectedTab, stacks)
        }

        val Saver: Saver<AppShellNavigator, Any> =
            Saver(
                save = { navigator -> saveState(navigator) },
                restore = { restored ->
                    val values = restored as? List<*> ?: return@Saver null
                    restoreState(values)
                },
            )
    }
}

@Composable
internal fun rememberAppShellNavigator(): AppShellNavigator =
    rememberSaveable(saver = AppShellNavigator.Saver) {
        AppShellNavigator(
            initialTab = AppShellRoot.Home.index,
        )
    }
