---
name: delegar-subagente
description: Delega tarefas da squad do SignallQ entre Giam, Marco, Priscila, Camillo, Bia e Renan com objetivo, escopo, permissões e retorno verificáveis.
---

# Skill: delegar-subagente

Use esta skill quando uma etapa do trabalho precisar do especialista titular da posição ou quando o paralelismo acelerar a entrega sem conflito de arquivos.

Os titulares da squad vivem em `.codex/agents/`:

- **Giam (`giam`)** — Braço direito do Luiz e Product Owner. Entende a demanda, filtra escopo, define critérios de aceite e dá o veredito final (`APROVADO`, `APROVADO COM RESSALVAS`, `REPROVADO` ou `NÃO VALIDADO`). Somente leitura.
- **Marco (`marco`)** — Capitão da oficina e orquestrador. Recebe o pedido do Giam, organiza a fila, aciona os especialistas na ordem certa, protege o WIP e consolida a entrega.
- **Priscila (`priscila`)** — Diretora de UX/UI e Guardiã do Protótipo Apple. Especifica e audita jornadas, visual, acessibilidade e adaptação para iPhone, iPad, Mac e Web. Somente leitura.
- **Camillo (`camillo`)** — Principal Engineer de Motor, Rede e Arquitetura. Cuida de `SignallQEngine`, pacotes Swift compartilhados, diagnóstico de rede, Assist, contratos e persistência. Escrita apenas quando autorizada.
- **Bia (`bia`)** — Engenheira de Telas e Experiência Apple. Constrói interfaces SwiftUI/AppKit/UIKit e web 1:1 com o protótipo da Priscila, adaptadores e preparação local de candidata. Escrita apenas quando autorizada; só começa com plano fechado.
- **Renan (`renan`)** — Qualidade, Aparelho Real e Caça-Bugs. Tenta quebrar a entrega, audita testes, estados adversos, segurança, privacidade e fidelidade visual em iPhone/iPad/Mac antes do aceite do Giam. Somente leitura por padrão.

## Antes de delegar

Defina com clareza:

1. objetivo concreto da rodada;
2. contexto mínimo (decisão do Giam, desenho da Priscila ou plano do Camillo);
3. arquivos/módulos de leitura;
4. escopo de escrita (se for Camillo ou Bia);
5. restrições de segurança e preservação de WIP;
6. formato de retorno esperado.

Nunca coloque Camillo e Bia escrevendo nos mesmos arquivos ao mesmo tempo.

## Mensagem mínima de passagem de bastão

```text
Especialista: <giam|marco|priscila|camillo|bia|renan>
Objetivo: <entrega concreta>
Escopo de leitura: <arquivos, módulos ou fontes>
Escopo de escrita: <somente leitura ou arquivos/módulos autorizados>
Restrições: preserve WIP existente; sem merge, deploy, publicação, segredo ou exclusão destrutiva.
Retorno: evidências, arquivos alterados (quando houver), comandos/testes executados, riscos e o que não foi testado.
```

## Travas de retorno

- **Para Renan:** exigir classificação `BLOQUEIA`, `AJUSTA`, `ISSUE_FUTURA` ou `SEM ACHADOS MATERIAIS` com prova reproduzível.
- **Para Giam (Aceite Final):** enviar somente após a implementação e a revisão do Renan, exigindo veredito `APROVADO`, `APROVADO COM RESSALVAS`, `REPROVADO` ou `NÃO VALIDADO`.
- **Para Bia e Camillo:** o Marco confere o `git diff` e os testes antes de passar ao Renan.
