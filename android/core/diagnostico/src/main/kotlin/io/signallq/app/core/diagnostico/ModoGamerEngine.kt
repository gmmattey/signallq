package io.signallq.app.core.diagnostico

/**
 * Modo gamer — Feature #550, issue #1476, fundido com o fluxo legado "Jogos" (GH#935) pela
 * issue #1487. Reaproveita a MESMA infraestrutura de [DiagnosticoGuiadoEngine]
 * ([Dimensao]/[MensagensStatus]/[construirResultadoBase]/[EvidenciaDiagnostico]/
 * [DiagnosticStatus]) — nenhum vocabulário de status novo, nenhum "pior faixa vence"
 * reimplementado. [DiagnosticoGuiadoEngine.avaliarJogosComLag] (objetivo "Jogos com lag ou
 * ping alto") já mede latência+jitter+perda com [MetricClassifier]; este motor generaliza a
 * mesma ideia por [CategoriaJogoModoGamer].
 *
 * Não reaproveita [GameReadinessClassifier] diretamente: aquele objeto tem vocabulário
 * PRÓPRIO ([GameReadinessClassifier.ReadinessStatus], "não reutilizar em outro contexto",
 * ver seu próprio kdoc) e hoje só alimenta [DiagnosticReport] (nenhuma tela consome), então
 * misturar os dois exigiria vazar `ReadinessStatus` pra dentro do container visual
 * "Medido pelo motor SignallQ" (`AiVsMotorExplainer`, que espera [EvidenciaDiagnostico]/
 * [DiagnosticStatus]) — na prática recriaria o mesmo motor com um verniz de adapter.
 *
 * Issue #1487 (fusão com GH#935) aposentou `JogoConexaoEngine`/`PerfilThresholds` (motor
 * por-jogo do legado, vocabulário `NivelResultado` próprio) — não viram um segundo motor
 * paralelo. O catálogo de 15 jogos do legado foi fundido em [CatalogoJogosModoGamer]
 * (dedupe por nome, ver kdoc do catálogo); o ping ao vivo do legado
 * ([io.signallq.app.feature.speedtest.PingExecutor] contra o `game-latency-probe-worker`)
 * virou refinamento OPCIONAL via [pingEspecificoMs] — nunca obrigatório, nunca bloqueia o
 * fluxo padrão (que reaproveita o [DiagnosticInput] já coletado, sem re-testar a rede).
 */
object ModoGamerEngine {
    /**
     * @param pingEspecificoMs Refinamento opcional (issue #1487, item 2 da fusão) — medição
     * dedicada de latência via [io.signallq.app.feature.speedtest.PingExecutor] contra o
     * `game-latency-probe-worker`, oferecida como escolha não-bloqueante na etapa `Config`
     * (`ModoGamerConfigConteudo`, "Medir ping específico agora"). Quando presente, substitui
     * `input.internet.latencyMs` na dimensão de latência de cada categoria (mesmos
     * thresholds de [MetricClassifier.classificarLatencia]) e acrescenta uma evidência
     * informativa deixando claro que é uma medição dedicada — nunca influencia severidade
     * além do que a própria dimensão de latência já influenciaria. `null` (padrão) preserva
     * o comportamento original: usa só o `input` já coletado, sem medir nada de novo.
     */
    fun avaliar(
        categoria: CategoriaJogoModoGamer,
        device: DeviceJogo,
        input: DiagnosticInput?,
        pingEspecificoMs: Double? = null,
        jitterMs: Double? = null,
        perdaPercentual: Double? = null,
    ): ResultadoModoGamer {
        val avaliador =
            when (categoria) {
                CategoriaJogoModoGamer.FPS_COMPETITIVO -> ::avaliarFpsCompetitivo
                CategoriaJogoModoGamer.BATTLE_ROYALE -> ::avaliarBattleRoyale
                CategoriaJogoModoGamer.MOBA -> ::avaliarMoba
                CategoriaJogoModoGamer.CASUAL -> ::avaliarCasual
                CategoriaJogoModoGamer.CLOUD_GAMING -> ::avaliarCloudGaming
                CategoriaJogoModoGamer.OUTRO -> ::avaliarOutro
            }
        val inputEfetivo = aplicarPingEspecifico(input, pingEspecificoMs, jitterMs, perdaPercentual)
        val resultado = avaliador(inputEfetivo, device)
        return if (pingEspecificoMs != null) {
            resultado.copy(evidencias = resultado.evidencias + evidenciaPingEspecifico(pingEspecificoMs))
        } else {
            resultado
        }
    }

    /** Substitui a latência, jitter e perda do [input] pela medição dedicada — demais
     *  métricas continuam vindo do `input` já coletado. Quando não havia [input] nenhum
     *  (ex.: usuário abriu o Modo gamer direto de Ferramentas, sem nunca ter rodado um teste
     *  de velocidade), a medição dedicada passa a ser a ÚNICA fonte de dado — deixa de cair
     *  em "dados insuficientes" só por causa disso. */
    private fun aplicarPingEspecifico(
        input: DiagnosticInput?,
        pingEspecificoMs: Double?,
        jitterMs: Double?,
        perdaPercentual: Double?,
    ): DiagnosticInput? {
        if (pingEspecificoMs == null) return input
        val internetBase =
            input?.internet ?: InternetDiagnosticInput(
                downloadMbps = null,
                uploadMbps = null,
                latencyMs = null,
                jitterMs = null,
                perdaPercentual = null,
            )
        return (input ?: DiagnosticInput()).copy(
            internet =
                internetBase.copy(
                    latencyMs = pingEspecificoMs,
                    jitterMs = jitterMs ?: internetBase.jitterMs,
                    perdaPercentual = perdaPercentual ?: internetBase.perdaPercentual,
                ),
        )
    }

    private fun evidenciaPingEspecifico(pingEspecificoMs: Double): EvidenciaDiagnostico =
        EvidenciaDiagnostico(
            label = "Tempo de resposta medido agora",
            valorExibido = "%.0f ms".format(pingEspecificoMs),
            status = MetricStatus.inconclusivo,
        )

    // ── FPS competitivo (Valorant, CODM, EA FC) ─────────────────────────────
    // Mesmas 3 dimensões de DiagnosticoGuiadoEngine.avaliarJogosComLag (latência + jitter +
    // perda) — jogo de precisão em tempo real, mesma prioridade de métrica.
    private fun avaliarFpsCompetitivo(
        input: DiagnosticInput?,
        device: DeviceJogo,
    ): ResultadoModoGamer {
        // Issue #1667 — mesma leitura de latência+jitter+perda de
        // DiagnosticoGuiadoEngine.avaliarJogosComLag, via dimsLatenciaJitterPerda (fonte
        // única, ver kdoc da função).
        val dims = dimsLatenciaJitterPerda(input?.internet)
        return montarResultadoModoGamer(
            categoria = CategoriaJogoModoGamer.FPS_COMPETITIVO,
            device = device,
            dims = dims,
            mensagens =
                MensagensStatus(
                    ok = "Tempo de resposta, variação do tempo de resposta e falhas na conexão dentro da faixa recomendada para FPS competitivo.",
                    atencao = "O tempo de resposta ou a variação do tempo de resposta estão no limite. Pode haver atraso perceptível na mira ou no registro de tiros.",
                    critica = "O tempo de resposta sob carga está prejudicando suas partidas. Faixa crítica para FPS competitivo.",
                ),
            acoes = { status ->
                if (status == DiagnosticStatus.ok) emptyList() else listOf("Jogue com cabo de rede em vez de Wi-Fi", "Ative priorização de jogos (prioridade de tráfego) no roteador, se disponível")
            },
        )
    }

    // ── Battle royale (Free Fire, Fortnite) ─────────────────────────────────
    // Lobby grande + assets carregados em tempo real: além de latência/jitter, download
    // entra como 3ª dimensão (diferente de FPS_COMPETITIVO, que prioriza perda).
    private fun avaliarBattleRoyale(
        input: DiagnosticInput?,
        device: DeviceJogo,
    ): ResultadoModoGamer {
        val internet = input?.internet
        val dims = mutableListOf<Dimensao>()
        internet?.latencyMs?.let { dims += Dimensao("Tempo de resposta com a rede ocupada", "%.0f ms".format(it), MetricClassifier.classificarLatencia(it)) }
        internet?.jitterMs?.let { dims += Dimensao("Variação do tempo de resposta", "%.0f ms".format(it), MetricClassifier.classificarJitter(it)) }
        internet?.downloadMbps?.let { dims += Dimensao("Download", "%.1f Mbps".format(it), MetricClassifier.classificarDownload(it)) }
        return montarResultadoModoGamer(
            categoria = CategoriaJogoModoGamer.BATTLE_ROYALE,
            device = device,
            dims = dims,
            mensagens =
                MensagensStatus(
                    ok = "Tempo de resposta, variação do tempo de resposta e download dentro do esperado para battle royale.",
                    atencao = "O tempo de resposta, a variação do tempo de resposta ou o download estão no limite. Pode haver atraso ao entrar na partida ou durante o combate.",
                    critica = "O tempo de resposta ou o download estão comprometidos. Isso costuma atrasar o carregamento da partida e travar o combate.",
                ),
            acoes = { status ->
                if (status == DiagnosticStatus.ok) emptyList() else listOf("Jogue com cabo de rede em vez de Wi-Fi", "Feche downloads em segundo plano antes de entrar na partida")
            },
        )
    }

    // ── MOBA (League of Legends) ────────────────────────────────────────────
    // Partidas longas, sensíveis a oscilação constante mais do que a picos isolados —
    // jitter entra primeiro na leitura, perda continua relevante (teamfight decide em ms).
    private fun avaliarMoba(
        input: DiagnosticInput?,
        device: DeviceJogo,
    ): ResultadoModoGamer {
        val internet = input?.internet
        val dims = mutableListOf<Dimensao>()
        internet?.jitterMs?.let { dims += Dimensao("Variação do tempo de resposta", "%.0f ms".format(it), MetricClassifier.classificarJitter(it)) }
        internet?.latencyMs?.let { dims += Dimensao("Tempo de resposta com a rede ocupada", "%.0f ms".format(it), MetricClassifier.classificarLatencia(it)) }
        internet?.perdaPercentual?.let { dims += Dimensao("Falhas estimadas na conexão", "%.1f%%".format(it), MetricClassifier.classificarPerdaPacotes(it)) }
        return montarResultadoModoGamer(
            categoria = CategoriaJogoModoGamer.MOBA,
            device = device,
            dims = dims,
            mensagens =
                MensagensStatus(
                    ok = "Variação do tempo de resposta, tempo de resposta e falhas na conexão dentro do esperado para MOBA.",
                    atencao = "Oscilação ou tempo de resposta no limite. Pode haver atraso perceptível em combates em equipe.",
                    critica = "Oscilação ou tempo de resposta prejudicando a partida. Faixa crítica para MOBA competitivo.",
                ),
            acoes = { status ->
                if (status == DiagnosticStatus.ok) emptyList() else listOf("Priorize a rede 5 GHz perto do roteador", "Evite downloads/uploads simultâneos durante a partida")
            },
        )
    }

    // ── Jogo casual ou mobile (Minecraft, Roblox, Genshin Impact) ───────────
    // Menos sensível a precisão de tiro em tempo real — download (mundo/assets) e latência
    // básica bastam; sem exigir jitter/perda tão estritos quanto os perfis competitivos.
    private fun avaliarCasual(
        input: DiagnosticInput?,
        device: DeviceJogo,
    ): ResultadoModoGamer {
        val internet = input?.internet
        val dims = mutableListOf<Dimensao>()
        internet?.downloadMbps?.let { dims += Dimensao("Download", "%.1f Mbps".format(it), MetricClassifier.classificarDownload(it)) }
        internet?.latencyMs?.let { dims += Dimensao("Tempo de resposta", "%.0f ms".format(it), MetricClassifier.classificarLatencia(it)) }
        return montarResultadoModoGamer(
            categoria = CategoriaJogoModoGamer.CASUAL,
            device = device,
            dims = dims,
            mensagens =
                MensagensStatus(
                    ok = "Download e tempo de resposta dentro do esperado para este tipo de jogo.",
                    atencao = "Download ou tempo de resposta no limite. Pode haver demora ao carregar conteúdo ou pequenos atrasos.",
                    critica = "Download ou tempo de resposta comprometidos. Isso costuma travar o carregamento do jogo.",
                ),
            acoes = { status ->
                if (status == DiagnosticStatus.ok) emptyList() else listOf("Feche downloads/uploads em segundo plano", "Aproxime-se do roteador ou use a rede 5 GHz")
            },
        )
    }

    // ── Cloud gaming (TV/Cloud gaming) ──────────────────────────────────────
    // Vídeo em tempo real: download alto + baixo atraso sob carga (bufferbloat), mesmas 2
    // dimensões de DiagnosticoGuiadoEngine.avaliarVideosTravam, mais latência.
    private fun avaliarCloudGaming(
        input: DiagnosticInput?,
        device: DeviceJogo,
    ): ResultadoModoGamer {
        val internet = input?.internet
        val dims = mutableListOf<Dimensao>()
        internet?.downloadMbps?.let { dims += Dimensao("Download", "%.1f Mbps".format(it), MetricClassifier.classificarDownload(it)) }
        internet?.bufferbloatMs?.let { dims += Dimensao("Lentidão com a rede ocupada", "%.0f ms".format(it), MetricClassifier.classificarBufferbloat(it)) }
        internet?.latencyMs?.let { dims += Dimensao("Tempo de resposta", "%.0f ms".format(it), MetricClassifier.classificarLatencia(it)) }
        return montarResultadoModoGamer(
            categoria = CategoriaJogoModoGamer.CLOUD_GAMING,
            device = device,
            dims = dims,
            mensagens =
                MensagensStatus(
                    ok = "Download e atraso sob carga dentro do esperado. A rede aguenta streaming de jogo em boa qualidade.",
                    atencao = "Download ou atraso sob carga no limite. Pode haver perda de qualidade de imagem ou engasgos.",
                    critica = "Download baixo com atraso alto sob carga. Isso costuma travar a transmissão do jogo.",
                ),
            acoes = { status ->
                if (status == DiagnosticStatus.ok) emptyList() else listOf("Perto do roteador, prefira a rede 5 GHz ou 6 GHz. Longe dele ou com paredes no caminho, a 2,4 GHz pode funcionar melhor.", "Pause downloads em segundo plano durante a sessão")
            },
        )
    }

    // ── Outro tipo de jogo (fallback quando o jogo não está no catálogo) ────
    // Mesmas 3 dimensões de FPS_COMPETITIVO — é a régua "genérica" citada na mensagem de
    // fallback (nunca inventa um perfil mais permissivo só por não conhecer o jogo).
    private fun avaliarOutro(
        input: DiagnosticInput?,
        device: DeviceJogo,
    ): ResultadoModoGamer {
        // Issue #1667 — mesma fonte de dimsLatenciaJitterPerda de avaliarFpsCompetitivo /
        // DiagnosticoGuiadoEngine.avaliarJogosComLag.
        val dims = dimsLatenciaJitterPerda(input?.internet)
        return montarResultadoModoGamer(
            categoria = CategoriaJogoModoGamer.OUTRO,
            device = device,
            dims = dims,
            mensagens =
                MensagensStatus(
                    ok = "Tempo de resposta, variação do tempo de resposta e falhas na conexão dentro do esperado para jogos online em geral.",
                    atencao = "Tempo de resposta ou variação do tempo de resposta no limite. Pode haver atraso perceptível em momentos de disputa.",
                    critica = "Tempo de resposta ou variação do tempo de resposta prejudicando a partida. Faixa crítica para jogos online.",
                ),
            acoes = { status ->
                if (status == DiagnosticStatus.ok) emptyList() else listOf("Jogue com cabo de rede em vez de Wi-Fi, quando possível", "Evite downloads/uploads simultâneos durante a partida")
            },
        )
    }

    // ── Compartilhado ────────────────────────────────────────────────────────

    private fun montarResultadoModoGamer(
        categoria: CategoriaJogoModoGamer,
        device: DeviceJogo,
        dims: List<Dimensao>,
        mensagens: MensagensStatus,
        acoes: (DiagnosticStatus) -> List<String>,
    ): ResultadoModoGamer {
        val base = construirResultadoBase(dims, mensagens)
        // Linha informativa de contexto (device testado) — sempre presente, mesmo sem
        // métricas (dadosInsuficientes), pra deixar claro em qual aparelho o usuário está
        // avaliando. Nunca influencia severidade/status (não é Dimensao).
        val evidenciaDevice = EvidenciaDiagnostico("Aparelho analisado", device.label, MetricStatus.inconclusivo)
        return ResultadoModoGamer(
            categoria = categoria,
            device = device,
            status = base.status,
            mensagemMotor = base.mensagem,
            veredito = base.status.paraVereditoDiretoModoGamer(),
            evidencias = base.evidencias + evidenciaDevice,
            // Sem dados suficientes nunca sugere ação — mesmo princípio de
            // DiagnosticoGuiadoEngine.montarResultado.
            acoes = if (base.dadosInsuficientes) emptyList() else acoes(base.status),
            dadosInsuficientes = base.dadosInsuficientes,
        )
    }
}

/**
 * Tradução direta e simples do [DiagnosticStatus] para o resultado do Modo gamer — decisão de
 * produto do Luiz (issue #1667, comentário de 2026-08-19): "linguagem direta e simples (ex.:
 * 'bom pra jogar' / 'não recomendado') em vez de fraseado de probabilidade — prioriza clareza
 * rápida sobre nuance de incerteza". Só o Modo gamer usa este texto — os outros 7 objetivos do
 * [DiagnosticoGuiadoEngine] continuam com [MensagensStatus] em prosa (fora do escopo desta
 * issue). Não confundir com o mapeamento GERAL `DiagnosticStatus.labelPt()`
 * (`ui/component/DiagnosticStatusUi.kt`, "ponto único de conversão para UI genérica") — este é
 * vocabulário de domínio (experiência de jogo), não rótulo de status neutro, mesma relação que
 * [MensagensStatus] já tem com o rótulo genérico.
 */
internal fun DiagnosticStatus.paraVereditoDiretoModoGamer(): String =
    when (this) {
        DiagnosticStatus.ok, DiagnosticStatus.info -> "Bom pra jogar"
        DiagnosticStatus.attention -> "Pode ter atrasos"
        DiagnosticStatus.critical -> "Não recomendado"
        DiagnosticStatus.inconclusive -> "Sem dados suficientes"
    }

/**
 * As 6 categorias de jogo do Modo gamer — mesmas do protótipo #1474
 * (`diagnostico-guiado.jsx`, `CATEGORIAS_GENERICAS`, issue #1483). [OUTRO] é o fallback
 * quando o jogo específico não está em [CatalogoJogosModoGamer] — nunca é erro, sempre cai
 * numa categoria com thresholds reais (regra de produto da issue #1476).
 */
enum class CategoriaJogoModoGamer(
    val label: String,
) {
    FPS_COMPETITIVO("Jogo de tiro competitivo"),
    BATTLE_ROYALE("Battle royale"),
    MOBA("Jogo de estratégia em equipe"),
    CASUAL("Jogo casual ou para celular"),
    CLOUD_GAMING("Jogo por streaming"),
    OUTRO("Outro tipo de jogo"),
}

/** Os 7 aparelhos do Modo gamer — mesmos do protótipo #1474 (`DEVICES`). Puramente
 *  informativo no motor (linha de evidência "Conexão do teste"), nunca muda thresholds —
 *  o protótipo não acopla device a categoria/thresholds, só registra o contexto. */
enum class DeviceJogo(
    val label: String,
) {
    PLAYSTATION("PS5 / PS4"),
    XBOX("Xbox"),
    PC("PC"),
    ANDROID("Android"),
    IPHONE("iPhone"),
    SWITCH("Switch"),
    TV_CLOUD("TV / Cloud gaming"),
}

/** Um jogo catalogado — [gameId] estável (nunca reaproveitar id de jogo removido; ver
 *  [CatalogoJogosModoGamer]). */
data class JogoCatalogoModoGamer(
    val gameId: String,
    val nome: String,
    val categoria: CategoriaJogoModoGamer,
    val specificProbeHost: String? = null,
    val platforms: List<DeviceJogo>? = null,
)

/**
 * Catálogo de jogos do Modo gamer — expandido com os jogos de servidores dedicados AWS GameLift
 * e catálogo remoto. Inclui suporte a plataformas recomendadas/disponíveis por jogo.
 */
object CatalogoJogosModoGamer {
    val jogos: List<JogoCatalogoModoGamer> =
        listOf(
            // ── Battle royale ────────────────────────────────────────────────────
            JogoCatalogoModoGamer("freefire", "Free Fire", CategoriaJogoModoGamer.BATTLE_ROYALE, platforms = listOf(DeviceJogo.ANDROID, DeviceJogo.IPHONE)),
            JogoCatalogoModoGamer("fortnite", "Fortnite", CategoriaJogoModoGamer.BATTLE_ROYALE, platforms = listOf(DeviceJogo.ANDROID, DeviceJogo.IPHONE, DeviceJogo.PLAYSTATION, DeviceJogo.XBOX, DeviceJogo.PC, DeviceJogo.SWITCH)),
            JogoCatalogoModoGamer("warzone", "Call of Duty: Warzone", CategoriaJogoModoGamer.BATTLE_ROYALE, platforms = listOf(DeviceJogo.PLAYSTATION, DeviceJogo.XBOX, DeviceJogo.PC)),
            JogoCatalogoModoGamer("apex_legends", "Apex Legends", CategoriaJogoModoGamer.BATTLE_ROYALE, platforms = listOf(DeviceJogo.PLAYSTATION, DeviceJogo.XBOX, DeviceJogo.PC, DeviceJogo.SWITCH)),
            JogoCatalogoModoGamer("pubg_battlegrounds", "PUBG: Battlegrounds", CategoriaJogoModoGamer.BATTLE_ROYALE, platforms = listOf(DeviceJogo.PLAYSTATION, DeviceJogo.XBOX, DeviceJogo.PC)),
            JogoCatalogoModoGamer("blood_strike", "Blood Strike", CategoriaJogoModoGamer.BATTLE_ROYALE, platforms = listOf(DeviceJogo.ANDROID, DeviceJogo.IPHONE, DeviceJogo.PC)),
            JogoCatalogoModoGamer("crossfar", "Crossfar", CategoriaJogoModoGamer.BATTLE_ROYALE, platforms = listOf(DeviceJogo.PC)),
            JogoCatalogoModoGamer("lost_rift", "Lost Rift", CategoriaJogoModoGamer.BATTLE_ROYALE, platforms = listOf(DeviceJogo.PC)),
            JogoCatalogoModoGamer("marauders", "Marauders", CategoriaJogoModoGamer.BATTLE_ROYALE, platforms = listOf(DeviceJogo.PC)),
            JogoCatalogoModoGamer("tacticool", "Tacticool", CategoriaJogoModoGamer.BATTLE_ROYALE, platforms = listOf(DeviceJogo.ANDROID, DeviceJogo.IPHONE, DeviceJogo.PC)),
            // ── FPS competitivo ──────────────────────────────────────────────────
            JogoCatalogoModoGamer("valorant", "Valorant", CategoriaJogoModoGamer.FPS_COMPETITIVO, platforms = listOf(DeviceJogo.PC, DeviceJogo.PLAYSTATION, DeviceJogo.XBOX)),
            JogoCatalogoModoGamer("codm", "Call of Duty Mobile", CategoriaJogoModoGamer.FPS_COMPETITIVO, platforms = listOf(DeviceJogo.ANDROID, DeviceJogo.IPHONE)),
            JogoCatalogoModoGamer("ea_fc", "EA FC", CategoriaJogoModoGamer.FPS_COMPETITIVO, platforms = listOf(DeviceJogo.PLAYSTATION, DeviceJogo.XBOX, DeviceJogo.PC, DeviceJogo.SWITCH)),
            JogoCatalogoModoGamer("overwatch", "Overwatch", CategoriaJogoModoGamer.FPS_COMPETITIVO, platforms = listOf(DeviceJogo.PLAYSTATION, DeviceJogo.XBOX, DeviceJogo.PC, DeviceJogo.SWITCH)),
            JogoCatalogoModoGamer("overwatch_2", "Overwatch 2", CategoriaJogoModoGamer.FPS_COMPETITIVO, platforms = listOf(DeviceJogo.PLAYSTATION, DeviceJogo.XBOX, DeviceJogo.PC, DeviceJogo.SWITCH)),
            JogoCatalogoModoGamer("rainbow_six_siege", "Rainbow Six Siege", CategoriaJogoModoGamer.FPS_COMPETITIVO, platforms = listOf(DeviceJogo.PLAYSTATION, DeviceJogo.XBOX, DeviceJogo.PC)),
            JogoCatalogoModoGamer("rainbow_six_mobile", "Rainbow Six Mobile", CategoriaJogoModoGamer.FPS_COMPETITIVO, platforms = listOf(DeviceJogo.ANDROID, DeviceJogo.IPHONE)),
            JogoCatalogoModoGamer("marvel_rivals", "Marvel Rivals", CategoriaJogoModoGamer.FPS_COMPETITIVO, platforms = listOf(DeviceJogo.PLAYSTATION, DeviceJogo.XBOX, DeviceJogo.PC)),
            JogoCatalogoModoGamer("the_finals", "THE FINALS", CategoriaJogoModoGamer.FPS_COMPETITIVO, platforms = listOf(DeviceJogo.PLAYSTATION, DeviceJogo.XBOX, DeviceJogo.PC)),
            JogoCatalogoModoGamer("counter_strike_2", "Counter-Strike 2", CategoriaJogoModoGamer.FPS_COMPETITIVO, platforms = listOf(DeviceJogo.PC)),
            JogoCatalogoModoGamer("rocket_league", "Rocket League", CategoriaJogoModoGamer.FPS_COMPETITIVO, platforms = listOf(DeviceJogo.PLAYSTATION, DeviceJogo.XBOX, DeviceJogo.PC, DeviceJogo.SWITCH)),
            // ── MOBA ─────────────────────────────────────────────────────────────
            JogoCatalogoModoGamer("league_of_legends", "League of Legends", CategoriaJogoModoGamer.MOBA, platforms = listOf(DeviceJogo.PC)),
            JogoCatalogoModoGamer("dota_2", "Dota 2", CategoriaJogoModoGamer.MOBA, platforms = listOf(DeviceJogo.PC)),
            JogoCatalogoModoGamer("wild_rift", "League of Legends: Wild Rift", CategoriaJogoModoGamer.MOBA, platforms = listOf(DeviceJogo.ANDROID, DeviceJogo.IPHONE)),
            JogoCatalogoModoGamer("predecessor", "Predecessor", CategoriaJogoModoGamer.MOBA, platforms = listOf(DeviceJogo.PLAYSTATION, DeviceJogo.XBOX, DeviceJogo.PC)),
            // ── Casual ou mobile ─────────────────────────────────────────────────
            JogoCatalogoModoGamer("minecraft", "Minecraft", CategoriaJogoModoGamer.CASUAL, platforms = listOf(DeviceJogo.ANDROID, DeviceJogo.IPHONE, DeviceJogo.PLAYSTATION, DeviceJogo.XBOX, DeviceJogo.PC, DeviceJogo.SWITCH)),
            JogoCatalogoModoGamer("roblox", "Roblox", CategoriaJogoModoGamer.CASUAL, platforms = listOf(DeviceJogo.ANDROID, DeviceJogo.IPHONE, DeviceJogo.PLAYSTATION, DeviceJogo.XBOX, DeviceJogo.PC)),
            JogoCatalogoModoGamer("genshin_impact", "Genshin Impact", CategoriaJogoModoGamer.CASUAL, platforms = listOf(DeviceJogo.ANDROID, DeviceJogo.IPHONE, DeviceJogo.PLAYSTATION, DeviceJogo.PC)),
            JogoCatalogoModoGamer("dead_by_daylight", "Dead by Daylight", CategoriaJogoModoGamer.CASUAL, platforms = listOf(DeviceJogo.PLAYSTATION, DeviceJogo.XBOX, DeviceJogo.PC, DeviceJogo.SWITCH)),
            JogoCatalogoModoGamer("destiny_2", "Destiny 2", CategoriaJogoModoGamer.CASUAL, platforms = listOf(DeviceJogo.PLAYSTATION, DeviceJogo.XBOX, DeviceJogo.PC)),
            JogoCatalogoModoGamer("brawl_stars", "Brawl Stars", CategoriaJogoModoGamer.CASUAL, platforms = listOf(DeviceJogo.ANDROID, DeviceJogo.IPHONE)),
            JogoCatalogoModoGamer("palworld", "Palworld", CategoriaJogoModoGamer.CASUAL, platforms = listOf(DeviceJogo.PLAYSTATION, DeviceJogo.XBOX, DeviceJogo.PC)),
            JogoCatalogoModoGamer("rust", "Rust", CategoriaJogoModoGamer.CASUAL, platforms = listOf(DeviceJogo.PLAYSTATION, DeviceJogo.XBOX, DeviceJogo.PC)),
            JogoCatalogoModoGamer("rocket_league_sideswipe", "Rocket League Sideswipe", CategoriaJogoModoGamer.CASUAL, platforms = listOf(DeviceJogo.ANDROID, DeviceJogo.IPHONE)),
            JogoCatalogoModoGamer("for_honor", "For Honor", CategoriaJogoModoGamer.CASUAL, platforms = listOf(DeviceJogo.PLAYSTATION, DeviceJogo.XBOX, DeviceJogo.PC)),
            JogoCatalogoModoGamer("borderlands_3", "Borderlands 3", CategoriaJogoModoGamer.CASUAL, platforms = listOf(DeviceJogo.PLAYSTATION, DeviceJogo.XBOX, DeviceJogo.PC)),
            JogoCatalogoModoGamer("new_world", "New World: Aeternum", CategoriaJogoModoGamer.CASUAL, platforms = listOf(DeviceJogo.PLAYSTATION, DeviceJogo.XBOX, DeviceJogo.PC)),
            JogoCatalogoModoGamer("squid_game_unleashed", "Squid Game: Unleashed", CategoriaJogoModoGamer.CASUAL, platforms = listOf(DeviceJogo.ANDROID, DeviceJogo.IPHONE, DeviceJogo.PLAYSTATION, DeviceJogo.XBOX, DeviceJogo.PC)),
            JogoCatalogoModoGamer("warhammer_darktide", "Warhammer 40,000: Darktide", CategoriaJogoModoGamer.CASUAL, platforms = listOf(DeviceJogo.PLAYSTATION, DeviceJogo.XBOX, DeviceJogo.PC)),
            JogoCatalogoModoGamer("marvel_snap", "MARVEL SNAP", CategoriaJogoModoGamer.CASUAL, platforms = listOf(DeviceJogo.ANDROID, DeviceJogo.IPHONE, DeviceJogo.PC)),
            JogoCatalogoModoGamer("the_outlast_trials", "The Outlast Trials", CategoriaJogoModoGamer.CASUAL, platforms = listOf(DeviceJogo.PLAYSTATION, DeviceJogo.XBOX, DeviceJogo.PC)),
            JogoCatalogoModoGamer("tribes_of_midgard", "Tribes of Midgard", CategoriaJogoModoGamer.CASUAL, platforms = listOf(DeviceJogo.PLAYSTATION, DeviceJogo.XBOX, DeviceJogo.PC, DeviceJogo.SWITCH)),
            JogoCatalogoModoGamer("star_wars_hunters", "Star Wars: Hunters", CategoriaJogoModoGamer.CASUAL, platforms = listOf(DeviceJogo.ANDROID, DeviceJogo.IPHONE, DeviceJogo.SWITCH)),
            JogoCatalogoModoGamer("rematch", "Rematch", CategoriaJogoModoGamer.CASUAL, platforms = listOf(DeviceJogo.PLAYSTATION, DeviceJogo.XBOX, DeviceJogo.PC)),
            JogoCatalogoModoGamer("super_mega_baseball_4", "Super Mega Baseball 4", CategoriaJogoModoGamer.CASUAL, platforms = listOf(DeviceJogo.PLAYSTATION, DeviceJogo.XBOX, DeviceJogo.PC, DeviceJogo.SWITCH)),
            JogoCatalogoModoGamer("legacy_steel_and_sorcery", "Legacy: Steel & Sorcery", CategoriaJogoModoGamer.CASUAL, platforms = listOf(DeviceJogo.PC)),
            JogoCatalogoModoGamer("ninjala", "Ninjala", CategoriaJogoModoGamer.CASUAL, platforms = listOf(DeviceJogo.SWITCH)),
            JogoCatalogoModoGamer("roller_champions", "Roller Champions", CategoriaJogoModoGamer.CASUAL, platforms = listOf(DeviceJogo.PLAYSTATION, DeviceJogo.XBOX, DeviceJogo.PC, DeviceJogo.SWITCH)),
            JogoCatalogoModoGamer("ghost_recon_breakpoint", "Ghost Recon Breakpoint", CategoriaJogoModoGamer.CASUAL, platforms = listOf(DeviceJogo.PLAYSTATION, DeviceJogo.XBOX, DeviceJogo.PC)),
            JogoCatalogoModoGamer("dark_alliance", "Dungeons & Dragons: Dark Alliance", CategoriaJogoModoGamer.CASUAL, platforms = listOf(DeviceJogo.PLAYSTATION, DeviceJogo.XBOX, DeviceJogo.PC)),
            JogoCatalogoModoGamer("wolcen_lords_of_mayhem", "Wolcen: Lords of Mayhem", CategoriaJogoModoGamer.CASUAL, platforms = listOf(DeviceJogo.PLAYSTATION, DeviceJogo.XBOX, DeviceJogo.PC)),
            JogoCatalogoModoGamer("streamline", "Streamline", CategoriaJogoModoGamer.CASUAL, platforms = listOf(DeviceJogo.PC)),
            JogoCatalogoModoGamer("ghost_war", "Ghost War", CategoriaJogoModoGamer.CASUAL, platforms = listOf(DeviceJogo.ANDROID, DeviceJogo.IPHONE)),
        )

    fun porId(gameId: String): JogoCatalogoModoGamer? = jogos.find { it.gameId == gameId }
}

/**
 * Resultado do Modo gamer para uma [categoria] (do jogo catalogado ou da categoria genérica
 * de fallback) + [device]. Mesmo contrato de evidências/status de
 * [ResultadoDiagnosticoGuiado] — o container visual "Medido pelo motor SignallQ" /
 * "Explicado por IA" (`AiVsMotorExplainer`, extraído em #1476 pra `ui/component`) é
 * literalmente o mesmo Composable dos dois motores.
 */
data class ResultadoModoGamer(
    val categoria: CategoriaJogoModoGamer,
    val device: DeviceJogo,
    val status: DiagnosticStatus,
    val mensagemMotor: String,
    /** Headline direta e simples (issue #1667, decisão do Luiz 2026-08-19) — "Bom pra jogar" /
     *  "Pode ter atrasos" / "Não recomendado" / "Sem dados suficientes". Mostrada acima de
     *  [mensagemMotor] no resultado; nunca substitui as evidências reais. */
    val veredito: String,
    val evidencias: List<EvidenciaDiagnostico>,
    val acoes: List<String>,
    val dadosInsuficientes: Boolean,
)
