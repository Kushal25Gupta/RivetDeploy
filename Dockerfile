# Stage 1: Build the React frontend
FROM node:18-alpine AS builder
WORKDIR /app
COPY frontend/package*.json ./
RUN npm install
COPY frontend/ ./
RUN npm run build

# Stage 2: Serve the static site
FROM nginx:alpine
# Vite outputs to /dist
COPY --from=builder /app/dist /usr/share/nginx/html
