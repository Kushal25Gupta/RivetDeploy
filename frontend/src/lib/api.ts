export interface User {
  id: string;
  githubId: string;
  username: string;
  email?: string;
  avatarUrl?: string;
}

export interface Project {
  id: string;
  name: string;
  repositoryUrl: string;
  branch: string;
  buildCommand?: string;
  outputDirectory?: string;
  ownerId: string;
  activeDeploymentId?: string;
  createdAt: string;
}

export type DeploymentStatus = 
  | 'QUEUED'
  | 'CLONING'
  | 'INSTALLING'
  | 'BUILDING'
  | 'UPLOADING'
  | 'DEPLOYED'
  | 'CLONE_FAILED'
  | 'INSTALL_FAILED'
  | 'BUILD_FAILED'
  | 'UPLOAD_FAILED'
  | 'TIMEOUT'
  | 'SYSTEM_FAILED'
  | 'CANCELLED';

export interface Deployment {
  id: string;
  project: Project;
  commitSha: string;
  status: DeploymentStatus;
  artifactLocation?: string;
  failureType?: string;
  createdAt: string;
}

export interface DeploymentEvent {
  id: number;
  deploymentId: string;
  eventType: string;
  message: string;
  timestamp: string;
}

const API_BASE = '';

export async function fetchCurrentUser(): Promise<User | null> {
  try {
    const res = await fetch(`${API_BASE}/api/me`, { credentials: 'include' });
    if (res.status === 401) return null;
    if (!res.ok) throw new Error('Failed to fetch user');
    return await res.json();
  } catch {
    return null;
  }
}

export async function fetchProjects(): Promise<Project[]> {
  const res = await fetch(`${API_BASE}/api/projects`, { credentials: 'include' });
  if (!res.ok) throw new Error('Failed to fetch projects');
  return await res.json();
}

export async function createProject(data: {
  name: string;
  repositoryUrl: string;
  branch: string;
  buildCommand?: string;
  outputDirectory?: string;
}): Promise<Project> {
  const res = await fetch(`${API_BASE}/api/projects`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(data),
    credentials: 'include',
  });
  if (!res.ok) throw new Error('Failed to create project');
  return await res.json();
}

export async function fetchProjectDeployments(projectId: string): Promise<Deployment[]> {
  const res = await fetch(`${API_BASE}/api/projects/${projectId}/deployments`, { credentials: 'include' });
  if (!res.ok) throw new Error('Failed to fetch deployments');
  return await res.json();
}

export async function triggerDeployment(projectId: string, commitSha: string = 'HEAD'): Promise<Deployment> {
  const res = await fetch(`${API_BASE}/api/projects/${projectId}/deployments?commitSha=${encodeURIComponent(commitSha)}`, {
    method: 'POST',
    credentials: 'include',
  });
  if (!res.ok) throw new Error('Failed to trigger deployment');
  return await res.json();
}

export async function rollbackDeployment(deploymentId: string): Promise<Project> {
  const res = await fetch(`${API_BASE}/api/deployments/${deploymentId}/rollback`, {
    method: 'POST',
    credentials: 'include',
  });
  if (!res.ok) {
    const err = await res.text();
    throw new Error(err || 'Failed to rollback deployment');
  }
  return await res.json();
}

export async function cancelDeployment(deploymentId: string): Promise<Deployment> {
  const res = await fetch(`${API_BASE}/api/deployments/${deploymentId}/cancel`, {
    method: 'POST',
    credentials: 'include',
  });
  if (!res.ok) {
    const err = await res.text();
    throw new Error(err || 'Failed to cancel deployment');
  }
  return await res.json();
}

export async function fetchDeploymentEvents(deploymentId: string): Promise<DeploymentEvent[]> {
  const res = await fetch(`${API_BASE}/api/deployments/${deploymentId}/events`, { credentials: 'include' });
  if (!res.ok) throw new Error('Failed to fetch events');
  return await res.json();
}
