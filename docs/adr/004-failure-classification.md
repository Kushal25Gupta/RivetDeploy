# ADR 004: Why classify retryable vs non-retryable failures?

## Context
Deployments fail for many reasons: network blips, bad user code, or system outages.

## Decision
We implemented a `FailureClassifier` that categorizes errors into transient (e.g. network timeout) and permanent (e.g. user build command failed).

## Consequences
We only apply exponential backoff retries to transient infrastructure failures. This prevents the system from wasting CPU cycles repeatedly rebuilding a project with a syntax error, while still providing high reliability against flaky networks.
