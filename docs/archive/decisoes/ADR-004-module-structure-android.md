# ADR-004: Arquitetura multi-módulo Android (app + core* + feature*)

**Data:** 2026-05-24  
**Status:** Accepted

## Contexto

O projeto SignallQ Android cresceu de um módulo único (app) para múltiplos módulos conforme features se acumulavam. Sem limite de dependency, o codebase virou um monolito acoplado onde:
- Mudança em uma classe quebrava múltiplos lugares
- Testes de feature isolada precisavam de toda a app context
- Compilação incremental ficava lenta
- Reutilização de código entre projetos era impossível

## Decisão

Adotar **arquitetura multi-módulo baseada em tipo**:

**Estrutura:**
```
app/                          # App principal, ponto de entrada
├─ build.gradle
├─ AndroidManifest.xml

core-common/                  # Utilities, extensões, constants
├─ exceptions/
├─ extensions/
├─ constants/

core-database/               # Banco de dados (Room, DAOs)
├─ entities/
├─ dao/
├─ migrations/

core-network/               # APIs, networking, DTOs
├─ models/
├─ interceptors/
├─ services/

feature-home/               # Feature Home (UI + lógica)
├─ ui/
├─ viewmodel/
├─ repository/

feature-auth/               # Feature Auth (UI + lógica)
├─ ui/
├─ viewmodel/
├─ repository/
```

**Regras de dependency:**
- `app` → pode depender de `core-*` e `feature-*`
- `feature-*` → podem depender de `core-*`
- `core-*` → dependem APENAS de `core-*` (ou nenhuma)
- `feature-*` → NÃO dependem de `feature-*` (sem ciclos)

**Benefícios:**
- Compilação incremental: mudança em `core-database` não recompila `feature-home`
- Testabilidade: `feature-home` pode ser testada sem `feature-auth`
- Separação de responsabilidade: clara divisão entre core (infraestrutura) e feature (UI)
- Reutilização: módulos `core-*` podem ser usados em outros projetos Android SignallQ

## Consequências

- **Estrutura:** Refactor em andamento (Issues #3, #7, etc. refletem essa migração)
- **Boilerplate:** Mínimo (Gradle multi-module é padrão; Android Studio gera scaffold)
- **Documentação:** `build.gradle` em cada módulo deve ter comentário sobre dependency rules
- **CI:** GitHub Actions valida dependency graph (opcional, mas recomendado futuramente)
- **Decisões futuras:** Possivelmente converter `core-*` em AAR (Android Archive) para distribuição

## Referências

- Issue #3: Feature home module + navigation
- Issue #7: Feat(infra) abstrair Dispatchers
- `TECNICO.md` — Seção 2: Arquitetura Modular
- Google: Android App Modularity: https://developer.android.com/guide/app-bundle/dynamic-feature-modules
