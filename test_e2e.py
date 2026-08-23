import requests
import time
import json
import uuid

API_BASE = "http://localhost:8081/api"

print("Waiting for backend to be ready...")
for i in range(30):
    try:
        if requests.get("http://localhost:8081/actuator/health").status_code == 200:
            break
    except Exception:
        pass
    time.sleep(1)

print("1. Creating Project...")
repo_url = "https://github.com/remix-run/react-router.git"
req = {
    "name": f"test-project-{uuid.uuid4().hex[:6]}",
    "githubRepoUrl": repo_url,
    "branch": "main",
    "buildCommand": "npm install && npm run build",
    "outputDirectory": "build"
}
resp = requests.post(f"{API_BASE}/projects", json=req)
assert resp.status_code == 201, f"Failed to create project: {resp.text}"
project = resp.json()
print(f"Project created: {project['id']}")

print("2. Triggering Deployment...")
resp = requests.post(f"{API_BASE}/projects/{project['id']}/deployments")
assert resp.status_code == 201, f"Failed to trigger deployment: {resp.text}"
deployment = resp.json()
print(f"Deployment triggered: {deployment['id']}")

print("3. Waiting for deployment to finish (up to 3 minutes)...")
status = "QUEUED"
for i in range(180):
    resp = requests.get(f"{API_BASE}/deployments/{deployment['id']}")
    status = resp.json()['status']
    print(f"[{i}s] Status: {status}")
    if status in ['DEPLOYED', 'BUILD_FAILED', 'CLONE_FAILED', 'UPLOAD_FAILED', 'SYSTEM_FAILED']:
        break
    time.sleep(1)

print(f"Final status: {status}")
if status == "DEPLOYED":
    print("Deployment successful!")
    print(f"Test site: http://localhost:8082/sites/projects/{project['id']}/current/")
else:
    print("Deployment failed.")
