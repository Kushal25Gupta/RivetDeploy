# ADR 002: Why separate controller/control plane from Docker build workers?

## Context
We need to execute untrusted user builds using Docker.

## Decision
We chose to separate the Spring Boot API (control plane) from the Docker build workers. The API will run on Cloud Run, while the build workers will run on dedicated Compute Engine instances.

## Consequences
This prevents a runaway build (e.g. CPU/memory exhaustion) from crashing the API server. It also allows us to scale the lightweight control plane independently of the resource-heavy build workers, and explicitly isolates untrusted execution environments.
