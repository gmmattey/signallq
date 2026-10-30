# ADR-008: Features futuras (#952, #951) usam arquitetura D1-only, sem R2/KV/Cron Trigger/Queue

**Data:** 2026-07-16
**Status:** Accepted

## Contexto

O lote de execução do squad de 2026-07-16 incluía um grupo "Features futuras já bem adiantadas"
com duas issues épicas:

- **#952** — Motor de diagnóstico remoto no Cloudflare com fallback local versionado. Arquitetura
  original: Worker + **D1** (regras, versões, auditoria) + **KV ou R2** (pacote de ruleset
  publicado e imutável).
- **#951** — Diretório remoto de provedores com canais oficiais e atualização automática.
  Arquitetura original: Worker + **D1** (cadastro/canais/aliases/ASN/auditoria) + **R2** (logos e
  assets de marca) + **Cloudflare Cron Trigger** (revisão mensal) + **Cloudflare Queue**
  (enriquecimento e revalidação assíncronos).

Ambas as issues já se declaram explicitamente como épico ("quebrar em issues menores antes da
implementação", "não deve ser implementada em um único PR") — este ADR não substitui essa quebra,
só resolve o bloqueio de custo/arquitetura que impedia sequer começar a planejar a quebra.

Luiz decidiu (2026-07-16, conversa) que qualquer componente que introduza **custo novo** de
infraestrutura deve ser cortado, mantendo uma solução alternativa via **D1** (já em uso no
projeto, sem custo adicional).

## Decisão

Cortar todo componente Cloudflare pago/novo além do D1 já existente:

### #952 — Motor de diagnóstico remoto

- ~~KV ou R2 para o pacote de ruleset publicado~~ → ruleset publicado vira uma linha em tabela D1
  (`rulesets`: versão, JSON do ruleset, status de publicação, metadados, auditoria). O Worker lê a
  versão ativa direto do D1 em vez de resolver um ponteiro KV/objeto R2.
- Resto da arquitetura proposta no corpo da issue (contrato de snapshot, formato declarativo de
  regra, shadow mode, rollout gradual, kill switch, testes dourados compartilhados) não depende de
  KV/R2 e continua válido sem alteração.

### #951 — Diretório remoto de provedores

- ~~R2 para logos e assets de marca~~ → o cadastro guarda apenas a **URL do logo oficial** como
  coluna em D1 (`providers.logo_url` ou equivalente); o app carrega a imagem direto da fonte
  cadastrada. Isso remove o controle de proporção/otimização/versionamento de asset que o design
  original tinha via R2 — fica como evolução futura se o custo for aprovado depois.
- ~~Cloudflare Cron Trigger mensal~~ → a rotina de revisão periódica de provedores desatualizados
  sai do MVP. Fica como acionamento manual (endpoint administrativo chamado sob demanda) até uma
  decisão futura sobre automação.
- ~~Cloudflare Queue para enriquecimento assíncrono~~ → o fluxo de enriquecimento (buscar
  domínio/SAC/WhatsApp oficiais quando um provedor novo aparece) sai do MVP como automação. Vira
  candidato manual revisado no Console, sem fila de background.

## Consequências

- Nenhuma infraestrutura Cloudflare nova além do D1 (já usado pelo `signallq-admin-worker`) é
  necessária para viabilizar as duas features — sem custo novo de armazenamento (R2/KV) nem de
  automação (Cron/Queue).
- **#951 perde a característica central de "diretório vivo com atualização automática"** no MVP —
  a atualização mensal e o enriquecimento assíncrono por volume de detecção (o coração da proposta
  original) ficam fora até uma decisão futura de custo. O que resta no MVP é essencialmente um
  cadastro D1 consultável por API, com curadoria manual via Console — mais próximo de uma evolução
  do catálogo local atual do que do "diretório vivo" descrito no corpo da issue.
- Assets de logo carregados diretamente da fonte oficial (sem cópia pro R2) reintroduzem o risco de
  hotlink/link quebrado que o desenho original com R2 evitava explicitamente (regra 4 do corpo da
  issue: "não fazer hotlink para o site da operadora") — precisa ser resolvido na quebra em
  subissues (ex.: fallback visual quando a URL externa falhar).
- Ambas as issues continuam sendo épicos multi-fase. Este ADR não autoriza implementação direta —
  a quebra em subissues por fase continua obrigatória antes de qualquer PR de código.

## Referências

- #952 — Motor de diagnóstico remoto no Cloudflare (comentário de 2026-07-16 registra o desvio
  deste ADR).
- #951 — Diretório remoto de provedores (comentário de 2026-07-16 registra o desvio deste ADR).
- `.claude/CLAUDE.md`, seção "Autonomia dos Agentes" — custo novo e alteração arquitetural
  relevante exigem aprovação do Luiz; esta decisão já foi aprovada por ele em conversa.
