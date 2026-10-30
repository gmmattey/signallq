# ADR-002: Ktlint + Detekt como ferramentas de qualidade automática

**Data:** 2026-05-24  
**Status:** Accepted

## Contexto

O projeto herdava sem instrumentação de qualidade de código. À medida que crescem contributors e features, manutenção manual de estilo e padrões fica insustentável:
- Inconsistência visual (indentação, espaçamento, imports)
- Padrões ruins não detectados (variáveis não usadas, lógica redundante)
- Code review manual lenta e subjetiva

## Decisão

Integrar **Ktlint** (style guide automático para Kotlin) + **Detekt** (análise estática de bugs e anti-patterns).

**Implementação:**
- **Ktlint:** enforcement via plugin Gradle com regras customizadas em `.editorconfig`
  - Auto-fix: `./gradlew ktlintFormat`
  - CI: bloqueia merge se houver violações
  - Regras: baseadas em Kotlin official style guide
  
- **Detekt:** análise estática via plugin Gradle com config em `detekt.yml`
  - CI: relatório de issues (severity high/medium/low)
  - Customizável por baseline (PR #26 estabeleceu baseline de issues conhecidas)

**Benefícios:**
- Estilo consistente sem discussão manual
- Detecção automática de bugs comuns
- CI verde automaticamente limpa quando regras são respeitadas
- Menores PRs (estilo já limpo antes de review)

## Consequências

- **Impacto:** Issue #29 resolveu 55 violações (33 auto-fix + 22 manuais)
- **Workflow:** Developers rodam `./gradlew ktlintFormat` antes de commit
- **Curva de aprendizado:** Mínima; erro de estilo é imediato no IDE
- **Baseline:** Estabelecido em PR #26; novos problemas bloqueiam merge

## Referências

- PR #26: Feat(infra) configurar Detekt + Ktlint + workflow CI (Issue #5)
- Issue #29: Chore(ktlint) resolver 22 violações manuais residuais
- `.editorconfig` — regras customizadas
- `detekt.yml` — configuração Detekt
