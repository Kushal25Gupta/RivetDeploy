# Changelog

All notable changes to this project will be documented in this file.

## [Unreleased]
- Initialized `CHANGELOG.md` to track all project changes.
- Read and analyzed the `RivetDeploy_JetSki_Build_Specification.doc`.
- Drafted initial implementation plan for Phase 1 based on the JetSki Execution Checklist.
- Provisioned AWS infrastructure (EC2 + S3) using Terraform.
- Updated `SecurityConfig.java` to use a configurable redirect URL after OAuth login.
- Added `RIVETDEPLOY_FRONTEND_URL` to `docker-compose.yml` to route back to Nginx on port 8082.
- Added `rivetdeploy_accessKeys.csv` to `.gitignore` to prevent credential leaks.
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
## [2026-08-22T18:18:00Z]
- Implemented build log and event collection (Step 13).
- Created `EventLoggerService` that persists `DeploymentEvent` entities into the `deployment_events` table using Spring Data JPA.
- Set `@Transactional(propagation = Propagation.REQUIRES_NEW)` on event logging to ensure logs are committed immediately even if the parent build transaction rolls back.
- Integrated `EventLoggerService` into `WorkerService` to persist pipeline state transitions and final build status.
- Integrated `EventLoggerService` into `DockerBuildService` to capture and stream `DockerClient` build output and `ProcessBuilder` Nixpacks standard output directly into the database as events.
## [2026-08-22T18:22:00Z]
- Implemented Cloud Storage / Artifact Upload service (Step 14).
- Added `ArtifactStorageService` interface with `LocalArtifactStorageService` (using immutable directory prefixes `projects/{projectId}/deployments/{deploymentId}`) and `GcsArtifactStorageService` for Google Cloud Storage.
## [2026-08-22T18:25:00Z]
- Implemented Active Deployment Pointer and Nginx Routing (Step 15).
- Added atomic symlink switching for local storage (`projects/{projectId}/current -> deployments/{deploymentId}`).
- Configured shared `artifacts_data` volume and Nginx `/sites/` location block to serve active static assets without container restarts.
## [2026-08-22T18:28:00Z]
- Implemented Deployment History & Rollback (Step 16).
- Added `rollback` method in `DeploymentService` and `POST /api/deployments/{id}/rollback` endpoint in `DeploymentController`.
- Verified rollback switches active pointers without rebuilding artifacts.
## [2026-08-22T18:31:00Z]
- Implemented WebSocket Live Logs (Step 17).
- Created `DeploymentLogWebSocketHandler` and `WebSocketConfig` mapping to `/ws/deployments/{id}`.
- Wired `EventLoggerService` to broadcast `DeploymentEventDto` to active WebSocket subscribers.
## [2026-08-22T18:34:00Z]
- Implemented Retry Policy and Failure Classification (Step 18).
- Created `FailureClassifier`, `FailureType`, `TransientFailureException`, `PermanentFailureException`, and `RetryPolicy` (exponential backoff with jitter).
- Configured state machine transitions allowing retry back to `QUEUED` and terminal failure states (`BUILD_FAILED`, `CLONE_FAILED`, `SYSTEM_FAILED`).
## [2026-08-22T18:37:00Z]
- Implemented GitHub Webhook Auto-Deploy with HMAC-SHA256 Signature Verification (Step 19).
- Created `GitHubWebhookService` and `POST /api/webhooks/github` controller endpoint.
- Implemented constant-time HMAC SHA-256 signature verification and automated deployment triggering on repository push events.
## [2026-08-22T18:38:00Z]
- Implemented Webhook Idempotency (Step 20).
- Created Flyway migration `V3__create_webhook_deliveries.sql` and `WebhookDelivery` repository to deduplicate webhook deliveries by `X-GitHub-Delivery`.
## [2026-08-22T18:39:00Z]
- Implemented Deployment Cancellation and Resource Cleanup (Step 21).
- Created `CancellationManager`, `POST /api/deployments/{id}/cancel` endpoint, and integrated cancellation checkpoints into `WorkerService`.
- Added automated workspace cleanup in `GitService.cleanupWorkspace()`.
## [2026-08-22T18:40:00Z]
- Implemented Redis-Backed JobQueue (Step 22).
- Added `RedisJobQueue` with `@ConditionalOnProperty(name = "rivetdeploy.queue.type", havingValue = "redis")` supporting delayed ZSet scheduling, blocking pops, and explicit acks.
- Added Redis container to `docker-compose.yml`.
## [2026-08-22T18:41:00Z]
- Implemented Multi-Worker Execution & Concurrency Limits (Step 23).
- Configured `WorkerService` to dynamically spin up fixed worker pools configured via `rivetdeploy.worker.pool-size` (1 to 4 workers).
## [2026-08-22T18:42:00Z]
- Implemented Prometheus Metrics & Actuator Observability (Step 25).
- Created `MetricsService` tracking `deployment_total`, `deployment_success_total`, `deployment_failure_total`, `active_workers`, `queue_wait_seconds`, `deployment_duration_seconds`, and `cancel_total`.
- Configured Spring Actuator and Micrometer Prometheus export under `/actuator/prometheus`.
## [2026-08-23T09:15:00Z]
- Completed and validated Phase 1 Core Engine locally via complete end-to-end testing.
- Fixed static artifact extraction to pull compiled `/usr/share/nginx/html` assets out of Docker containers in `DockerBuildService` using Docker Java API and `commons-compress`.
- Re-enabled and secured GitHub OAuth flow by properly restoring `SecurityConfig` and removing local bypasses.
- Updated `docker-compose.yml` to inject real `GITHUB_CLIENT_ID` and `GITHUB_CLIENT_SECRET` via a `.env` file.
- Fixed React frontend blank screen on subpaths by explicitly setting `base: './'` in `vite.config.ts`.
- Fixed Spring Security logout 404 by configuring `.logoutRequestMatcher` to accept `GET` requests from the frontend.
- Fixed a `frontend/.gitignore` bug that accidentally ignored `TerminalViewer.tsx` during GitHub builds.
- Upgraded root `Dockerfile` to `node:20-alpine` and configured it to compile the React dashboard for self-hosted deployments.
- Cleaned up obsolete local API testing scripts and temporary Python scratchpad files.
## [2026-08-23T09:27:00Z]
- Began Phase 2: Google Cloud Infrastructure Provisioning.
- Authored initial Terraform scripts (`infra/terraform/main.tf`, `variables.tf`, `outputs.tf`) targeting GCP's "Always Free" tier.
- Designed architecture integrating Cloud Run (API) and an `e2-micro` Compute Engine VM (DB/Worker) using Direct VPC Egress for zero-cost internal network communication.
## [2026-08-23T10:09:00Z]
- Pivoted Phase 2 infrastructure from Google Cloud Platform to Amazon Web Services (AWS) by user request.
- Removed GCP Terraform files.
- Authored AWS Free-Tier Terraform scripts mapping to the identical architecture:
  - Replaced Cloud Storage with AWS S3 for immutable artifacts.
  - Replaced Cloud Run/Compute Engine hybrid with a single `t2.micro` EC2 instance handling the API, Postgres, Redis, and Worker via a 2GB swap file to fit within 1GB RAM constraints.
## [2026-08-23T10:18:00Z]
- Updated `README.md` with streamlined local deployment instructions utilizing `run.sh` and Docker Compose.
