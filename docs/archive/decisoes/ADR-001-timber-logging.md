# ADR-001: Usar Timber como biblioteca de logging

**Data:** 2026-05-24  
**Status:** Accepted  
**Atualizado em:** 2026-06-28 — Crashlytics integrado via ReleaseTree (SIG-227)

## Contexto

O projeto Android herdou uso inconsistente de `Log.*` (Android logging nativo) espalhado pelo codebase. A biblioteca `Log.d()`, `Log.e()` etc. oferece pouca flexibilidade para:
- Redirecionamento de logs em diferentes ambientes (debug vs. release)
- Integração com ferramentas de crash reporting (Firebase Crashlytics, etc.)
- Formatação uniforme de mensagens
- Supressão de logs em release sem refactoring

## Decisão

Migrar todo logging do projeto para usar **Timber** (biblioteca mantida por Jake Wharton, depêndência padrão em arquitetura moderna Android).

**Implementação:**
- `com.jakewharton.timber:timber` em `libs.versions.toml`
- `SignallQApplication.onCreate()` planta `DebugTree` em debug e `ReleaseTree` em release
- Todas as ocorrências de `Log.*` migradas para `Timber.*`

**Benefícios:**
- Logs automaticamente suprimidos em release (via BuildConfig.DEBUG)
- Stack trace rastreável em relatório de crashes
- Formatação consistente com tag automática (classe + linha)
- Integração com Crashlytics via `ReleaseTree` sem mudança de código nos chamadores

## Estado atual da integração com Crashlytics

`ReleaseTree` (`app/src/main/kotlin/.../logging/ReleaseTree.kt`):
- Filtra apenas `WARN` e `ERROR` (`priority < Log.WARN` ignorado)
- Envia breadcrumb via `FirebaseCrashlytics.getInstance().log(...)`
- Registra exceções via `FirebaseCrashlytics.getInstance().recordException(t)`
- Para `ERROR`, registra evento `feature_crash` via `AnalyticsTracker`

**Debug**: `FirebaseCrashlytics.getInstance().setCrashlyticsCollectionEnabled(false)` chamado
em `SignallQApplication.onCreate()` — coleta de crashes desabilitada para não poluir dados
de produção no Firebase Console com crashes de desenvolvimento.

**Release**: coleta habilitada por padrão (SDK default). `mappingFileUploadEnabled = true`
configurado em `build.gradle.kts` — o mapping.txt do R8 é enviado automaticamente ao Firebase
como dependência do task `assembleRelease`/`bundleRelease`.

## Consequências

- **Impacto:** ~70 ocorrências de `Log.*` refatoradas (PR #28)
- **Dependência nova:** Timber (leve, sem maiores dependências)
- **Performance:** Negligenciável (Timber é otimizado para zero-overhead quando desabilitado)
- **Reversão:** Improvável; Timber é padrão de ouro em Android moderno

## Referências

- PR #28: Refactor Log.* para Timber (70 ocorrências)
- Issue #6: Logger
- SIG-227: Validação Crashlytics em release build
- Site: https://github.com/JakeWharton/timber
