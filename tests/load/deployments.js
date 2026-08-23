import http from 'k6/http';
import { check, sleep } from 'k6';
import { uuidv4 } from 'https://jslib.k6.io/k6-utils/1.4.0/index.js';

export const options = {
  stages: [
    { duration: '10s', target: 5 }, // Ramp up to 5 concurrent users
    { duration: '30s', target: 5 }, // Stay at 5 users
    { duration: '10s', target: 0 }, // Ramp down to 0 users
  ],
  thresholds: {
    http_req_duration: ['p(95)<500'], // 95% of API requests should be below 500ms
    http_req_failed: ['rate<0.01'],   // Error rate should be less than 1%
  },
};

const BASE_URL = __ENV.API_URL || 'http://localhost:8081/api';
const AUTH_COOKIE = __ENV.SESSION_COOKIE || 'JSESSIONID=dummy'; 

export default function () {
  const params = {
    headers: {
      'Content-Type': 'application/json',
      'Cookie': AUTH_COOKIE,
    },
  };

  // 1. Create a project
  const projectId = `test-proj-${uuidv4().substring(0, 8)}`;
  const projectPayload = JSON.stringify({
    id: projectId,
    name: `Load Test Project ${projectId}`,
    repositoryUrl: 'https://github.com/remix-run/react-router.git',
    branch: 'main',
    buildCommand: 'npm install && npm run build',
    outputDirectory: 'build'
  });

  const createRes = http.post(`${BASE_URL}/projects`, projectPayload, params);
  
  // Note: For this load test, if auth fails, we assume it's unauthenticated.
  // We check if it returns 200/201 (or 401 if we haven't configured a bypass, but k6 will still run the requests).
  check(createRes, {
    'project created': (r) => r.status === 200 || r.status === 201,
  });

  // 2. Trigger a deployment
  if (createRes.status === 200 || createRes.status === 201) {
    const deployRes = http.post(`${BASE_URL}/projects/${projectId}/deployments?commitSha=HEAD`, null, params);
    
    check(deployRes, {
      'deployment triggered': (r) => r.status === 200 || r.status === 201,
    });
    
    // 3. Check deployment status
    if (deployRes.status === 200 || deployRes.status === 201) {
      const deploymentId = deployRes.json('id');
      const statusRes = http.get(`${BASE_URL}/deployments/${deploymentId}`, params);
      check(statusRes, {
        'deployment status retrieved': (r) => r.status === 200,
      });
    }
  }

  sleep(1);
}
