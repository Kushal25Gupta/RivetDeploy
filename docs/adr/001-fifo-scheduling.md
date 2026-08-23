# ADR 001: Why FIFO scheduling before priority scheduling?

## Context
When designing the deployment orchestration queue, we had to choose between a simple First-In-First-Out (FIFO) queue and a complex priority-based queue.

## Decision
We implemented a strict FIFO queue first.

## Consequences
By starting with FIFO, we guaranteed correct ordering of jobs and isolated the worker lifecycle logic from the complexities of job starvation and priority inversion. This allowed us to build the core engine faster and prove the system's correctness. Priority scheduling can be added later if metrics show high-priority jobs are delayed beyond acceptable SLAs.
