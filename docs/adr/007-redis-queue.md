# ADR 007: Why Redis only after the queue abstraction is proven?

## Context
We needed a persistent queue for distributing jobs across multiple worker nodes.

## Decision
We implemented an `InMemoryJobQueue` first, defined an interface, and only added `RedisJobQueue` in Phase 2.

## Consequences
This forced the scheduler to depend purely on the interface contract rather than Redis-specific features. When Redis was introduced (using delayed ZSets and blocking lists), the core scheduling loop required zero changes, proving the architectural boundary.
