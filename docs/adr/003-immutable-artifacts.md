# ADR 003: Why immutable artifacts and pointer-based rollback?

## Context
We need a way to serve the active deployment and allow users to rollback if a build fails.

## Decision
Every successful build is uploaded to a unique, immutable prefix in Google Cloud Storage. The active deployment is simply a database pointer (and an Nginx symlink) to that specific prefix.

## Consequences
Rollbacks are instantaneous (zero-rebuild) because the old artifacts are never modified or deleted. It ensures auditability and guarantees that returning to an old version results in the exact same application state that was previously served.
