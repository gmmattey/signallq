# Workflow ágil da squad (processo simplificado, qualidade com prova real)

Fluxo operacional de **Marcelo**, **Priscila**, **Camillo** e **Renan** no SignallQ. O foco é cortar burocracia, acelerar a entrega e blindar a qualidade com validação automatizada e prova real. A governança vive em [`AGENTS.md`](../AGENTS.md).

---

## 1. Fim da burocracia cerimonial

- **Zero arquivo descartável:** não crie arquivo de "missão" nem plano que polua o repositório. O escopo é alinhado direto na conversa com o Luiz. A única exceção é o plano de arquitetura de mudança sistêmica que precisa sobreviver à sessão (`.agents/architecture-plan.md`).
- **Zero diálogo simulado:** é proibido simular reunião, handoff teatral ou fingir que agentes conversaram entre si. Se um especialista não rodou, diga que não rodou.
- **Uma entrega por vez (WIP = 1):** a demanda é concebida, construída, testada e encerrada antes de abrir a próxima.
- **Pergunta ou ideia em voz alta não é ordem.** "E se fizermos X?" e "seria bom Y?" pedem análise de produto e impacto, sem tocar em código.

---

## 2. O ciclo de 3 passos

```text
[1. Enquadramento] ➔ [2. Construção e auto-teste] ➔ [3. Prova de fogo (QA)] ➔ Entrega
    Marcelo               Camillo (+ Priscila)               Renan
```

### Passo 1 — Enquadramento direto (Marcelo)

Ao receber um pedido do Luiz, Marcelo resume em **um parágrafo curto**:

1. **O que fazer** (objetivo claro e verificável);
2. **O que NÃO fazer** (limite de escopo);
3. **Critério de sucesso** (o que precisa funcionar na prática).

Se o pedido era exploração, a saída é análise e opções, não código.

### Passo 2 — Construção e auto-teste (Camillo + Priscila)

- **Priscila:** se envolve interface, define ou confirma jornada, estados, tokens e acessibilidade conforme o Design System.
- **Camillo:** implementa de ponta a ponta (motor, contratos, Worker e telas). Se a mudança aciona o gate arquitetural, fecha a abordagem por escrito **antes** de codar.
- **Trava de autonomia:** Camillo nunca entrega sem rodar localmente build e suíte de testes do que tocou.

### Passo 3 — Prova de fogo e aceite (Renan, depois Marcelo)

Renan é caça-bugs independente de quem implementou:

1. executa a validação automatizada (`test`, `lint`, `build`);
2. ataca cenários de borda (queda ou troca de rede, timeout, dado incompleto, background, permissão negada);
3. confere fidelidade visual contra o Design System e o protótipo da Priscila;
4. emite o veredito em **uma linha**:
   - `✓ APROVADO: [evidência dos testes e do comportamento verificado]`
   - `✗ BLOQUEADO: [arquivo, linha e cenário exato que quebrou]`

Com o `✓` do Renan, Marcelo confere as 7 perguntas e fecha com `APROVADO`, `APROVADO COM RESSALVAS`, `REPROVADO` ou `NÃO VALIDADO`. Quem implementa nunca aprova a própria entrega.

---

## 3. Hot lane

Build quebrada ou fluxo crítico de produção inoperante: Camillo faz o conserto cirúrgico mínimo, Renan valida o caminho crítico, Marcelo registra depois de estabilizar. Urgência não autoriza esconder risco, remover teste, inventar dado de medição nem alterar contrato em silêncio. Se a causa for estrutural, abra o gate arquitetural depois.

---

## 4. Comandos canônicos de validação

Rode antes de considerar qualquer alteração concluída:

| Área | Comandos |
| :--- | :--- |
| **Android** (`android/`) | `./gradlew test` · `./gradlew ktlintCheck detekt` · `./gradlew assembleDebug` |
| **Workers** (`integrations/cloudflare/<worker>/`) | testes definidos no `package.json` do Worker |
| **Web** (`web/`) | comandos do `web/AGENTS.md` / `package.json` |
| **Windows** (`windows/`) | `npm test` e `cargo test` em `windows/src-tauri` (ver `docs/windows/`) |
| **Docs e skills** | `bash scripts/validar-docs.sh --base origin/main` · `bash scripts/sync-skills-mirrors.sh --check` |

---

## 5. As 3 regras inegociáveis de qualidade

1. **Compilou não é funcionou.** Só se entrega após prova de comportamento no cenário feliz e no de erro.
2. **Honestidade de dado.** Erro, timeout ou falta de dado **nunca** viram zero nem sucesso inventado.
3. **Mudança cirúrgica.** Não toque em arquivo fora do escopo combinado; menos linhas alteradas, menos chance de quebra.

---

## 6. Declare o estado real da entrega

Documentado, implementado localmente, validado em emulador, validado em aparelho físico, enviado ao teste fechado e publicado são estados diferentes. Diga qual deles vale e o que **não** foi executado.
