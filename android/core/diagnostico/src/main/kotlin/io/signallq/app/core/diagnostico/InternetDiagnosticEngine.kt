package io.signallq.app.core.diagnostico

private const val CAT = "internet"

object InternetDiagnosticEngine {
    fun avaliar(
        input: InternetDiagnosticInput?,
        wifiConfiavelParaTeste: Boolean,
        /** Tipo de conexao ativa. Usado para nao falar de "roteador/modem" quando a
         *  conexao e 100% rede movel (GH#521, mesmo padrao de FindingEngine SIG-514). */
        connectionType: ConnectionType = ConnectionType.wifi,
    ): List<DiagnosticResult> {
        if (input == null) {
            return listOf(
                DiagnosticResult(
                    id = "IN-NORMAL-00",
                    titulo = "Não encontrei um teste recente",
                    status = DiagnosticStatus.inconclusive,
                    evidencia = null,
                    mensagemUsuario = "Não encontrei um teste recente para analisar.",
                    recomendacao = "Faça um teste de velocidade para eu analisar sua conexão.",
                    categoria = CAT,
                ),
            )
        }

        // IN-NORMAL-01: internet indisponível
        if (input.downloadMbps == null) {
            return listOf(
                DiagnosticResult(
                    id = "IN-NORMAL-01",
                    titulo = "A internet pode estar sem acesso",
                    status = DiagnosticStatus.critical,
                    evidencia = "download=null",
                    mensagemUsuario = "Não consegui medir a velocidade. Sua internet pode estar sem acesso.",
                    recomendacao = "Veja se outros sites ou aplicativos abrem. Se nada funcionar, o problema pode estar no roteador ou na operadora.",
                    categoria = CAT,
                    podeConcluir = true,
                ),
            )
        }

        val resultados = mutableListOf<DiagnosticResult>()
        val dl = input.downloadMbps
        val ul = input.uploadMbps
        val lat = input.latencyMs
        val jit = input.jitterMs
        val perda = input.perdaPercentual

        // IN-NORMAL-07 / 07b: perda de pacotes
        // NAO migrado para MetricClassifier.classificarPerdaPacotes() (issue #1228 Fase 1,
        // ADR-011): a tabela do classifier usa fronteiras 0.5%/2.0% (excelente/bom/regular/ruim),
        // valores substancialmente diferentes dos limiares de negocio 1.0%/3.0% deste achado —
        // nao e um caso de fronteira coincidente por acidente de estrita-maior-que como jitter,
        // e sim duas reguas de produto distintas. Migrar mudaria o comportamento observavel (ex.:
        // perda=1.5% hoje gera "moderada", classifier ja classificaria como "regular" != "ruim"
        // aos 2.0%, sem gerar achado "critico" nem no ponto certo). Ver achado de arquitetura
        // registrado na issue #1466.
        val emRedeMovel = connectionType == ConnectionType.mobile
        if (perda != null) {
            when {
                perda >= 3.0 ->
                    resultados.add(
                        DiagnosticResult(
                            id = "IN-NORMAL-07",
                            titulo = "Muitas falhas na conexão",
                            status = DiagnosticStatus.critical,
                            evidencia = "perda=${"%.1f".format(perda)}%",
                            mensagemUsuario = "Sua conexão está com muitas falhas (${"%.1f".format(perda)}%). Chamadas de vídeo e jogos serão gravemente afetados.",
                            recomendacao =
                                if (emRedeMovel) {
                                    "Teste em outro local ou horário. Se persistir, contate a operadora."
                                } else {
                                    "Reinicie o roteador e o modem. Se persistir, contate a operadora."
                                },
                            categoria = CAT,
                            podeConcluir = true,
                        ),
                    )
                perda >= 1.0 ->
                    resultados.add(
                        DiagnosticResult(
                            id = "IN-NORMAL-07b",
                            titulo = "Algumas falhas na conexão",
                            status = DiagnosticStatus.attention,
                            evidencia = "perda=${"%.1f".format(perda)}%",
                            mensagemUsuario = "Sua conexão está com algumas falhas (${"%.1f".format(perda)}%). Jogos e chamadas podem ser afetados.",
                            recomendacao = "Verifique interferências no Wi-Fi ou instabilidade no link.",
                            categoria = CAT,
                        ),
                    )
            }
        }

        // IN-NORMAL-06: jitter — migrado para MetricClassifier (issue #1228 Fase 1, ADR-011).
        // classificarJitter() so tem fronteira "ruim" exatamente em >20.0 (mesmo valor e
        // mesma estrita-maior-que do limiar historico deste achado) — unico ponto de verdade
        // sem alterar o resultado observavel (golden test: InternetDiagnosticEngineTest).
        if (jit != null && MetricClassifier.classificarJitter(jit) == MetricStatus.ruim) {
            resultados.add(
                DiagnosticResult(
                    id = "IN-NORMAL-06",
                    titulo = "A conexão está oscilando",
                    status = DiagnosticStatus.attention,
                    evidencia = "jitter=${"%.0f".format(jit)} ms",
                    mensagemUsuario = "A variação do tempo de resposta está alta (${"%.0f".format(jit)} ms). Chamadas de voz e jogos podem ter instabilidade.",
                    recomendacao = "Verifique se há outros dispositivos consumindo a rede. Uma variação do tempo de resposta alta pode indicar congestionamento.",
                    categoria = CAT,
                ),
            )
        }

        // IN-NORMAL-05: limiar historico de latencia (>100ms). "Anatel RQUAL" era usado
        // como nome informal da ORIGEM historica deste limiar, mas NAO e uma citacao
        // normativa comprovada -- a unica documentacao real de "Anatel RQUAL" neste repo
        // (docs/01-funcional.md) descreve um criterio diferente (% de velocidade em relacao
        // ao plano contratado, Ato 7869/2022), sem relacao com latencia. Por isso a mensagem
        // ao usuario NAO cita Anatel/RQUAL (GH#1502, revisao independente da PR #1515 --
        // decisao aprovada na planilha original foi superada por revisao de confiabilidade:
        // a alegacao regulatoria nao pode ser comprovada dentro do repositorio). Mantido
        // aqui só como nota historica de origem do numero, nao como fundamento vigente.
        // NAO migrado para MetricClassifier.classificarLatencia() (issue #1228 Fase 1, ADR-011):
        // a tabela do classifier (excelente <100 | bom <=150 | regular <=200 | ruim >200) usa uma
        // fronteira "nao-excelente" em >=100.0 (inclusiva), enquanto este achado historicamente
        // dispara em >100.0 (exclusiva) — divergem no exato ponto lat=100.0, e nenhuma categoria
        // do classifier reproduz o limiar de negocio deste achado sem mudar comportamento
        // observavel (golden test trava lat=100.0 sem achado, lat=100.01 com achado). Tabelas sao
        // de fontes de produto diferentes (skill /regras-diagnostico-rede vs o limiar historico
        // deste achado) — ver achado de arquitetura registrado na issue #1466.
        if (lat != null && lat > 100.0) {
            resultados.add(
                DiagnosticResult(
                    id = "IN-NORMAL-05",
                    titulo = "A conexão está demorando para responder",
                    status = DiagnosticStatus.attention,
                    evidencia = "latencia=${"%.0f".format(lat)} ms",
                    mensagemUsuario = "O tempo de resposta está acima de 100 ms (${"%.0f".format(lat)} ms), o que pode prejudicar chamadas de voz e jogos.",
                    recomendacao = "O tempo de resposta alto pode ser causado por congestionamento na operadora ou Wi-Fi com sinal fraco.",
                    categoria = CAT,
                ),
            )
        }

        // IN-NORMAL-09: bufferbloat — migrado para MetricClassifier (issue #1228 Fase 1, ADR-011).
        // classificarBufferbloat() usa as mesmas fronteiras ja documentadas aqui historicamente
        // (nenhum <5ms | leve <=30ms=bom | moderado <=100ms=regular | severo >100ms=ruim) —
        // regular vira "elevado" (09b), ruim vira "critico" (09), bom/excelente nao geram achado.
        // Unico ponto de verdade sem alterar o resultado observavel (golden test).
        val bb = input.bufferbloatMs
        val bbStatus = bb?.let { MetricClassifier.classificarBufferbloat(it) }
        if (bb != null && (bbStatus == MetricStatus.regular || bbStatus == MetricStatus.ruim)) {
            val isCritico = bbStatus == MetricStatus.ruim
            resultados.add(
                DiagnosticResult(
                    id = if (isCritico) "IN-NORMAL-09" else "IN-NORMAL-09b",
                    titulo = if (isCritico) "A internet fica muito lenta quando está em uso" else "A internet fica mais lenta quando está em uso",
                    status = if (isCritico) DiagnosticStatus.critical else DiagnosticStatus.attention,
                    evidencia = "bufferbloat=${"%.0f".format(bb)} ms",
                    mensagemUsuario =
                        if (isCritico) {
                            "A lentidão com a rede ocupada está muito alta (${"%.0f".format(bb)} ms). Streaming, jogos e chamadas serão gravemente prejudicados mesmo com velocidade adequada."
                        } else {
                            "A lentidão com a rede ocupada está elevada (${"%.0f".format(bb)} ms). Jogos e chamadas podem ter instabilidade sob carga."
                        },
                    recomendacao = "Verifique se o roteador suporta priorização de tráfego. Reduza o número de dispositivos usando a rede simultaneamente.",
                    categoria = CAT,
                    podeConcluir = isCritico,
                ),
            )
        }

        // IN-NORMAL-04: upload
        // IN-NORMAL-04Z: upload zerado (prioridade maxima sobre upload baixo generico)
        // NAO migrado para MetricClassifier.classificarUpload() (issue #1228 Fase 1, ADR-011):
        // a tabela do classifier usa fronteiras 1/3/10/20 Mbps (throughput bruto de speedtest),
        // sem nenhuma categoria na fronteira de 5.0 Mbps usada por este achado (limiar de
        // videoconferencia/upload de arquivo) — 4.999 e 5.0 caem na mesma categoria "regular" do
        // classifier, entao nenhuma combinacao de status reproduz o limiar sem mudar
        // comportamento observavel. Ver achado de arquitetura registrado na issue #1466.
        if (ul != null && ul == 0.0) {
            resultados.add(
                DiagnosticResult(
                    id = "IN-NORMAL-04Z",
                    titulo = "Não consegui medir o envio de dados",
                    status = DiagnosticStatus.critical,
                    evidencia = "upload=0.0 Mbps",
                    mensagemUsuario = "O upload medido foi 0 Mbps. Isso costuma quebrar chamadas de vídeo, jogos online, trabalho remoto e envio de arquivos.",
                    recomendacao = "Verifique se há algum bloqueio no roteador, prioridade de tráfego mal configurado, ou instabilidade no link. Reinicie o roteador. Se persistir, contate a operadora.",
                    categoria = CAT,
                    podeConcluir = true,
                ),
            )
        }
        if (ul != null && ul > 0.0 && ul < 5.0) {
            resultados.add(
                DiagnosticResult(
                    id = "IN-NORMAL-04",
                    titulo = "A velocidade de envio está baixa",
                    status = DiagnosticStatus.attention,
                    evidencia = "upload=${"%.1f".format(ul)} Mbps",
                    mensagemUsuario = "O upload está baixo (${"%.1f".format(ul)} Mbps). Videoconferências e envio de arquivos podem ser afetados.",
                    recomendacao = "Verifique se há uploads em andamento em outros dispositivos.",
                    categoria = CAT,
                ),
            )
        }

        // IN-NORMAL-03: download — migrado para MetricClassifier (issue #1228 Fase 1, ADR-011).
        // classificarDownload() tem fronteira regular/ruim exatamente em 25.0 Mbps (mesmo valor
        // e mesma estrita-menor-que do limiar historico deste achado) — trigger "ruim ou critico"
        // reproduz exatamente dl<25.0 sem alterar o resultado observavel (golden test).
        val dlStatus = MetricClassifier.classificarDownload(dl)
        if (dlStatus == MetricStatus.ruim || dlStatus == MetricStatus.critico) {
            resultados.add(
                DiagnosticResult(
                    id = "IN-NORMAL-03",
                    titulo = "A velocidade está baixa",
                    status = DiagnosticStatus.attention,
                    evidencia = "download=${"%.1f".format(dl)} Mbps",
                    mensagemUsuario = "O download está abaixo de 25 Mbps (${"%.1f".format(dl)} Mbps), o mínimo recomendado para uso confortável.",
                    recomendacao = "Verifique se o plano contratado entrega essa velocidade e se outros dispositivos estão consumindo a rede.",
                    categoria = CAT,
                ),
            )
        }

        // IN-NORMAL-08: se WiFi não é confiável e há qualquer problema, marca tudo como inconclusivo
        if (!wifiConfiavelParaTeste && resultados.isNotEmpty()) {
            return resultados.map {
                it.copy(
                    id = "${it.id}-inc",
                    status = DiagnosticStatus.inconclusive,
                    mensagemUsuario = "${it.mensagemUsuario} Porém, o sinal Wi-Fi fraco pode ser a causa real, então o teste pode não refletir o link de internet.",
                    recomendacao = "Aproxime-se do roteador e refaça o teste. Assim, consigo avaliar melhor.",
                    podeConcluir = false,
                )
            }
        }

        // IN-NORMAL-02: tudo ok
        if (resultados.isEmpty()) {
            resultados.add(
                DiagnosticResult(
                    id = "IN-NORMAL-02",
                    titulo = "Sua conexão está boa",
                    status = DiagnosticStatus.ok,
                    evidencia = "dl=${"%.1f".format(dl)} Mbps ul=${ul?.let { "%.1f".format(it) } ?: "—"} Mbps lat=${lat?.let { "%.0f".format(it) } ?: "—"} ms",
                    mensagemUsuario = "Sua conexão está funcionando bem.",
                    recomendacao = null,
                    categoria = CAT,
                    podeConcluir = true,
                ),
            )
        }

        return resultados
    }
}
