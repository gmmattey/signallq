# ADR-003: DispatcherProvider via injeção de dependência (sem Hilt)

**Data:** 2026-05-24  
**Status:** Accepted

## Contexto

Em arquitetura Android com Kotlin Coroutines, `Dispatchers.IO`, `Dispatchers.Main` etc. são frequentemente hardcoded em serviços e use cases. Isso causa:
- Dificuldade em testes (não dá para mockear dispatcher em testes unitários)
- Acoplamento forte com implementação nativa do Android
- Impossível injetar custom dispatcher para debug/profiling

Historicamente, projetos resolvem isso com Hilt + `@Provides`, mas o SignallQ decidiu usar injeção manual (sem DI framework).

## Decisão

Criar objeto `DispatcherProvider` com interface:
```kotlin
interface DispatcherProvider {
    val io: CoroutineDispatcher
    val main: CoroutineDispatcher
    val default: CoroutineDispatcher
    val unconfined: CoroutineDispatcher
}
```

Instâncias:
- **Production:** `DefaultDispatcherProvider` (mapeia para `Dispatchers.IO`, etc.)
- **Testing:** `TestDispatcherProvider` (usa `StandardTestDispatcher` ou equivalente)

**Uso:**
- Serviços e use cases recebem `DispatcherProvider` via construtor
- Usam `dispatcherProvider.io` em `launch(dispatcherProvider.io) { }`

**Benefícios:**
- Testability: testes injetam `TestDispatcherProvider`
- Flexibilidade: swappable em qualquer ambiente
- Sem framework DI (mantém simplicidade)

## Consequências

- **Impacto:** Refactor estrutural em MainViewModel e serviços que usam Coroutines
- **Boilerplate:** Minimal (constructor injection straightforward)
- **Performance:** Zero overhead (object é singleton)
- **Issue relacionada:** #7 (DispatcherProvider)

## Referências

- Issue #7: Feat(infra) abstrair Dispatchers com DispatcherProvider
- `TECNICO.md` Seção 3.2 — Injeção: "manual por construtor"
