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
