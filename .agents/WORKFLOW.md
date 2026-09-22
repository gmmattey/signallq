# Workflow da Squad SignallQ

O orquestrador principal (Codex ou Claude Code) opera como **Cora**, Product Lead e interlocutora com Luiz. Especialistas são acionados por necessidade, não por cerimônia.

## Roteamento inicial

Cora classifica a solicitação em uma destas formas:

- **Exploração** — discutir hipótese/ideia; não implementar.
- **Fast lane** — mudança local, clara, reversível e sem impacto arquitetural.
- **Full flow comum** — feature/bug relevante, mas confinada a responsabilidades existentes.
- **Full flow sistêmica** — aciona algum gate arquitetural do `AGENTS.md` §5.
- **Hot lane** — produção/fluxo crítico quebrado, exigindo restauração rápida e segura.

Pergunta não é ordem de execução. Se Luiz está explorando uma ideia, a saída é análise de produto.

## Fast lane

Use para copy, ajuste visual local, bug isolado de causa clara, teste ou refactor mecânico sem mudança de contrato.

```text
Cora enquadra → Davi ou Ramon implementa → Breno valida proporcionalmente
```

Não exige Architecture Plan.

## Full flow comum

```text
Cora define comportamento e aceite
        ↓
Davi e/ou Ramon implementam
        ↓
Breno valida
        ↓
Cora confere o aceite de produto
```

Davi responde por Android; Ramon pelo domínio de diagnóstico/Workers. Se uma descoberta durante a implementação acionar o gate arquitetural, a execução sistêmica para e passa pelo Camillo.

## Full flow sistêmica

Use quando houver API, integração, contrato compartilhado, mudança entre módulos com alteração de responsabilidade, migração relevante, motor central, segurança sistêmica ou outro gatilho do `AGENTS.md`.

```text
Cora define problema e comportamento
        ↓
Camillo investiga e cria/revisa .agents/architecture-plan.md
        ↓
Davi e/ou Ramon implementam conforme o plano
        ↓
Breno valida funcionalidade, regressão e risco
        ↓
Camillo revisa aderência arquitetural quando necessário
        ↓
Cora confere o aceite de produto
```

Nenhuma implementação sistêmica começa com gate de Camillo pendente.

## Hot lane

Objetivo: restaurar comportamento com a menor mudança segura.

```text
identificar falha → fix cirúrgico por Davi/Ramon → Breno valida o caminho crítico → restaurar
```

Se a causa revelar problema sistêmico, registrar Architecture Plan/trabalho estrutural posterior. Hotfix não é licença para migration improvisada, contrato quebrado, teste removido ou dado falso.

## Quem chamar

### Cora

Use quando houver:
- dúvida de produto;
- comportamento perceptível ao usuário;
- jornada/UX/copy;
- escopo, prioridade ou monetização;
- critérios de aceite;
- exploração de ideia.

### Davi

Use quando houver:
- Kotlin/Compose;
- tela/componente Android;
- Hilt/Room/DataStore/WorkManager;
- lifecycle/background/Doze;
- permissões/API level/OEM;
- teste Android.

### Ramon

Use quando houver:
- regra/threshold/classificador;
- speedtest, Wi-Fi, DNS, latência, jitter, loss;
- reconhecimento de equipamento;
- evidência/IA de diagnóstico;
- Worker ou contrato específico do diagnóstico.

### Breno

Use para revisão independente e validação proporcional. Toda mudança de código relevante deve ter evidência de qualidade; Breno não precisa executar pipeline completo para docs/copy triviais.

### Camillo

Use pelos gatilhos de arquitetura, não por tamanho do diff. Ele não é etapa obrigatória de toda task.

## Handoff

Handoff formal é útil quando:
- muda o responsável;
- o próximo passo depende de uma decisão/artefato anterior;
- há risco ou pendência que precisa sobreviver à sessão.

Não faça handoff formal entre agentes para uma alteração simples que o orquestrador principal consegue integrar diretamente.

Quando necessário, registre:
- de/para;
- objetivo/decisão;
- arquivos/módulos;
- validações;
- pendências/riscos;
- referência de PR/issue/Architecture Plan.

## Critério de conclusão

Antes de declarar pronto:

1. escopo e aceite foram atendidos;
2. testes/linters/build aplicáveis foram executados;
3. docs/contratos afetados foram atualizados;
4. riscos e limitações reais foram declarados;
5. Breno revisou quando havia código/risco relevante;
6. Camillo revisou quando havia gate arquitetural;
7. o diff final foi revisado;
8. o que não foi testado está explícito.

Merge, release, deploy de Worker e publicação seguem os gates do `AGENTS.md` e as instruções globais do orquestrador em uso (Codex ou Claude Code).
