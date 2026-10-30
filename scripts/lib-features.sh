#!/usr/bin/env bash
# Leitura do frontmatter das paginas docs/features/*.md. Fonte unica do parser,
# usada por gerar-features-index.sh e validar-docs.sh para os dois nao divergirem
# sobre o que significa "lista de modulos" ou "slug".
#
# O frontmatter e YAML restrito: escalares `chave: "valor"` e listas em bloco
# (`chave:` seguido de linhas `  - "item"`) ou vazias (`chave: []`). Nada alem disso
# e suportado — e o formato do contrato em docs/features/README.md.
# CRLF e removido na leitura: no Windows (core.autocrlf=true) os .md ficam com CR.

# Paginas de feature (exclui README e INDEX), uma por linha, ordenadas.
features_paginas() {
  local f
  for f in docs/features/*.md; do
    [[ -e "$f" ]] || continue
    case "$(basename "$f")" in README.md|INDEX.md) continue ;; esac
    echo "$f"
  done | sort
}

# Valor escalar do campo, sem aspas.
fm_escalar() {
  tr -d '\r' < "$1" | awk -v c="$2" '
    NR == 1 { next }
    /^---$/ { exit }
    index($0, c ":") == 1 {
      v = substr($0, length(c) + 2)
      sub(/^[ \t]+/, "", v); sub(/[ \t]+$/, "", v)
      gsub(/^"|"$/, "", v)
      print v; exit
    }'
}

# Itens da lista do campo, um por linha, sem aspas.
fm_lista() {
  tr -d '\r' < "$1" | awk -v c="$2" '
    NR == 1 { next }
    /^---$/ { exit }
    emlista && /^[ \t]+-[ \t]/ {
      v = $0; sub(/^[ \t]+-[ \t]+/, "", v); sub(/[ \t]+$/, "", v)
      gsub(/^"|"$/, "", v)
      print v; next
    }
    emlista && /^[^ \t-]/ { emlista = 0 }
    index($0, c ":") == 1 { emlista = ($0 !~ /\[[ \t]*\]/) }'
}
