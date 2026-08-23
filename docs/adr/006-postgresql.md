# ADR 006: Why PostgreSQL for durable state?

## Context
We need to store project configurations, deployment history, and state machine transitions.

## Decision
We chose PostgreSQL.

## Consequences
PostgreSQL provides strong ACID guarantees, which is critical for maintaining accurate state machine transitions and deployment metadata. Relational integrity ensures orphaned deployments or invalid states are prevented at the database level.
