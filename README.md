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

## How to Run Locally

You can run the entire RivetDeploy platform (Frontend, Backend, Database, Redis, and Nginx) using a single Docker Compose command.

1. Ensure Docker is installed and running on your machine.
2. Make the startup script executable:
   ```bash
   chmod +x run.sh
   ```
3. Run the startup script:
   ```bash
   ./run.sh
   ```
4. Access the React Dashboard at: [http://localhost:8082](http://localhost:8082)
