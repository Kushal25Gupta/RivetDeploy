# ADR 005: Why one worker first, then measure 1-4 workers?

## Context
We needed to prove the system can scale concurrent builds.

## Decision
We explicitly limited the initial implementation to a single worker to guarantee the queue abstraction worked correctly. Only after passing integration tests did we expand the thread pool to measure 1-4 workers.

## Consequences
This prevented concurrency bugs from hiding in the initial implementation and provided a clear baseline metric. Increasing the worker count later allowed us to accurately measure the throughput bottleneck and observe the impact on queue wait times.
