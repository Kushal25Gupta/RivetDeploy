# RivetDeploy

Deployment Orchestration & Secure Build Platform.

## Overview
RivetDeploy is a self-hosted deployment orchestration platform designed to turn a Git commit into a controlled deployment artifact through a traceable state machine and an isolated build runtime.

## Project Structure
- `backend/`: Spring Boot API control plane.
- `worker/`: Ephemeral Docker build execution logic.
- `frontend/`: React Dashboard.
- `infra/`: Infrastructure definitions (Terraform, Nginx).
- `tests/`: Integration, failure injection, and load tests.
- `docs/`: Architecture Decision Records (ADRs) and documentation.
