package io.signallq.app.ui.screen

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable

// Raiz "Ferramentas" (tab 4) do AppShell — issue #1698, épico #1647.
//
// Primeira das duas raízes migradas para AppShellRootRegistry. Traz junto os dois vetores de
// crescimento que a fatia 2.0.05 devolveu a `AppShell.kt` e que o registro de overlays (#1695)
// não cobria:
//  1. a REGRA DE NEGÓCIO de disponibilidade por ferramenta (~28 linhas de lambda inline) virou
//     `resolverDisponibilidadeFerramenta`, função pura e testável sem Compose;
//  2. os 9 CALLBACKS DE NAVEGAÇÃO viraram `AppShellFerramentasAcoes`, um grupo só — o hub aparece
//     em dois lugares (a tab e o `Overlay.Ferramentas`) e as duas listas de callbacks eram
//     idênticas e duplicadas linha a linha em `AppShell.kt`.
//
// Ver `docs/technical/appshell-root-content-registry.md` para o padrão completo.

/**
 * Os 9 destinos de navegação do hub Ferramentas, agrupados.
 *
 * Grupo em vez de campos soltos por decisão herdada da revisão da PR #1697 (ressalva 3 de Caio):
 * `AppShellOverlayRegistry` chegou a 17 parâmetros e ganhava ~4 por overlay migrado — na mesma
 * trajetória, o registro viraria o próximo monolito. Aqui cada raiz contribui com **um**
 * parâmetro para [AppShellRootRegistry], não com N campos.
 *
 * Consumido pelos dois pontos de entrada do hub (tab 4 e `Overlay.Ferramentas`), que antes
 * repetiam as mesmas 9 atribuições em `AppShell.kt`.
 */
@Stable
data class AppShellFerramentasAcoes(
    val onAbrirSinalCanais: () -> Unit,
    val onAbrirDispositivos: () -> Unit,
    val onAbrirEquipamentoInternet: () -> Unit,
    val onAbrirPing: () -> Unit,
    val onAbrirDns: () -> Unit,
    val onAbrirLaudo: () -> Unit,
    val onAbrirMonitoramento: () -> Unit,
    val onAbrirModoGamer: () -> Unit,
    val onAbrirSinalWifi: () -> Unit,
)

/**
 * Regra de disponibilidade de cada ferramenta do hub — extraída sem alteração da lambda
 * `disponibilidadeFerramenta` que vivia inline em `AppShell.kt` (fatia 2.0.05, +67 linhas
 * medidas na revisão da PR #1697, das quais ~30 eram esta regra).
 *
 * Ordem de precedência preservada exatamente como estava: flag remota desligada vence tudo,
 * depois falta de conexão, depois falta de permissão de localização.
 *
 * Função pura de propósito: é regra de negócio, não composição visual, e o motivo de estar aqui
 * (e não em `core/diagnostico`) é que decide **habilitação de superfície do hub**, não
 * classificação de rede — mesmo critério da regra de higiene §4.8b, item 2.
 */
internal fun resolverDisponibilidadeFerramenta(
    tipo: TipoFerramenta,
    featureFlags: AppShellFeatureFlagsState,
    conectado: Boolean,
    temPermissaoLocalizacao: Boolean,
): FerramentaDisponibilidade {
    val flagEnabled =
        when (tipo) {
            TipoFerramenta.SINAL_CANAIS_MOVEL, TipoFerramenta.SINAL_WIFI -> featureFlags.wifiEnabled
            TipoFerramenta.DISPOSITIVOS -> featureFlags.devicesEnabled
            TipoFerramenta.EQUIPAMENTO_INTERNET -> featureFlags.fibraEnabled
            TipoFerramenta.DNS -> featureFlags.dnsEnabled
            TipoFerramenta.LAUDO -> featureFlags.diagnosticoEnabled
            TipoFerramenta.MONITORAMENTO -> featureFlags.settingsEnabled
            TipoFerramenta.PING, TipoFerramenta.MODO_JOGOS -> true
        }
    return when {
        !flagEnabled -> FerramentaDisponibilidade.IndisponivelRemotamente("Tente novamente mais tarde.")
        !conectado &&
            tipo in
            setOf(
                TipoFerramenta.EQUIPAMENTO_INTERNET,
                TipoFerramenta.PING,
                TipoFerramenta.DNS,
                TipoFerramenta.MODO_JOGOS,
            ) -> FerramentaDisponibilidade.Offline("Reconecte-se e tente novamente.")
        !temPermissaoLocalizacao &&
            tipo in
            setOf(TipoFerramenta.SINAL_CANAIS_MOVEL, TipoFerramenta.SINAL_WIFI, TipoFerramenta.DISPOSITIVOS) ->
            FerramentaDisponibilidade.PermissaoNecessaria("Abra para permitir redes próximas.")
        else -> FerramentaDisponibilidade.Disponivel
    }
}

/**
 * A raiz em si: só compõe [FerramentasScreen] a partir do grupo de ações. Sem estado próprio —
 * o hub inteiro é stateless em relação ao shell.
 */
@Composable
internal fun AppShellFerramentasRoot(
    acoes: AppShellFerramentasAcoes,
    disponibilidade: (TipoFerramenta) -> FerramentaDisponibilidade,
    onAbrirMenu: () -> Unit,
    onRegistrarAbertura: (TipoFerramenta) -> Unit,
) {
    FerramentasScreen(
        onAbrirMenu = onAbrirMenu,
        onAbrirSinalCanais = acoes.onAbrirSinalCanais,
        onAbrirDispositivos = acoes.onAbrirDispositivos,
        onAbrirEquipamentoInternet = acoes.onAbrirEquipamentoInternet,
        onAbrirPing = acoes.onAbrirPing,
        onAbrirDns = acoes.onAbrirDns,
        onAbrirLaudo = acoes.onAbrirLaudo,
        onAbrirMonitoramento = acoes.onAbrirMonitoramento,
        onAbrirJogos = acoes.onAbrirModoGamer,
        onAbrirSinalWifi = acoes.onAbrirSinalWifi,
        disponibilidade = disponibilidade,
        onRegistrarAbertura = onRegistrarAbertura,
    )
}
