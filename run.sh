#!/bin/bash
echo "Starting RivetDeploy E2E Environment..."
docker compose up --build -d
echo "Environment is up!"
echo "Dashboard: http://localhost:8082"
echo "API Backend: http://localhost:8081"
