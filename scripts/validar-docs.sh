#!/usr/bin/env bash
set -uo pipefail

# Guardrail da documentacao. Existe porque regra sem enforcement foi exatamente
# como docs/ chegou a 235 arquivos com inventario defasado em varios deles.
#
# Uso:
#   scripts/validar-docs.sh                 valida so os .md alterados vs. origin/main
#   scripts/validar-docs.sh --base <ref>    idem, contra outra ref
#   scripts/validar-docs.sh --todos         valida a arvore inteira (relatorio)
#   scripts/validar-docs.sh --relatorio     so imprime cobertura, nunca falha
#
# Por padrao valida apenas o que a PR toca: exigir frontmatter de toda a arvore
# reprovaria 89 dos 119 documentos hoje. A divida antiga fica visivel em
# --relatorio e encolhe conforme os arquivos sao tocados; divida NOVA e barrada.

cd "$(dirname "$0")/.."

BASE="origin/main"
MODO="alterados"

while [[ $# -gt 0 ]]; do
  case "$1" in
    --base)      BASE="$2"; shift 2 ;;
    --todos)     MODO="todos"; shift ;;
    --relatorio) MODO="relatorio"; shift ;;
    *) echo "argumento desconhecido: $1" >&2; exit 2 ;;
  esac
done

# Diretorios onde .md e permitido. Qualquer outro lugar exige decisao explicita.
# web/ (site publico, migrado do repo signallq-web em 2026-10) tem docs e skills proprios.
ARVORE_PERMITIDA='^(docs/|\.claude/|\.github/|\.agents/|android/|integrations/|packages/|scripts/|brand/|web/|windows/|[A-Z_]+\.md$)'

CAMPOS_OBRIGATORIOS=(title description type status owner last_updated)

falhas=0
avisos=0

erro()  { echo "  ✗ $1"; falhas=$((falhas + 1)); }
aviso() { echo "  ⚠ $1"; avisos=$((avisos + 1)); }

# ---------------------------------------------------------------------------
# 1. Inventario gerado precisa estar em dia com o codigo
# ---------------------------------------------------------------------------
echo "→ inventário gerado vs. código"
if bash scripts/gerar-inventario-docs.sh --check >/tmp/inv.out 2>&1; then
  echo "  ✓ em dia"
else
  erro "bloco de inventário desatualizado — rode: scripts/gerar-inventario-docs.sh"
  sed 's/^/    /' /tmp/inv.out | head -12
fi

# ---------------------------------------------------------------------------
# 2. Espelhos de skill
# ---------------------------------------------------------------------------
echo "→ espelhos de skill"
if bash scripts/sync-skills-mirrors.sh --check >/tmp/mir.out 2>&1; then
  echo "  ✓ sincronizados"
else
  erro "espelhos divergem — rode: scripts/sync-skills-mirrors.sh"
  sed 's/^/    /' /tmp/mir.out | head -8
fi

# ---------------------------------------------------------------------------
# 3. Quais arquivos validar
# ---------------------------------------------------------------------------
if [[ "$MODO" == "alterados" ]]; then
  if ! git rev-parse --verify "$BASE" >/dev/null 2>&1; then
    echo "→ base '$BASE' inacessível; caindo para --todos"
    MODO="todos"
  fi
fi

case "$MODO" in
  alterados)
    ALVOS=$(git diff --name-only --diff-filter=d "$BASE"...HEAD -- '*.md' 2>/dev/null || true)
    echo "→ validando .md alterados vs. $BASE"
    ;;
  *)
    ALVOS=$(find docs -name '*.md' | sort)
    echo "→ validando árvore inteira"
    ;;
esac

# O frontmatter obrigatorio e regra de docs/. Fora dali cada arvore tem formato
# proprio — SKILL.md usa name/description exigidos pelo carregador de skills, e as
# regras em .claude/rules/ nao sao documentacao de produto. Impor o mesmo cabecalho
# ali quebraria ferramenta em troca de nada.
# docs/archive/ e docs/windows/ ficam fora do frontmatter obrigatorio: o primeiro e historico
# preservado como esta, o segundo tem formato proprio do produto Windows.
ALVOS=$(echo "$ALVOS" | grep -E '^docs/' | grep -vE '^docs/(archive|windows)/' || true)

# ---------------------------------------------------------------------------
# 4. Frontmatter obrigatorio
# ---------------------------------------------------------------------------
sem_fm=0
com_fm=0
if [[ -n "${ALVOS//[[:space:]]/}" ]]; then
  while read -r f; do
    [[ -z "$f" || ! -f "$f" ]] && continue

    if ! head -1 "$f" | grep -q '^---$'; then
      sem_fm=$((sem_fm + 1))
      [[ "$MODO" != "relatorio" ]] && erro "$f — sem frontmatter YAML"
      continue
    fi

    faltando=""
    for campo in "${CAMPOS_OBRIGATORIOS[@]}"; do
      sed -n '2,/^---$/p' "$f" | grep -qE "^$campo:" || faltando+="$campo "
    done

    if [[ -n "$faltando" ]]; then
      sem_fm=$((sem_fm + 1))
      [[ "$MODO" != "relatorio" ]] && erro "$f — frontmatter sem: $faltando"
    else
      com_fm=$((com_fm + 1))
    fi

    # status precisa ser um valor conhecido.
    # templates/ e isento: o campo la lista as opcoes possiveis, nao um valor.
    # archive/decisoes/ tem vocabulario proprio — decisao registrada nao e "ativa",
    # e "vigente" ou "registrado (historico)"; ADR usa aceito/proposto/rejeitado.
    if ! echo "$f" | grep -qE '^docs/(templates|archive/decisoes)/'; then
      st=$(sed -n '2,/^---$/p' "$f" | grep -E '^status:' | head -1 | sed 's/status: *//; s/"//g' || true)
      if [[ -n "$st" ]] && ! echo "$st" | grep -qE '^(ativo|draft|congelado|deprecated)$'; then
        aviso "$f — status desconhecido: '$st' (esperado: ativo|draft|congelado|deprecated)"
      fi
    fi
  done <<< "$ALVOS"
fi

# ---------------------------------------------------------------------------
# 4b. Paginas de feature (docs/features/*.md)
# ---------------------------------------------------------------------------
# O frontmatter dessas paginas e o mapa feature -> codigo que uma IA usa para se
# localizar; mapa com caminho morto e pior que mapa nenhum. Entao toda referencia
# do frontmatter precisa existir de verdade. Contrato: docs/features/README.md.
#
# Promover a cobertura de modulos de AVISO para FALHA quando as 12 paginas existirem:
#   FEATURES_COBERTURA_ESTRITA=1 bash scripts/validar-docs.sh   (ou default 1 desde a migração das 12 features; use FEATURES_COBERTURA_ESTRITA=0 para rebaixar a aviso)
COBERTURA_ESTRITA="${FEATURES_COBERTURA_ESTRITA:-1}"
# Modulos de infraestrutura que nao pertencem a nenhuma feature de produto.
# Lista explicita de proposito: modulo novo nao entra aqui por omissao.
MODULOS_INFRA=(
  android/core/database
  android/core/datastore
  android/core/permissions
  android/core/featureflags
)
CATALOGO_FLAGS="android/core/featureflags/src/main/resources/featureflags/consumer-catalog.json"

echo "→ páginas de feature"
# shellcheck source=lib-features.sh
source scripts/lib-features.sh
PAGINAS_FEAT=$(features_paginas)

if [[ "$MODO" == "alterados" ]]; then
  # Alem do diff contra a base, inclui paginas ainda nao commitadas: sem isso uma
  # pagina nova so seria validada depois do commit, que e tarde para o autor.
  ALVOS_FEAT=$( { echo "$ALVOS"; git ls-files -m -o --exclude-standard -- docs/features; } \
    | grep -E '^docs/features/[^/]+[.]md$' | grep -vE '/(README|INDEX)[.]md$' | sort -u || true)
else
  ALVOS_FEAT="$PAGINAS_FEAT"
fi

LIT_KT=""
literais_kotlin() {
  # Literais "snake_case" de todo o Kotlin do Android, uma vez por execucao.
  if [[ -z "$LIT_KT" ]]; then
    LIT_KT=$(mktemp)
    grep -rhoE '"[a-z][a-z0-9_]*"' android --include='*.kt' --exclude-dir=build 2>/dev/null \
      | tr -d '"' | sort -u > "$LIT_KT"
  fi
}

n_feat=0
if [[ -n "${ALVOS_FEAT//[[:space:]]/}" ]]; then
  while read -r f; do
    [[ -z "$f" || ! -f "$f" ]] && continue
    n_feat=$((n_feat + 1))
    base_f=$(basename "$f" .md)

    faltando=""
    for campo in feature tipo modulos arquivos contratos eventos flags testes adrs thresholds_em; do
      sed -n '2,/^---$/p' "$f" | grep -qE "^$campo:" || faltando+="$campo "
    done
    [[ -n "$faltando" ]] && erro "$f — frontmatter de feature sem: $faltando"

    slug=$(fm_escalar "$f" feature)
    [[ "$slug" != "$base_f" ]] && erro "$f — feature '$slug' difere do nome do arquivo '$base_f'"
    [[ "$(fm_escalar "$f" type)" != "feature" ]] && erro "$f — type deve ser \"feature\""
    tp=$(fm_escalar "$f" tipo)
    if [[ "$tp" != "jornada" && "$tp" != "transversal" ]]; then
      erro "$f — tipo '$tp' invalido (esperado: jornada|transversal)"
    fi

    # Caminhos: modulos, arquivos, contratos, testes (+ ponteiro thresholds_em).
    for campo in modulos arquivos contratos testes; do
      while IFS= read -r item; do
        [[ -z "$item" ]] && continue
        if [[ ! -e "$item" ]]; then
          # contratos pode vir relativo a docs/ (ex.: CONTRATOS/openapi/x.yaml)
          if [[ "$campo" == "contratos" && -e "docs/$item" ]]; then continue; fi
          erro "$f — $campo: caminho inexistente: $item"
        fi
      done < <(fm_lista "$f" "$campo")
    done
    th=$(fm_escalar "$f" thresholds_em)
    if [[ -n "$th" && ! -e "$th" ]]; then
      erro "$f — thresholds_em: caminho inexistente: $th"
    fi

    # Eventos: o nome e o primeiro token do item ("feature_used (feature_id=dns)").
    while IFS= read -r item; do
      [[ -z "$item" ]] && continue
      ev=${item%%[^a-z0-9_]*}
      [[ -z "$ev" ]] && { erro "$f — eventos: item sem nome de evento: $item"; continue; }
      literais_kotlin
      if grep -qxF "$ev" "$LIT_KT"; then continue; fi
      if grep -qF "$ev" docs/technical/analytics-events.md docs/technical/analytics-events-schema.md 2>/dev/null; then
        continue
      fi
      erro "$f — eventos: '$ev' não aparece no código Kotlin nem em technical/analytics-events*.md"
    done < <(fm_lista "$f" eventos)

    # Flags: chave no codigo Kotlin ou no catalogo de flags do Consumer.
    while IFS= read -r item; do
      [[ -z "$item" ]] && continue
      fl=${item%%[^a-zA-Z0-9_.-]*}
      [[ -z "$fl" ]] && { erro "$f — flags: item sem chave: $item"; continue; }
      literais_kotlin
      if grep -qxF "$fl" "$LIT_KT"; then continue; fi
      if [[ -f "$CATALOGO_FLAGS" ]] && grep -qF "\"$fl\"" "$CATALOGO_FLAGS"; then continue; fi
      erro "$f — flags: '$fl' não existe no código Kotlin nem em $CATALOGO_FLAGS"
    done < <(fm_lista "$f" flags)
  done <<< "$ALVOS_FEAT"
fi
[[ -n "$LIT_KT" ]] && rm -f "$LIT_KT"
echo "  $n_feat página(s) de feature validada(s)"

# INDEX.md e features.json sao gerados: divergir da fonte e erro (mesmo padrao do inventario).
if [[ -n "$PAGINAS_FEAT" || -e docs/features/INDEX.md ]]; then
  if bash scripts/gerar-features-index.sh --check >/tmp/feat-idx.out 2>&1; then
    echo "  ✓ INDEX.md e features.json em dia"
  else
    erro "docs/features/INDEX.md ou features.json desatualizado — rode: scripts/gerar-features-index.sh"
    sed 's/^/    /' /tmp/feat-idx.out | head -8
  fi
fi

# Cobertura: todo modulo Gradle precisa aparecer em alguma pagina de feature
# (ou estar na lista de infra). Aviso enquanto as 12 paginas nao existem.
settings=android/settings.gradle.kts
mods_gradle=$(sed -n '/^include(/,/^)/p' "$settings" | sed 's|//.*||' \
  | grep -oE '":[a-zA-Z0-9:_-]+"' | tr -d '"' | sort -u | while read -r alias; do
    dir=$(grep -E '^project\(' "$settings" | grep -F "(\"$alias\")" | head -1 | awk -F'"' '{print $4}')
    if [[ -z "$dir" ]]; then dir=${alias#:}; dir=${dir//://}; fi
    echo "android/$dir"
  done)
cobertos=$(for f in $PAGINAS_FEAT; do fm_lista "$f" modulos; done | sed 's|/$||' | sort -u)
sem_feature=""
while read -r m; do
  [[ -z "$m" ]] && continue
  printf '%s\n' "${MODULOS_INFRA[@]}" | grep -qxF "$m" && continue
  echo "$cobertos" | grep -qxF "$m" || sem_feature+="${m#android/} "
done <<< "$mods_gradle"
if [[ -n "$sem_feature" ]]; then
  msg="módulos Gradle sem nenhuma página de feature: $sem_feature"
  if [[ "$COBERTURA_ESTRITA" == "1" ]]; then erro "$msg"; else aviso "$msg (vira falha com FEATURES_COBERTURA_ESTRITA=1)"; fi
else
  echo "  ✓ todo módulo Gradle coberto por alguma feature"
fi

# Aviso: .kt listado em arquivos mudou na PR e a pagina da feature nao foi tocada.
if [[ "$MODO" == "alterados" ]]; then
  CHANGED_KT=$( { git diff --name-only "$BASE"...HEAD 2>/dev/null; git ls-files -m -o --exclude-standard android; } \
    | grep '[.]kt$' | sort -u || true)
  if [[ -n "$CHANGED_KT" ]]; then
    for f in $PAGINAS_FEAT; do
      echo "$ALVOS_FEAT" | grep -qxF "$f" && continue
      tocou=""
      while IFS= read -r item; do
        [[ -z "$item" ]] && continue
        if [[ "$item" == *.kt ]]; then
          echo "$CHANGED_KT" | grep -qxF "$item" && tocou+="${item##*/} "
        else
          echo "$CHANGED_KT" | grep -qF "${item%/}/" && tocou+="${item%/}/ "
        fi
      done < <(fm_lista "$f" arquivos)
      [[ -n "$tocou" ]] && aviso "$f — código listado mudou ($tocou) mas a página não foi tocada"
    done
  fi
fi

# ---------------------------------------------------------------------------
# 5. .md fora da arvore permitida
# ---------------------------------------------------------------------------
echo "→ localização dos .md"
fora=$(git ls-files '*.md' | grep -vE "$ARVORE_PERMITIDA" || true)
if [[ -n "$fora" ]]; then
  while read -r f; do erro "$f — .md fora da árvore permitida"; done <<< "$fora"
else
  echo "  ✓ nenhum fora do lugar"
fi

# ---------------------------------------------------------------------------
# 6a. Contagem declarada no INDICE bate com o disco
# ---------------------------------------------------------------------------
# O indice cita pastas como "`operations/` (26)". Se alguem adiciona um documento
# e nao atualiza o indice, a contagem diverge — e isso e o que se quer pegar.
# Exigir link nominal para cada um dos 22 documentos de decisions/ seria
# burocracia sem retorno.
echo "→ contagens declaradas no INDICE.md"
divergencias=0
while read -r linha; do
  dir=$(echo "$linha" | grep -oE '`[a-zA-Z/_-]+/`' | head -1 | tr -d '`')
  num=$(echo "$linha" | grep -oE '\(([0-9]+)\)' | head -1 | tr -d '()')
  [[ -z "$dir" || -z "$num" ]] && continue
  [[ ! -d "docs/$dir" ]] && continue
  real=$(find "docs/$dir" -maxdepth 1 -name '*.md' | wc -l | tr -d ' ')
  if [[ "$real" != "$num" ]]; then
    erro "INDICE.md declara $dir com $num documentos; o disco tem $real"
    divergencias=$((divergencias + 1))
  fi
done < <(grep -E '^#{2,3} .*`[a-zA-Z/_-]+/`.*\([0-9]+\)' docs/INDICE.md || true)
[[ $divergencias -eq 0 ]] && echo "  ✓ contagens conferem"

# ---------------------------------------------------------------------------
# 6b. Pasta nova nao mencionada em lugar nenhum
# ---------------------------------------------------------------------------
echo "→ pastas de docs/ citadas no índice"
naocitadas=0
for d in docs/*/; do
  nome=$(basename "$d")
  if ! grep -qE "$nome/" docs/INDICE.md docs/README.md 2>/dev/null; then
    erro "docs/$nome/ existe mas não é citada em INDICE.md nem README.md"
    naocitadas=$((naocitadas + 1))
  fi
done
[[ $naocitadas -eq 0 ]] && echo "  ✓ todas citadas"

# Documento solto na raiz de docs/ precisa de citacao nominal.
echo "→ documentos na raiz de docs/"
soltos=0
for f in docs/*.md; do
  nome=$(basename "$f")
  [[ "$nome" == "INDICE.md" || "$nome" == "README.md" ]] && continue
  if ! grep -qF "$nome" docs/INDICE.md docs/README.md 2>/dev/null; then
    erro "$f — documento na raiz sem citação em INDICE.md nem README.md"
    soltos=$((soltos + 1))
  fi
done
[[ $soltos -eq 0 ]] && echo "  ✓ todos citados"

# ---------------------------------------------------------------------------
# 7. Resumo
# ---------------------------------------------------------------------------
echo
if [[ "$MODO" != "alterados" ]]; then
  tot=$(find docs -name '*.md' | wc -l | tr -d ' ')
  echo "cobertura de frontmatter: $com_fm de $((com_fm + sem_fm)) verificados (árvore: $tot documentos)"
fi
echo "falhas: $falhas · avisos: $avisos"

if [[ "$MODO" == "relatorio" ]]; then
  echo "(modo relatório — não falha)"
  exit 0
fi

[[ $falhas -gt 0 ]] && exit 1
exit 0
