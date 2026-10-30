package io.signallq.app.core.diagnostico

/**
 * Espelha, do lado Kotlin, o envelope completo de uma avaliacao remota de
 * diagnostico -- a interface TypeScript `DiagnosticResult` definida em
 * `integrations/cloudflare/signallq-diagnostic-worker/src/contracts.ts:158`
 * (worker `signallq-diagnostic`, endpoint `POST /diagnostic/evaluate`).
 *
 * ## Por que o nome NAO e `DiagnosticResult` (assimetria intencional)
 * Existe uma colisao real de conceitos, documentada e resolvida em
 * [ADR-011](../../../../../../../../docs/archive/decisoes/ADR-011-fase0-motor-canonico-diagnostico.md)
 * (Decisao 3.2): `io.signallq.app.core.diagnostico.DiagnosticResult` (mesmo
 * pacote deste arquivo) ja existe, em producao, com **98 usos**, e representa
 * um conceito totalmente diferente -- **um achado individual** (titulo,
 * status de 5 valores, evidencia, mensagem ao usuario). O `DiagnosticResult`
 * do lado TypeScript representa o **envelope completo de uma avaliacao**
 * (score, achados, fluxo primario/secundario, resumo humano etc.) -- e este
 * segundo conceito que [DiagnosticEvaluation] espelha.
 *
 * O ADR-011 decidiu manter o nome `DiagnosticResult` como esta dos dois
 * lados que ja existem (Kotlin local e TypeScript no worker) e batizar
 * apenas o tipo Kotlin NOVO como `DiagnosticEvaluation`, para nao colidir
 * com nenhum dos dois. Isso significa que o nome do contrato remoto e
 * `DiagnosticResult` no TS e `DiagnosticEvaluation` no Kotlin -- assimetria
 * deliberada, nao um erro de digitacao ou uma tentativa de "corrigir" mais
 * tarde renomeando um lado ou outro sem reler o ADR-011 primeiro.
 *
 * ## Escopo desta classe (issue #1443)
 * Este tipo so existe para fixar o contrato/nome -- **ainda nao esta
 * plugado em nenhum mapper, repository ou fluxo de consumo**. A integracao
 * real (deserializar o JSON de `POST /diagnostic/evaluate` para este tipo)
 * e escopo de uma issue futura de #952, hoje estimada como #1444 (shadow
 * mode). Ate la, [DiagnosticInput]/[DiagnosticReport]/[DiagnosticResult]
 * continuam sendo o unico caminho de dado real do app -- ver
 * `RemoteDiagnosticReportMapper` (`feature/diagnostico`), que hoje mapeia
 * o payload remoto direto para [DiagnosticReport], sem passar por este tipo.
 *
 * @property resultSchemaVersion versao do schema deste envelope (nunca
 *   inferida -- sempre o valor exato que o worker devolveu).
 * @property engineVersion versao do motor de regras que gerou a avaliacao.
 * @property rulesetVersion versao do ruleset (`DiagnosticRuleset.version`
 *   no TS) usado na avaliacao.
 * @property evaluationSource origem da avaliacao (remota, cache local ou
 *   ruleset embarcado no app).
 * @property overallStatus status agregado da avaliacao inteira -- vocabulario
 *   proprio deste contrato ([DiagnosticEvaluationStatus]), sem correspondencia
 *   1:1 com [DiagnosticStatus] (vocabulario do achado individual local).
 * @property score nota agregada da avaliacao (mesma escala do `score` local
 *   de [ScoreResult], mas calculada pelo motor remoto).
 * @property confidence confianca agregada da avaliacao.
 * @property matchedRules ids das regras do ruleset que casaram nesta avaliacao.
 * @property findings achados individuais desta avaliacao -- ver [DiagnosticEvaluationFinding].
 * @property recommendations ids de recomendacao (catalogo remoto) aplicaveis.
 * @property primaryFlow fluxo de causa-raiz principal apontado pela avaliacao.
 * @property secondaryFlows fluxos de causa-raiz secundarios, quando houver mais de uma hipotese.
 * @property humanSummary resumo em linguagem natural (PT-BR) da avaliacao.
 * @property humanResolution passos de resolucao em linguagem natural (PT-BR).
 * @property missingInputs campos de [DiagnosticInput]/snapshot que fariam falta para uma avaliacao mais precisa.
 * @property nextBestChecks proximas verificacoes sugeridas pelo motor remoto.
 * @property resolvableNow se a causa raiz pode ser resolvida pelo usuario agora, sem suporte externo.
 * @property evaluatedAt timestamp ISO-8601 de quando a avaliacao foi gerada no worker.
 * @property traceId id de rastreio da avaliacao (correlaciona com logs/observabilidade do worker).
 */
data class DiagnosticEvaluation(
    val resultSchemaVersion: Int,
    val engineVersion: Int,
    val rulesetVersion: Int,
    val evaluationSource: DiagnosticEvaluationSource,
    val overallStatus: DiagnosticEvaluationStatus,
    val score: Int,
    val confidence: DiagnosticEvaluationConfidence,
    val matchedRules: List<String>,
    val findings: List<DiagnosticEvaluationFinding>,
    val recommendations: List<String>,
    val primaryFlow: DiagnosticEvaluationFlow,
    val secondaryFlows: List<DiagnosticEvaluationFlow>,
    val humanSummary: String,
    val humanResolution: List<String>,
    val missingInputs: List<String>,
    val nextBestChecks: List<String>,
    val resolvableNow: Boolean,
    val evaluatedAt: String,
    val traceId: String,
)

/**
 * Achado individual dentro de [DiagnosticEvaluation.findings] -- espelha
 * `DiagnosticFinding` em `contracts.ts:137`.
 *
 * Nomeado `DiagnosticEvaluationFinding` (nao apenas `DiagnosticFinding`) de
 * proposito: o ADR-011 registra `DiagnosticFinding` (TS) como uma divida de
 * nomeacao ainda em aberto do lado Kotlin ("aplicar o mesmo cuidado de
 * nomeacao deste ADR antes de escolher um nome"). Namespacar sob
 * `DiagnosticEvaluation*` evita reservar o nome curto `DiagnosticFinding`
 * antes dessa decisao dedicada acontecer.
 */
data class DiagnosticEvaluationFinding(
    val findingCode: String,
    val category: DiagnosticEvaluationFindingCategory,
    val severity: DiagnosticEvaluationSeverity,
    val confidence: DiagnosticEvaluationConfidence,
    val recommendationId: String,
    val matchedRuleId: String,
    val matchedRuleVersion: Int,
)

/** Espelha `DiagnosticResult.evaluationSource` (`contracts.ts:162`). */
enum class DiagnosticEvaluationSource(
    val wireValue: String,
) {
    REMOTE("REMOTE"),
    CACHED_LOCAL("CACHED_LOCAL"),
    BUNDLED_LOCAL("BUNDLED_LOCAL"),
    ;

    companion object {
        fun fromWire(value: String): DiagnosticEvaluationSource? = entries.firstOrNull { it.wireValue == value }
    }
}

/**
 * Espelha `DiagnosticResult.overallStatus` (`contracts.ts:163`). Vocabulario
 * proprio deste contrato remoto -- nao confundir com [DiagnosticStatus]
 * (5 valores, achado individual local) nem com `MetricStatus` (6 valores,
 * classificador de metrica).
 */
enum class DiagnosticEvaluationStatus(
    val wireValue: String,
) {
    OK("OK"),
    ATTENTION("ATTENTION"),
    CRITICAL("CRITICAL"),
    INCONCLUSIVE("INCONCLUSIVE"),
    ;

    companion object {
        fun fromWire(value: String): DiagnosticEvaluationStatus? = entries.firstOrNull { it.wireValue == value }
    }
}

/** Espelha `confidence` em `DiagnosticResult` e `DiagnosticFinding` (`contracts.ts:165` e `:141`). */
enum class DiagnosticEvaluationConfidence(
    val wireValue: String,
) {
    LOW("LOW"),
    MEDIUM("MEDIUM"),
    HIGH("HIGH"),
    ;

    companion object {
        fun fromWire(value: String): DiagnosticEvaluationConfidence? = entries.firstOrNull { it.wireValue == value }
    }
}

/** Espelha `DiagnosticFinding.severity` (`contracts.ts:140`). */
enum class DiagnosticEvaluationSeverity(
    val wireValue: String,
) {
    INFO("INFO"),
    WARNING("WARNING"),
    ERROR("ERROR"),
    ;

    companion object {
        fun fromWire(value: String): DiagnosticEvaluationSeverity? = entries.firstOrNull { it.wireValue == value }
    }
}

/**
 * Espelha `DiagnosticFinding.category` (`contracts.ts:139`). `wireValue` e
 * explicito (nao derivado do nome da constante) porque `WIFI_CANAL` usa
 * hifen no contrato (`"wifi-canal"`), quebrando a transformacao automatica
 * UPPER_SNAKE_CASE -> snake_case que os demais valores permitiriam.
 */
enum class DiagnosticEvaluationFindingCategory(
    val wireValue: String,
) {
    INTERNET("internet"),
    WIFI("wifi"),
    DNS("dns"),
    FIBRA("fibra"),
    MOBILE("mobile"),
    HISTORICO("historico"),
    WIFI_CANAL("wifi-canal"),
    DECISAO("decisao"),
    ;

    companion object {
        fun fromWire(value: String): DiagnosticEvaluationFindingCategory? = entries.firstOrNull { it.wireValue == value }
    }
}

/**
 * Espelha `DiagnosticFlowCode` (`contracts.ts:147`) -- fluxo de causa-raiz
 * apontado por [DiagnosticEvaluation.primaryFlow]/[DiagnosticEvaluation.secondaryFlows].
 */
enum class DiagnosticEvaluationFlow(
    val wireValue: String,
) {
    ISP_EXTERNO("isp_externo"),
    WIFI_LOCAL("wifi_local"),
    DNS("dns"),
    FIBRA("fibra"),
    REDE_MOVEL("rede_movel"),
    HISTORICO_DEGRADACAO("historico_degradacao"),
    INTERNET_INSTAVEL("internet_instavel"),
    SEM_DADOS_SUFICIENTES("sem_dados_suficientes"),
    SAUDAVEL_MONITORAR("saudavel_monitorar"),
    ;

    companion object {
        fun fromWire(value: String): DiagnosticEvaluationFlow? = entries.firstOrNull { it.wireValue == value }
    }
}
