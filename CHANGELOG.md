# Changelog

All notable changes to this project will be documented in this file.

## [Unreleased]
- Initialized `CHANGELOG.md` to track all project changes.
- Read and analyzed the `RivetDeploy_JetSki_Build_Specification.doc`.
- Drafted initial implementation plan for Phase 1 based on the JetSki Execution Checklist.
## [2026-08-22T17:45:00Z]
- Created monorepo and documentation skeleton (`backend`, `worker`, `frontend`, `infra`, `tests`, `docs` directories).
- Added initial `README.md` and empty `docker-compose.yml`.
## [2026-08-22T17:46:00Z]
- Scaffolding `backend` Spring Boot application with Java 21, Maven, and dependencies (Web, Data JPA, PostgreSQL, Security, OAuth2, WebSocket).
## [2026-08-22T17:47:00Z]
- Created frontend application using React + TypeScript + Vite.
## [2026-08-22T17:48:00Z]
- Configured frontend Tailwind CSS, shadcn/ui dependencies, and directory structure.
- Added `docker-compose.yml` for local PostgreSQL and Nginx setup.
- Created `backend/Dockerfile` and `infra/nginx/nginx.conf` for basic containerization.
## [2026-08-22T17:49:00Z]
- Implemented PostgreSQL schema and Flyway migrations (`V1__init_schema.sql`).
- Added Flyway dependency to backend.
- Created basic application.yml with DB configuration.
## [2026-08-22T17:50:00Z]
- Implemented GitHub OAuth login mechanism (Step 6).
- Created `User` entity, `UserRepository`, `CustomOAuth2UserService`, and `SecurityConfig`.
- Added `/api/me` endpoint in `AuthController`.
## [2026-08-22T17:51:00Z]
- Implemented Project CRUD with ownership checks (Step 7).
- Created `Project` entity, `ProjectRepository`, and `ProjectService`.
- Added `/api/projects` endpoints in `ProjectController`.
## [2026-08-22T18:03:00Z]
- Implemented Deployment entity and explicit state machine with transitions (`QUEUED` to `DEPLOYED` and terminals) (Step 8).
- Added `DeploymentStateTest` to verify valid and invalid state transitions.
- Created `DeploymentEvent` entity for log and event storage.
- Created `DeploymentService` and `DeploymentController` for managing deployments.
## [2026-08-22T18:06:00Z]
- Implemented `JobQueue` abstraction and `InMemoryJobQueue` implementation (Step 9).
- Added `InMemoryJobQueueTest` to verify strict FIFO ordering and delayed requeuing logic.
## [2026-08-22T18:09:00Z]
- Implemented single Worker process (`WorkerService`) to sequentially claim and process deployment jobs from `JobQueue` (Step 10).
- Verified `WorkerServiceTest` accurately simulates execution and records terminal deployment state.
## [2026-08-22T18:12:00Z]
- Added `docker-java` dependency to integrate with Docker Engine API (Step 11).
- Created `DockerService` to configure the connection to the host Docker daemon.
- Created `DockerBuildService` to handle programmatic image building (`buildImageCmd`).
- Created `GitService` to handle repository cloning (`ProcessBuilder` with `git clone`).
- Integrated `GitService` and `DockerBuildService` into `WorkerService`, replacing the simulated Thread.sleep work with actual pipeline execution.
- Updated `docker-compose.yml` to mount `/var/run/docker.sock` to the backend container to allow interaction with the host daemon.
## [2026-08-22T18:15:00Z]
- Implemented Nixpacks fallback build strategy (Step 12) in `DockerBuildService`.
- Modified `backend/Dockerfile` to use Ubuntu (`eclipse-temurin:21-jre`) instead of Alpine, and added instructions to install `nixpacks`, `curl`, `git`, and `docker.io` so that the worker can build child images.
- Configured `DockerBuildService` to check for `Dockerfile` in the root of the cloned repo. If absent, it shells out to `nixpacks build . --name {tag}`.
- Updated `WorkerServiceTest` and `WorkerService` transitions to strictly follow `CLONING -> INSTALLING -> BUILDING -> UPLOADING -> DEPLOYED` flow as required by the state machine.
