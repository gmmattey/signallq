#!/usr/bin/env bash
set -euo pipefail

# Gera docs/features/INDEX.md e docs/features/features.json a partir do
# frontmatter de docs/features/*.md (exceto README.md e INDEX.md).
#
# Existe para uma IA (ou pessoa) achar a pagina certa lendo ~40 linhas em vez de
# varrer FUNCIONAL.md e 22 docs de modulo. Indice mantido a mao diverge da pagina
# que ele resume — entao ele e gerado, e o CI reprova se alguem editar um sem o outro.
#
# Uso: scripts/gerar-features-index.sh [--check]
#   (sem flag)  reescreve INDEX.md e features.json
#   --check     nao escreve; sai 1 se algum dos dois estiver desatualizado

cd "$(dirname "$0")/.."
# shellcheck source=lib-features.sh
source scripts/lib-features.sh

CHECK_ONLY=false
[[ "${1:-}" == "--check" ]] && CHECK_ONLY=true

DEST_MD="docs/features/INDEX.md"
DEST_JSON="docs/features/features.json"

# Escapa para string JSON: barra invertida e aspas (valores aqui nao tem quebra de linha).
jstr() { local v=$1; v=${v//\\/\\\\}; v=${v//\"/\\\"}; printf '"%s"' "$v"; }

# Lista bash -> array JSON numa linha.
jlista() {
  local f="$1" c="$2" out="" item
  while IFS= read -r item; do
    [[ -z "$item" ]] && continue
    out+="${out:+, }$(jstr "$item")"
  done < <(fm_lista "$f" "$c")
  printf '[%s]' "$out"
}

PAGINAS=$(features_paginas)

# --- features.json -----------------------------------------------------------
json() {
  local f slug primeiro=true
  echo '{'
  echo '  "gerado_por": "scripts/gerar-features-index.sh",'
  echo '  "features": ['
  while IFS= read -r f; do
    [[ -z "$f" ]] && continue
    slug=$(fm_escalar "$f" feature)
    $primeiro || echo ','
    primeiro=false
    printf '    {\n'
    printf '      "slug": %s,\n' "$(jstr "$slug")"
    printf '      "title": %s,\n' "$(jstr "$(fm_escalar "$f" title)")"
    printf '      "tipo": %s,\n' "$(jstr "$(fm_escalar "$f" tipo)")"
    printf '      "status": %s,\n' "$(jstr "$(fm_escalar "$f" status)")"
    printf '      "owner": %s,\n' "$(jstr "$(fm_escalar "$f" owner)")"
    printf '      "arquivo": %s,\n' "$(jstr "$f")"
    for c in modulos arquivos contratos eventos flags testes adrs; do
      printf '      "%s": %s,\n' "$c" "$(jlista "$f" "$c")"
    done
    printf '      "thresholds_em": %s\n' "$(jstr "$(fm_escalar "$f" thresholds_em)")"
    printf '    }'
  done <<< "$PAGINAS"
  echo
  echo '  ]'
  echo '}'
}

# --- INDEX.md ----------------------------------------------------------------
# last_updated e a maior data entre as paginas, nao a data de hoje: o --check precisa
# ser deterministico, e a data de geracao o faria falhar no dia seguinte.
indice() {
  local f slug nome mods ultima="" d n=0
  while IFS= read -r f; do
    [[ -z "$f" ]] && continue
    n=$((n + 1))
    d=$(fm_escalar "$f" last_updated)
    [[ "$d" > "$ultima" ]] && ultima="$d"
  done <<< "$PAGINAS"
  [[ -z "$ultima" ]] && ultima="1970-01-01"

  cat <<CAB
---
title: "Índice de features"
description: "Tabela gerada de todas as páginas de feature: slug, nome, tipo, status, módulos e arquivo."
type: "índice"
status: "ativo"
owner: "Squad"
last_updated: "$ultima"
version: "1.0.0"
---

# Índice de features

<!-- GERADO por scripts/gerar-features-index.sh — não edite à mão. Fonte: frontmatter de cada página. -->

$n feature(s). Abra a página da feature antes de tocar no código dela; \`features.json\` tem o mesmo conteúdo com arquivos, eventos, flags e testes. Formato das páginas: [\`README.md\`](README.md).

| Slug | Nome | Tipo | Status | Módulos | Arquivo |
|---|---|---|---|---|---|
CAB
  while IFS= read -r f; do
    [[ -z "$f" ]] && continue
    slug=$(fm_escalar "$f" feature)
    nome=$(fm_escalar "$f" title | sed 's/^Feature[[:space:]]*[—-][[:space:]]*//')
    mods=$(fm_lista "$f" modulos | sed 's|^android/||' | paste -sd',' - | sed 's/,/, /g')
    printf '| `%s` | %s | %s | %s | %s | [%s](%s) |\n' \
      "$slug" "$nome" "$(fm_escalar "$f" tipo)" "$(fm_escalar "$f" status)" \
      "$mods" "$(basename "$f")" "$(basename "$f")"
  done <<< "$PAGINAS"
}

TMP_MD=$(mktemp); TMP_JSON=$(mktemp)
trap 'rm -f "$TMP_MD" "$TMP_JSON"' EXIT
indice > "$TMP_MD"
json > "$TMP_JSON"

# Mesmo tratamento de CRLF do gerar-inventario-docs.sh: comparar ignorando CR e,
# ao escrever, preservar a convencao que o arquivo ja usa.
status=0
aplicar() {
  local tmp="$1" alvo="$2"
  if [[ -f "$alvo" ]] && grep -q $'\r' "$alvo" 2>/dev/null; then
    sed -i 's/\r*$/\r/' "$tmp"
  fi
  if [[ "$CHECK_ONLY" == true ]]; then
    if [[ ! -f "$alvo" ]] || ! diff -q --strip-trailing-cr "$alvo" "$tmp" >/dev/null; then
      echo "desatualizado: $alvo — rode: scripts/gerar-features-index.sh"
      [[ -f "$alvo" ]] && diff --strip-trailing-cr "$alvo" "$tmp" | head -10
      status=1
    fi
  else
    cp "$tmp" "$alvo"
    echo "atualizado: $alvo"
  fi
}
aplicar "$TMP_MD" "$DEST_MD"
aplicar "$TMP_JSON" "$DEST_JSON"

exit $status
