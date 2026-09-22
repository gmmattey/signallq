#!/usr/bin/env bash
set -euo pipefail

# Fonte canônica: .claude/skills/. O script sincroniza os espelhos de compatibilidade
# usados pelo Codex (.agents/skills) e pelo GitHub/Copilot (.github/skills).
# Nunca crie regra exclusiva em um espelho: edite .claude/skills e sincronize.
#
# Uso: scripts/sync-skills-mirrors.sh [--check]
#   --check  não escreve; falha se um espelho divergir da fonte canônica.

cd "$(dirname "$0")/.."

CANONICAL=".claude/skills"
MIRRORS=(".agents/skills" ".github/skills")
CHECK_ONLY=false

if [[ "${1:-}" == "--check" ]]; then
  CHECK_ONLY=true
fi

status=0

for mirror in "${MIRRORS[@]}"; do
  if [[ "$CHECK_ONLY" == true ]]; then
    # README.md é específico do diretório. Pastas chamadas agents dentro de skills
    # podem conter metadados de integração específicos da ferramenta (ex.: impeccable).
    diff_out=$(diff -rq --exclude=README.md --exclude=agents "$CANONICAL" "$mirror" 2>&1 || true)
    if [[ -n "$diff_out" ]]; then
      echo "desatualizado: $mirror diverge de $CANONICAL"
      echo "$diff_out"
      status=1
    fi
  else
    mkdir -p "$mirror"
    # Copia procedimentos compartilhados. Metadados extras já existentes no destino
    # não são apagados; eles não têm autoridade de governança.
    cp -R "$CANONICAL/." "$mirror/"
    echo "sincronizado: $mirror"
  fi
done

exit $status
