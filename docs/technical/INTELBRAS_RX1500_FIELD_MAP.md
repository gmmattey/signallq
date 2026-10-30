---
title: "Reconhecimento — firmware Intelbras RX1500/RAX1500"
description: "Análise estática parcial (bloqueada) do firmware Intelbras RX1500/RAX1500; sem mapa de campos da interface web. Insumo de reconhecimento, não de produto."
type: "técnico"
status: "ativo"
owner: "Camillo"
last_updated: "2026-10-04"
version: "2.0.0"
---

# Reconhecimento — firmware Intelbras RX1500/RAX1500 (análise estática)

**Natureza:** reconhecimento, não produto (metodologia da skill `/reconhecimento-equipamento-rede`). Diferente de `NOKIA_GPON_FIELD_MAP.md` e `TPLINK_ARCHER_ROUTER_FIELD_MAP.md`, **não houve acesso a equipamento ao vivo**: a fonte foi um único arquivo de firmware (`INTELBRAS_RX1500_2.2.24__20250828_release.aes`, ~20 MB), analisado offline em 2026-07-09 com parsing binário em Python (stdlib). Resultado **parcial**: a interface web (`rootfs`) não pôde ser extraída, então **este documento não contém nenhum campo de interface administrativa**.
**Fonte de verdade:** o próprio arquivo de firmware (não versionado); nenhum código do produto depende deste documento.
**Substitui:** a versão 1.x (257 linhas), que detalhava o padrão de "buracos" byte a byte e tentativas de extração; recuperável via `git log -- docs/technical/INTELBRAS_RX1500_FIELD_MAP.md`.

## Identificação

| Campo | Valor | Fonte |
|---|---|---|
| Modelo auto-declarado | `RAX1500` (o nome do arquivo diz `RX1500`; confirmar na etiqueta física) | preâmbulo binário, offset `0x00` |
| Hardware / firmware | `H1` / `2.2.24` | preâmbulo, offsets `0x10` e `0x14` |
| Build | `Thu Aug 28 09:39:47 CST 2025` | componente `fwu_ver` |
| Kernel | Linux 4.4.140, MIPS, GZIP | header `uImage` |
| Plataforma | provável SDK MIPS estilo Ralink/MediaTek (utilitário `flash get HW_HWVER`); SoC exato **não confirmado** | `fwu.sh` |
| Armazenamento / OTA | NAND com UBI; atualização dual-bank A/B (`ubi_k0/k1`, `ubi_r0/r1`) | `fwu.sh` |
| Root filesystem | provavelmente SquashFS, **não confirmado** | convenção de mercado |

## Estrutura do container

Apesar da extensão `.aes`, o arquivo **não é um blob cifrado**: é um TAR USTAR com preâmbulo proprietário de 48 bytes (modelo, hardware, firmware, flag de formato) contendo `fwu.sh` (script de atualização, sem chamadas de criptografia), `rootfs` (~15,5 MB), kernel `uImage` (~3,75 MB), `fwu_ver`, `hw_ver` e `md5.txt`.

Em blocos de 512 bytes contados a partir do início do TAR, **todo bloco par tem os 16 primeiros bytes sobrescritos** por valor de alta entropia (marca de integridade/anti-repack, formato não identificado; ~1,6% do arquivo). A sobrescrita é destrutiva: o stream GZIP do kernel e o superbloco do `rootfs` ficam inutilizáveis, e `hw_ver` é irrecuperável.

## Bloqueio

Não foi possível extrair o `rootfs`, onde estariam interface web, endpoints e contas padrão. Tentativas esgotadas dentro do escopo autorizado: busca por chave/algoritmo no `fwu.sh` (nenhuma) e reconstrução do stream removendo os buracos (`zlib` falha com `invalid distance too far back`). Continuar exigiria a ferramenta oficial de empacotamento da Intelbras ou um parser de SquashFS tolerante a lacunas.

**Segurança:** nenhuma credencial ou chave foi encontrada, mas só porque a análise não alcançou o `rootfs`; ausência de evidência não é evidência de ausência.

## Relação com o produto

No código do SignallQ, "Intelbras" existe apenas como fabricante no catálogo OUI (`android/core/network/.../topologia/oui/OuiCatalog.kt` e `android/feature/diagnostico/src/main/assets/oui.txt`); **não há parser de interface web Intelbras**. Se houver um RAX1500 acessível, o caminho natural é aplicar a skill `/reconhecimento-equipamento-rede` ao vivo, com credencial autorizada, em vez de insistir na análise estática deste arquivo.
