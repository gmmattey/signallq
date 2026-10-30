package io.signallq.app.feature.fibra

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.net.ConnectException
import java.net.SocketTimeoutException

class ExecutorFibra {
    private val mutableSnapshotFlow =
        MutableStateFlow(
            SnapshotFibra(
                estado = EstadoFibra.idle,
                gpon = null,
                wan = null,
                ppp = null,
                deviceInfo = null,
                erroMensagem = null,
            ),
        )

    val snapshotFlow: StateFlow<SnapshotFibra> = mutableSnapshotFlow.asStateFlow()

    // #894: antes cada chamada de executar() criava um NokiaModemClient novo e fazia login()
    // do zero, mesmo que a sessao HTTP anterior ainda estivesse valida no equipamento — o
    // firmware chega a REJEITAR o novo login com err_t=0 ("sessao em uso por outro acesso")
    // quando a sessao anterior ainda nao expirou no lado do modem. Isso quebrava exatamente o
    // cenario da issue: usuario marca "manter conectado", navega pra outra tela e volta, e o
    // app tenta autenticar de novo em vez de reusar a sessao que ainda estava de pe.
    // Cache vale apenas enquanto o processo do app estiver vivo (escopo prometido pela opcao
    // "manter conectado" — nunca sobrevive ao processo sendo encerrado, ExecutorFibra nao
    // persiste nada em disco).
    private var clienteCache: NokiaModemClient? = null
    private var credenciaisCache: Triple<String, String, String>? = null

    suspend fun executar(
        host: String,
        username: String,
        password: String,
    ) =
        withContext(Dispatchers.IO) {
            mutableSnapshotFlow.value = SnapshotFibra(EstadoFibra.conectando, null, null, null, null, null)

            val credenciaisAtuais = Triple(host, username, password)
            val clienteReaproveitavel =
                clienteCache?.takeIf { credenciaisCache == credenciaisAtuais && it.isLoggedIn }

            // Tentativa 0: reusa a sessao existente sem novo login. Se falhar (sessao expirou no
            // equipamento, rede mudou, etc.), invalida o cache e cai no fluxo normal de login+retry.
            if (clienteReaproveitavel != null) {
                val resultado = runCatching { buscarSnapshot(clienteReaproveitavel) }
                resultado.onSuccess { snapshot ->
                    Timber.i("executar: sessao reaproveitada com sucesso, sem novo login")
                    mutableSnapshotFlow.value = snapshot
                    return@withContext
                }
                Timber.w(resultado.exceptionOrNull(), "executar: sessao cacheada invalida, refazendo login")
                clienteCache = null
                credenciaisCache = null
            }

            var ultimoErro: Throwable? = null
            for (tentativa in 0 until 3) {
                if (tentativa > 0) delay(1_000L * tentativa)
                try {
                    val client = NokiaModemClient(host)
                    client.login(username, password)

                    val snapshot = buscarSnapshot(client)
                    Timber.i("executar[${tentativa + 1}]: gpon=${snapshot.gpon?.status} rx=${snapshot.gpon?.rxPowerDbm}")
                    clienteCache = client
                    credenciaisCache = credenciaisAtuais
                    mutableSnapshotFlow.value = snapshot
                    return@withContext
                } catch (t: IllegalArgumentException) {
                    // GH#1213 item 2/4 — host invalido/nao-privado (ValidadorHostEquipamento)
                    // e erro de configuracao permanente, nao transitorio: retry nao vai
                    // resolver, entao para na primeira tentativa em vez de gastar as 3.
                    ultimoErro = t
                    Timber.w("executar[${tentativa + 1}]: host invalido, sem retry — ${t.message}")
                    break
                } catch (t: Throwable) {
                    if (t is kotlinx.coroutines.CancellationException) throw t
                    ultimoErro = t
                    Timber.w("executar[${tentativa + 1}]: falhou — ${t.message}")
                    // GH#1213 item 4 — credenciais invalidas (err_t=1) e driver incompativel
                    // (pagina de login sem pubkey/nonce/csrf -- nao e um Nokia, ou firmware com
                    // layout diferente) sao erros PERMANENTES: repetir 3x com a MESMA senha
                    // errada ou o MESMO host incompativel nunca muda o resultado. So timeout/
                    // conexao recusada/sessao ocupada (err_t=0) sao transitorios de verdade e
                    // continuam com retry normal.
                    val ehErroPermanente =
                        t.message?.contains("credenciais invalidas", ignoreCase = true) == true ||
                            t.message?.contains("err_t=1", ignoreCase = true) == true ||
                            t.message?.contains("pubkey", ignoreCase = true) == true ||
                            t.message?.contains("nonce", ignoreCase = true) == true ||
                            t.message?.contains("csrf", ignoreCase = true) == true
                    if (ehErroPermanente) break
                }
            }
            val t = ultimoErro ?: return@withContext
            val chave = chaveErroFibra(t)
            Timber.e(t, "executar: $chave após 3 tentativas — ${t.message}")
            mutableSnapshotFlow.value =
                SnapshotFibra(
                    estado = EstadoFibra.erro,
                    gpon = null,
                    wan = null,
                    ppp = null,
                    deviceInfo = null,
                    erroMensagem = chave,
                )
        }

    // Busca todas as paginas e monta o snapshot com o client ja autenticado (sessao nova ou
    // reaproveitada) — extraido pra ser compartilhado pelos dois caminhos de executar().
    private fun buscarSnapshot(client: NokiaModemClient): SnapshotFibra {
        val gponHtml = client.fetchPage("/wan_status.cgi?gpon")
        val wanHtml = client.fetchPage("/show_wan_status.cgi?ipv4")
        val pppJson = client.fetchPage("/index.cgi?getppp")
        val deviceHtml = client.fetchPage("/device_status.cgi")

        val gpon = NokiaModemParser.parseGpon(gponHtml)
        val wan = NokiaModemParser.parseWan(wanHtml)
        val ppp = NokiaModemParser.parsePpp(pppJson)
        val devInfo = NokiaModemParser.parseDeviceInfo(deviceHtml)

        // GH#865 Fase 1 — Wi-Fi/LAN reais (docs/technical/NOKIA_GPON_FIELD_MAP.md).
        // Leitura best-effort: falha nessas paginas novas nao deve derrubar o
        // resultado de fibra/WAN que ja funcionava.
        //
        // O objeto wlan_status (radios Wi-Fi com canal/seguranca/potencia) vive
        // na mesma pagina lan_status.cgi?lan ja buscada para LAN — corrigido apos
        // revalidacao contra equipamento real em 2026-07-10. A pagina
        // lan_status.cgi?wlan NAO contem esse objeto (contem wlan_ssid/device_cfg/
        // alias_cfg, ainda nao consumidos).
        val lanStatusHtml = client.fetchPage("/lan_status.cgi?lan")
        val lanConfigHtml = client.fetchPage("/lan_ipv4.cgi")

        val wifi =
            runCatching {
                NokiaModemParser.parseWifi(lanStatusHtml)
            }.getOrElse {
                Timber.w(it, "buscarSnapshot: falha ao ler wifi (nao critico)")
                null
            }
        val lan =
            runCatching {
                NokiaModemParser.parseLan(lanStatusHtml, lanConfigHtml)
            }.getOrElse {
                Timber.w(it, "buscarSnapshot: falha ao ler lan (nao critico)")
                null
            }

        // GH#839/#865 Fase 2 — lista real de clientes (device_cfg + alias_cfg),
        // vive em lan_status.cgi?wlan (achado real na revalidacao de
        // 2026-07-10 — essa pagina NAO tem wlan_status, so esses objetos).
        // Best-effort, mesmo padrao de wifi/lan.
        val clientes =
            runCatching {
                val wlanHtml = client.fetchPage("/lan_status.cgi?wlan")
                NokiaModemParser.parseClientes(wlanHtml)
            }.getOrElse {
                Timber.w(it, "buscarSnapshot: falha ao ler clientes (nao critico)")
                emptyList()
            }

        return SnapshotFibra(
            estado = EstadoFibra.concluido,
            gpon = gpon,
            wan = wan,
            ppp = ppp,
            deviceInfo = devInfo,
            erroMensagem = null,
            wifi = wifi,
            lan = lan,
            clientes = clientes,
        )
    }

    fun marcarSemRede() {
        mutableSnapshotFlow.value =
            SnapshotFibra(
                estado = EstadoFibra.erro,
                gpon = null,
                wan = null,
                ppp = null,
                deviceInfo = null,
                erroMensagem = "semRede",
            )
    }

    // GH#934 — solicita reboot do equipamento usando a sessao ja autenticada em cache.
    // Sem sessao ativa (usuario nunca conectou ou a sessao ja caiu), nao ha o que reiniciar —
    // retorna false sem tentar novo login (reiniciar so faz sentido a partir de um estado
    // "conectado"). Apos a chamada, invalida sempre o cache: a sessao HTTP nao sobrevive ao
    // reboot do equipamento, entao o proximo executar() precisa logar de novo do zero.
    suspend fun reiniciar(): Boolean =
        withContext(Dispatchers.IO) {
            val cliente = clienteCache ?: return@withContext false
            val resultado = runCatching { cliente.reboot() }
            clienteCache = null
            credenciaisCache = null
            val sucesso = resultado.getOrDefault(false)
            Timber.i("reiniciar: sucesso=$sucesso")
            sucesso
        }

    // #894: desconexao manual (usuario desliga "manter conectado" ou desconecta
    // explicitamente) precisa encerrar a sessao cacheada imediatamente — senao a proxima
    // chamada de executar() reaproveitaria uma sessao que o usuario ja pediu pra derrubar.
    fun desconectar() {
        clienteCache = null
        credenciaisCache = null
        mutableSnapshotFlow.value =
            SnapshotFibra(
                estado = EstadoFibra.idle,
                gpon = null,
                wan = null,
                ppp = null,
                deviceInfo = null,
                erroMensagem = null,
            )
    }
}

/**
 * GH#1213 (dispatch final) — diferenciação completa de estados de sessão/erro. Antes,
 * "sessão ocupada" (`err_t=0`) e "token expirado" (`err_t=2`) caíam ambos no bucket genérico
 * `erroComunicacaoModem`, misturados com falha de rede real — mesmo os dois já sendo,
 * internamente no firmware Nokia, causas bem diferentes de uma leitura falhar. Extraído da
 * função [ExecutorFibra.executar] pra função pura testável sem HTTP/coroutines.
 */
internal fun chaveErroFibra(t: Throwable): String =
    when {
        // GH#1213 item 2 — host informado nao e um IP privado/local valido.
        t is IllegalArgumentException -> "erroHostInvalido"
        t is ConnectException -> "erroModemInacessivel"
        t is SocketTimeoutException -> "erroTimeout"
        t.message?.contains("timed out", ignoreCase = true) == true -> "erroTimeout"
        t.message?.contains("refused", ignoreCase = true) == true -> "erroModemInacessivel"
        // GH#934 — err_t=1 do firmware Nokia = usuario/senha incorretos, distinto
        // de falha generica de comunicacao (ver AcessoEquipamento.CREDENCIAIS_NECESSARIAS).
        t.message?.contains("credenciais invalidas") == true ||
            t.message?.contains("err_t=1") == true -> "erroCredenciaisInvalidas"
        // GH#1213 (final) — err_t=0: outra sessao ja esta logada no equipamento. Transitorio
        // (a sessao concorrente tende a expirar sozinha), mas NAO e a mesma causa de "token
        // expirado" nem de falha de rede -- nao pode continuar caindo no bucket generico.
        t.message?.contains("err_t=0") == true -> "erroSessaoOcupada"
        // GH#1213 (final) — err_t=2: token/nonce da tentativa de login expirou antes do POST
        // (janela de uso unico do nonce). Um novo login() (que sempre busca nonce novo) resolve
        // sozinho -- mas e uma causa distinta de "sessao ocupada" e de erro de comunicacao.
        t.message?.contains("err_t=2") == true -> "erroTokenExpirado"
        t.message?.contains("pubkey") == true ||
            t.message?.contains("nonce") == true ||
            t.message?.contains("csrf") == true -> "erroRespostaModemInvalida"
        else -> "erroComunicacaoModem"
    }
