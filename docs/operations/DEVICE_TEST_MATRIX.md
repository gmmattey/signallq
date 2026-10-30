---
title: "Matriz de dispositivos para teste"
description: "Matriz mínima de dispositivo, versão Android e operadora por release."
type: "runbook"
status: "ativo"
owner: "Camillo"
last_updated: "2026-10-04"
version: "1.1.0"
---

# Matriz de Dispositivos para Teste — SignallQ

- **Fonte de verdade:** `android/gradle/libs.versions.toml` (minSdk/targetSdk/compileSdk reais)
- **Escopo:** matriz de dispositivo/Android/operadora obrigatória por release

## Critérios de Seleção

- minSdk 24 (Android 7.0) — cobrir a faixa mais ampla possível
- Fabricantes populares no Brasil: Samsung, Motorola, Xiaomi, Pixel
- Variedade de hardware: low-end, mid-range, high-end
- Versões Android: 7.0 a 16

## Matriz Obrigatória (mín. para cada release)

| Dispositivo | Android | Tier | Foco de Teste |
|---|---|---|---|
| Samsung Galaxy A14 | 13-14 | Low-end | Performance, memória, ANR |
| Motorola Moto G54 | 13-14 | Mid-range | Baseline comportamento |
| Xiaomi Redmi Note 13 | 14 | Mid-range | MIUI quirks, permissões |
| Samsung Galaxy S24 | 14-15 | High-end | Performance ceiling |
| Google Pixel 7a | 14-16 | Reference | Stock Android, baseline |
| Dispositivo Android 7-8 | 7.0-8.1 | Legacy | Compatibilidade minSdk |

## Cenários de Teste por Dispositivo

### Obrigatórios (todos os devices)

- [ ] Fresh install e onboarding
- [ ] Speedtest completo (download + upload + latência)
- [ ] Diagnóstico IA end-to-end
- [ ] Scan Wi-Fi
- [ ] Histórico — persistência entre sessões
- [ ] Background worker (MonitoramentoWorker)
- [ ] Permissões: conceder, negar, revogar em runtime

### Específicos por tier

**Low-end:**
- [ ] Cold start < 3s (tolerância maior)
- [ ] Sem ANR durante diagnóstico
- [ ] Memória: sem OOM em operação normal

**Legacy (API 24-27):**
- [ ] APIs de conectividade funcionam (ConnectivityManager compat)
- [ ] TelephonyManager sem crash
- [ ] UI renderiza corretamente (sem Compose issues)

**Xiaomi/MIUI:**
- [ ] AutoStart ativo (MonitoramentoWorker)
- [ ] Permissões especiais de background
- [ ] Notificações não bloqueadas

## Operadoras a Cobrir

| Operadora | Tipo | Prioridade |
|---|---|---|
| Claro | Móvel | Alta |
| Vivo | Móvel | Alta |
| Tim | Móvel | Alta |
| Oi | Móvel | Média |
| Wi-Fi doméstico | Fixo | Alta |
| Wi-Fi público/compartilhado | Fixo | Média |

## Emuladores (complementar, não substitui device real)

- API 24 (Android 7.0) — teste de compatibilidade minSdk
- API 36 (Android 16) — targetSdk real (`android/gradle/libs.versions.toml`), behavior changes
- API 37 — compileSdk real; usar para pegar warning/preview antes do targetSdk subir
