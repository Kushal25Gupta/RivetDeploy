# ADR 008: Why Compute Engine for build workers on GCP?

## Context
We need to execute Docker containers. Cloud Run supports containers, but we need host-level Docker daemon access.

## Decision
We will deploy the build workers on Google Compute Engine (GCE) instances.

## Consequences
While Cloud Run is perfect for the stateless API, GCE gives us root access to the Docker daemon (`/var/run/docker.sock`), allowing us to spawn ephemeral build containers, strictly enforce CPU/Memory limits, and perform absolute cleanup after the build completes or cancels.
