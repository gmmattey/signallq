---
title: "Páginas de feature — guia de autoria"
description: "Propósito, formato e regras das páginas por feature do SignallQ Android em docs/features/."
type: "técnico"
status: "ativo"
owner: "Marcelo"
last_updated: "2026-10-04"
version: "1.0.0"
---

# Páginas de feature

## Propósito

Uma página por feature de produto, com **negócio** (o que e por quê) e **técnico** (onde e como) no mesmo
arquivo, para que uma pessoa ou uma IA decida comportamento sem perguntar. O frontmatter é legível por
máquina: aponta arquivos, flags, eventos e testes reais.

Fontes de verdade, em caso de conflito: código e testes > esta página > `FUNCIONAL.md`/`TECNICO.md`.
Fato que não dá para verificar no código é removido ou marcado `não verificado`.

## Features (slugs)

`inicio-status`, `velocidade-resultado`, `assist-diagnostico`, `wifi-canais-sinal`, `wifi-casa`,
`dispositivos-rede`, `equipamento-internet`, `dns-ping`, `modo-gamer`, `historico-laudo`,
`monitoramento-alertas`, `perfil-ajustes-legal`.

Tipo `jornada`: faz parte de entender → diagnosticar → resolver → confirmar. Tipo `transversal`: sustenta
várias jornadas (monitoramento, ajustes). O slug é o nome estável da feature, separado do nome técnico do
overlay ou da classe (ex.: o overlay `SinalWifi` abre a feature `wifi-casa`).

## Como ler

1. Frontmatter: escopo e ponteiros (`arquivos`, `flags`, `eventos`, `testes`, `thresholds_em`).
2. **NEGÓCIO** (seções 1–6): problema, quando aparece, regras, estados, próximo passo, fora de escopo.
3. **TÉCNICO** (seções 7–12): mapa de código, dados, eventos/flags, falhas, testes, riscos.

## Como escrever

Copie `docs/templates/feature-page.md` para `docs/features/<slug>.md`. Campos de frontmatter:

| Campo | Regra |
|---|---|
| `title`, `description`, `type: "feature"`, `status`, `owner`, `last_updated`, `version` | obrigatórios; `status` ∈ ativo, draft, congelado, deprecated |
| `feature` | slug, igual ao nome do arquivo |
| `tipo` | `jornada` ou `transversal` |
| `modulos` | pastas de módulo Gradle (ver `android/settings.gradle.kts`) |
| `arquivos` | caminhos reais relativos à raiz do repo; lista curta, prefira diretórios |
| `contratos`, `adrs` | caminhos em `docs/CONTRATOS/` e `docs/archive/decisoes/`; ADR não é editado |
| `eventos`, `flags` | nomes exatamente como estão no código / `consumer-catalog.json` |
| `testes` | caminhos reais de teste |
| `thresholds_em` | caminho do arquivo onde os limiares vivem |

Exemplo mínimo (corpo resumido):

```markdown
---
title: "Exemplo de feature"
description: "Mostra se X está saudável, para quem não entende de redes."
type: "feature"
status: "ativo"
owner: "Marcelo"
last_updated: "2026-10-04"
version: "1.0.0"
feature: "exemplo"
tipo: "jornada"
modulos: [android/feature/exemplo]
arquivos: [android/feature/exemplo/src/main/kotlin/io/signallq/app/]
contratos: []
eventos: [exemplo_aberto]
flags: [consumer_exemplo_enabled]
testes: [android/feature/exemplo/src/test/]
adrs: []
thresholds_em: android/core/diagnostico/src/main/kotlin/io/signallq/app/ExemploThresholds.kt
---

# Exemplo de feature

## NEGÓCIO
### 1. Problema e promessa
Responde "X está bom?". Não promete causa raiz.
### 4. Estados e honestidade
| Estado | Texto / ação |
|---|---|
| sem permissão | explica e pede a permissão; não mostra valor |
| timeout | "Não consegui medir"; nunca vira sucesso |
## TÉCNICO
### 7. Mapa de código
| Responsabilidade | Módulo | Arquivo |
|---|---|---|
| UI | android/feature/exemplo | ExemploScreen.kt |
```

(Os caminhos e nomes acima são fictícios, só ilustram o formato.)

## Regras

- **Nunca copiar thresholds.** Aponte `thresholds_em`. Valor duplicado envelhece e vira segunda fonte de verdade.
- **Uma feature por PR.** Mantém revisão e validação proporcionais; não migre várias páginas de uma vez.
- Distinga sempre dado medido, inferência determinística e interpretação de IA (`AGENTS.md` §8).
- Ausência de dado nunca vira zero nem sucesso; timeout nunca é sucesso.
- Draft não se apresenta como entregue. Código vence doc.
- Frontmatter completo e `last_updated` atualizado a cada alteração; incremente `version`.
- Não crie índice manual das páginas: `INDICE.md` e o índice gerado cuidam disso.

## Como migrar de FUNCIONAL / MODULOS sem perder fatos

1. Liste as seções de `FUNCIONAL.md` (§4–5) e de `ARQUITETURA/MODULOS/*` que tratam da feature.
2. Para cada afirmação, confirme no código; corrija ou marque `não verificado`.
3. Distribua: comportamento e regras → NEGÓCIO; arquivos, contratos, eventos, testes → TÉCNICO e frontmatter.
4. Só depois de a página cobrir um fato, reduza o original a um resumo curto com link para a página. Nunca
   apague primeiro.
5. No mesmo PR, atualize os links que apontavam para o trecho movido e registre a origem no commit.
6. Fatos históricos sem efeito no comportamento atual saem da página, o git preserva (sem `_archive/`).
