#!/usr/bin/env python3
"""Verifica a aderencia do codigo Android ao De-Para editorial da issue #1502.

Compara cada linha aplicavel de `de_para.csv` (extrato somente-leitura da aba
De-Para da planilha "levantamento_textos_diagnostico_signallq_de_para_CORRIGIDO.xlsx",
fornecida por Luiz) contra o estado atual do repositorio.

Este script e insumo de desenvolvimento/auditoria -- nao roda no app, nao e
empacotado no APK e nao deve virar dependencia de runtime (ver instrucoes da
issue #1502). Uso:

    python3 scripts/de-para-1502/verify_de_para.py [--repo-root .] [--json out.json]

Saida: contagem por status (aplicado / ja_aplicado_antes / nao_aplicavel /
nao_localizado / divergencias) e lista de divergencias restantes.
"""
from __future__ import annotations

import argparse
import csv
import json
import re
import sys
from collections import Counter
from pathlib import Path

# IDs cujo "Texto atual"/"Texto recomendado" sao apenas a DESCRICAO de um slot
# dinamico (ex.: "{titulo da decisao do motor}") -- decisao Manter/Definir regra,
# nada a comparar literalmente no codigo.
DYNAMIC_DESCRIPTOR_NATURES = {
    "Dinâmico IA",
    "Dinâmico externo",
    "Dinâmico do componente",
    "Dinâmico determinístico",
}

# Linhas verificadas manualmente durante a implementacao da #1502 cujo De-Para
# nao pode ser confirmado por correspondencia textual simples no arquivo-fonte
# indicado pela planilha -- ver relatorio de aderencia na issue #1502 para o
# raciocinio linha a linha. Mantido explicito aqui para o script nunca fingir
# 100% de cobertura automatica.
KNOWN_MANUAL_IDS = {
    26, 28, 29,  # Conflito: vocabulario canonico de severidade (MetricStatusUi.kt) - nao aplicado, decisao pendente de produto (ver relatorio de aderencia).
    63,  # Erro de concordancia no texto recomendado da planilha ("Um variacao...") corrigido para "Uma variacao... alta" ao aplicar.
    65,  # EXCECAO POR REVISAO DE CONFIABILIDADE (revisao independente da PR #1515) -- o
         # "Texto recomendado" da planilha mantinha "acima da referencia Anatel RQUAL",
         # mas essa citacao nao tem fonte normativa comprovada no repositorio (a unica
         # documentacao real de "Anatel RQUAL" -- docs/01-funcional.md -- descreve um
         # criterio diferente, % de velocidade do plano contratado, sem relacao com
         # latencia). A decisao aprovada na planilha foi superada: a mensagem aplicada
         # remove a alegacao regulatoria, mantendo limiar (100ms) e severidade
         # (attention) inalterados -- ver InternetDiagnosticEngine.kt e o relatorio de
         # aderencia atualizado na PR #1515 para o raciocinio completo.
    177, 546,  # "Acoes recomendadas" -- movido de DiagnosticoResultadoComponents.kt para ModoGamerConfigResultadoSection.kt.
    190, 191, 192, 193,  # "Relato: melhora sem Wi-Fi" -- rotulo/valor separados em DiagnosticoGuiadoEngine.kt.
    # ID 308 ("DNS" -> "Tempo para localizar sites") nao esta nesta lista -- continua
    # classificando sozinho como aplicado_parcial_ou_ambiguo (o rotulo recomendado
    # continua presente; "dns" tambem aparece como substring dentro da nova sublinha
    # "Servidor DNS: ..." descrita no ID 309 abaixo, mesma familia de falso-positivo de
    # colisao de substring ja documentada para outros IDs desse bucket).
    309,  # Superado pela correcao do ID 308 (revisao independente da PR #1515): a
          # decisao original "Manter DNS ({provedor ou IP})" ja contradizia o Ajustar da
          # 308 no mesmo call site; agora nem o formato antigo "DNS (X)" nem o template
          # literal da planilha se aplicam -- o servidor vira uma sublinha propria
          # "Servidor DNS: {nome ou ip}", nunca sufixo da frase principal.
    411, 419,  # Erro de concordancia no texto recomendado da planilha ("ao operadora") corrigido para "a operadora"/"à operadora" ao aplicar.
    501,  # "Medicao de ping" -- rotulo/valor reestruturados em ModoGamerEngine.kt.
}


def normalize(text: str) -> str:
    if text is None:
        return ""
    text = re.sub(r'["\'+\\]', "", text)
    text = re.sub(r"\s+", " ", text)
    return text.strip().lower()


def literal_fragments(text: str) -> list[str]:
    parts = re.split(r"\{[^}]*\}|%[sd0-9.]*", text)
    out = []
    for part in parts:
        cleaned = part.strip(" :,%").strip()
        if len(normalize(cleaned)) >= 3:
            out.append(cleaned)
    return out


def load_rows(csv_path: Path) -> list[dict]:
    with csv_path.open(encoding="utf-8") as fh:
        return list(csv.DictReader(fh))


def classify(row: dict, repo_root: Path, file_cache: dict[str, str | None]) -> str:
    atual = row["Texto atual"] or ""
    recom = row["Texto recomendado"] or ""
    arquivo = row["Arquivo-fonte"] or ""
    decisao = row["Decisão"]
    natureza = row["Natureza"] or ""

    if natureza in DYNAMIC_DESCRIPTOR_NATURES or (
        atual.strip().startswith("{") and atual.strip().endswith("}") and not literal_fragments(atual)
    ):
        return "dinamico"

    if not arquivo:
        return "sem_arquivo_fonte"

    if arquivo not in file_cache:
        full = repo_root / arquivo
        file_cache[arquivo] = full.read_text(encoding="utf-8") if full.exists() else None
    content = file_cache[arquivo]

    if content is None:
        return "removido_do_codigo"

    n_content = normalize(content)
    n_atual = normalize(atual)
    n_recom = normalize(recom)

    atual_presente = bool(n_atual) and n_atual in n_content
    recom_presente = bool(n_recom) and n_recom in n_content

    if decisao == "Manter":
        if atual_presente:
            return "manter_confirmado"
        fa = literal_fragments(atual)
        if fa and all(normalize(frag) in n_content for frag in fa):
            return "manter_confirmado"
        if int(row["ID"]) in KNOWN_MANUAL_IDS:
            return "verificado_manualmente"
        return "manter_divergente"

    if atual == recom:
        return "identico_sem_mudanca"

    if recom_presente and not atual_presente:
        return "aplicado"
    if recom_presente and atual_presente:
        # Ambos aparecem -- comum quando o mesmo texto ocorre em mais de um
        # lugar do arquivo e so parte foi migrada, ou quando a colisao e uma
        # substring generica (ex.: "Download" dentro de "Velocidade de download").
        return "aplicado_parcial_ou_ambiguo"

    if int(row["ID"]) in KNOWN_MANUAL_IDS:
        return "verificado_manualmente"

    fa = literal_fragments(atual)
    fr = literal_fragments(recom)
    if fa:
        found_r = sum(1 for frag in fr if normalize(frag) in n_content)
        if fr and found_r == len(fr):
            return "aplicado"
        found_a = sum(1 for frag in fa if normalize(frag) in n_content)
        if found_a == len(fa):
            return "nao_aplicado"

    return "divergencia"


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--repo-root", default=".", help="Raiz do repositorio (default: diretorio atual)")
    parser.add_argument("--csv", default=None, help="Caminho do de_para.csv (default: ao lado deste script)")
    parser.add_argument("--json", default=None, help="Se definido, grava o relatorio completo em JSON neste caminho")
    args = parser.parse_args()

    script_dir = Path(__file__).resolve().parent
    csv_path = Path(args.csv) if args.csv else script_dir / "de_para.csv"
    repo_root = Path(args.repo_root).resolve()

    rows = load_rows(csv_path)
    file_cache: dict[str, str | None] = {}

    counts = Counter()
    divergencias = []
    for row in rows:
        status = classify(row, repo_root, file_cache)
        counts[status] += 1
        if status in ("divergencia", "nao_aplicado", "manter_divergente"):
            divergencias.append(row)

    total_ajustaveis = sum(1 for r in rows if r["Decisão"] not in ("Manter",))

    print("=== Verificacao De-Para issue #1502 ===")
    print(f"Total de linhas na aba De-Para: {len(rows)}")
    print(f"Total de itens ajustaveis (decisao != Manter): {total_ajustaveis}")
    print()
    for status, qty in counts.most_common():
        print(f"  {status:32s} {qty}")
    print()
    if divergencias:
        print(f"Divergencias restantes: {len(divergencias)}")
        for row in divergencias:
            print(f"  ID {row['ID']:>4s} [{row['Decisão']}] {row['Arquivo-fonte']}")
            print(f"       atual: {row['Texto atual']!r}")
            print(f"       recom: {row['Texto recomendado']!r}")
    else:
        print("Nenhuma divergencia restante.")

    if args.json:
        Path(args.json).write_text(
            json.dumps({"counts": dict(counts), "divergencias": divergencias}, ensure_ascii=False, indent=2),
            encoding="utf-8",
        )

    return 1 if divergencias else 0


if __name__ == "__main__":
    sys.exit(main())
