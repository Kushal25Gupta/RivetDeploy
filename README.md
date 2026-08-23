# 🚀 RivetDeploy

**RivetDeploy** is a powerful, self-hosted deployment orchestration platform designed to serve as your own personal alternative to services like Vercel or Netlify. It transforms a GitHub repository into a live, hosted website by automating the entire CI/CD pipeline—from cloning code and building it inside secure, isolated Docker containers, to extracting static artifacts, uploading them to AWS S3, and serving them seamlessly behind a high-performance Nginx reverse proxy.

With RivetDeploy, you get zero-downtime rollbacks, live-streaming build logs via WebSockets, and instantaneous automated deployments via GitHub Webhooks.

---

## 🛠️ Deploying Your Own Projects on RivetDeploy

To deploy a web application (like React, Next.js, Vite, or a static HTML site) using RivetDeploy, your repository must follow a few strict rules:

> [!IMPORTANT]
> **Rule 1: Dockerfile Location**  
> Your repository **must** contain a `Dockerfile` located exactly at the **root** folder of your repository. RivetDeploy will use this file to build your application.

> [!WARNING]
> **Rule 2: .dockerignore Configuration**  
> If you have a `.dockerignore` file, you **must not** ignore the `Dockerfile` itself. Ensure that `Dockerfile` is whitelisted (e.g., add `!Dockerfile` if using a wildcard), otherwise the internal Docker Java Engine will throw a `SYSTEM_ERROR` during the build phase.

> [!TIP]
> **Rule 3: Artifact Extraction Path**  
> RivetDeploy is designed to extract static artifacts from the final Docker image. By default, it expects your built files to be placed at `/usr/share/nginx/html`. 
> 
> **Standard Multi-Stage Dockerfile Example:**
> ```dockerfile
> # Stage 1: Build your app
> FROM node:20-alpine AS builder
> WORKDIR /app
> COPY package*.json ./
> RUN npm install
> COPY . .
> RUN npm run build
> 
> # Stage 2: Serve
> FROM nginx:alpine
> # Copy the build output (e.g., 'dist' or 'build') to the required extraction path
> COPY --from=builder /app/dist /usr/share/nginx/html
> ```

---

## 💻 Local Setup & Installation

Follow these steps to run the complete RivetDeploy platform on your local machine.

### 1. Clone the Repository
```bash
git clone https://github.com/Kushal25Gupta/RivetDeploy.git
cd RivetDeploy
```

### 2. Configure Environment Variables
You must create a `.env` file at the **root** of the repository (`/RivetDeploy/.env`). RivetDeploy requires AWS S3 for artifact storage and a GitHub OAuth App for user authentication.

Populate your `.env` file with the following keys:

```env
# AWS S3 Configuration
RIVETDEPLOY_STORAGE_S3_BUCKET=your-aws-s3-bucket-name
AWS_ACCESS_KEY_ID=your-aws-access-key
AWS_SECRET_ACCESS_KEY=your-aws-secret-key
AWS_REGION=us-east-1

# GitHub OAuth App Configuration
# (Callback URL should be: http://localhost:8081/login/oauth2/code/github for local dev)
GITHUB_CLIENT_ID=your-github-client-id
GITHUB_CLIENT_SECRET=your-github-client-secret

# Spring Security mappings
SPRING_SECURITY_OAUTH2_CLIENT_REGISTRATION_GITHUB_CLIENTID=${GITHUB_CLIENT_ID}
SPRING_SECURITY_OAUTH2_CLIENT_REGISTRATION_GITHUB_CLIENTSECRET=${GITHUB_CLIENT_SECRET}

# Frontend Configuration
VITE_API_BASE_URL=
```

### 3. Run the Platform
RivetDeploy is fully containerized. You can launch the entire stack (Frontend, Backend, PostgreSQL, Redis, and Nginx) with a single command:

```bash
chmod +x run.sh
./run.sh
# Or alternatively: docker-compose up --build
```

Access the React Dashboard locally at: [http://localhost](http://localhost) (Nginx maps port 80 to the frontend and proxies `/api` to the backend).

---

## 🏗️ Architecture & Technology Stack

RivetDeploy is built with modern, enterprise-grade technologies optimized for concurrency and isolated build execution.

### Tech Stack
- **Backend**: Java 21, Spring Boot 3.3.0, Spring Security (OAuth2), Spring Data JPA, Flyway Migrations, Java Docker API client, AWS SDK v2.
- **Frontend**: React 18, TypeScript, Vite, Tailwind CSS, Lucide Icons.
- **Databases**: 
  - PostgreSQL 15 (Relational data, Projects, Deployments, Events).
  - Redis 7 (Distributed Job Queue for FIFO build scheduling & Spring Session storage).
- **Infrastructure**: Nginx (Reverse Proxy & Static File Serving), Docker Compose.

### System Architecture
1. **FIFO Job Scheduler**: When a deployment is triggered, it is pushed to a Redis-backed delayed job queue to ensure strict FIFO (First-In, First-Out) processing, preventing server overload.
2. **Worker Service**: A background thread polls the queue, clones the GitHub repository, and invokes the Docker Engine API to securely build the image inside an isolated sandbox.
3. **Artifact Extraction**: Instead of running endless containers for hosted sites, the worker extracts the static files directly from the built Docker image and uploads them to AWS S3.
4. **Site Controller & Nginx Proxy**: When a user visits a hosted site, Nginx proxies the request to the Spring Boot backend, which acts as an intelligent router to fetch and serve the exact `index.html` and assets from S3 based on the active deployment pointer.
5. **Live WebSockets**: The `DeploymentLogWebSocketHandler` streams Docker build output directly from the backend to the frontend UI in real-time.

---

## ✨ Core Features
- **Isolated Docker Builds**: Guaranteed secure runtime environments for every deployment.
- **Zero-Rebuild Rollbacks**: Because artifacts are permanently stored in S3, rolling back to a previous deployment simply updates a database pointer—happening instantly with zero downtime.
- **Real-Time Build Streaming**: Watch your code clone, install, and build live on the dashboard.
- **GitHub Webhook Integration**: Push to `main` and watch your platform automatically trigger zero-downtime builds.

---

*"Code is like humor. When you have to explain it, it’s bad."* — Fortunately, RivetDeploy handles the complexity so you don't have to. Happy deploying, and welcome to the future of self-hosted infrastructure! 🚀
