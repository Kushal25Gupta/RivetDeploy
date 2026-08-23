import React, { useState, useEffect } from 'react';
import { fetchProjectDeployments, triggerDeployment, suspendProject, resumeProject, deleteProject } from '../../lib/api';
import type { Project, Deployment } from '../../lib/api';
import { DeploymentList } from '../deployments/DeploymentList';
import { 
  ArrowLeft, 
  Play, 
  GitBranch, 
  Globe, 
  ExternalLink, 
  RefreshCw, 
  Copy, 
  Check, 
  Zap,
  Trash2,
  PowerOff,
  Power
} from 'lucide-react';

interface ProjectDetailProps {
  project: Project;
  onBack: () => void;
  onUpdateProject: (updated: Project) => void;
}

export const ProjectDetail: React.FC<ProjectDetailProps> = ({ project, onBack, onUpdateProject }) => {
  const [deployments, setDeployments] = useState<Deployment[]>([]);
  const [deploying, setDeploying] = useState(false);
  const [commitSha, setCommitSha] = useState('');
  const [copiedWebhook, setCopiedWebhook] = useState(false);

  const loadDeployments = async () => {
    try {
      const list = await fetchProjectDeployments(project.id);
      setDeployments(list);
      
      // Update parent state if the active deployment ID has changed
      if (list.length > 0 && list[0].project.activeDeploymentId !== project.activeDeploymentId) {
        onUpdateProject(list[0].project);
      }
    } catch (e) {
      console.error(e);
    }
  };

  useEffect(() => {
    loadDeployments();
    const interval = setInterval(loadDeployments, 3000);
    return () => clearInterval(interval);
  }, [project.id]);

  const handleTriggerDeploy = async () => {
    setDeploying(true);
    try {
      await triggerDeployment(project.id, commitSha.trim() || 'HEAD');
      setCommitSha('');
      await loadDeployments();
    } catch (e: any) {
      alert(e.message || 'Failed to trigger deployment');
    } finally {
      setDeploying(false);
    }
  };

  const copyWebhookUrl = () => {
    const url = `${window.location.protocol}//${window.location.host}/api/webhooks/github`;
    navigator.clipboard.writeText(url);
    setCopiedWebhook(true);
    setTimeout(() => setCopiedWebhook(false), 2000);
  };

  const handleSuspendToggle = async () => {
    try {
      if (project.isSuspended) {
        const updated = await resumeProject(project.id);
        onUpdateProject(updated);
      } else {
        const updated = await suspendProject(project.id);
        onUpdateProject(updated);
      }
    } catch (e: any) {
      alert(e.message || 'Failed to toggle suspension status');
    }
  };

  const handleDelete = async () => {
    if (window.confirm('Are you sure you want to delete this project? This will permanently delete all deployments.')) {
      try {
        await deleteProject(project.id);
        onBack(); // Go back to the main projects list
      } catch (e: any) {
        alert(e.message || 'Failed to delete project');
      }
    }
  };

  return (
    <div className="space-y-8 animate-in fade-in duration-300">
      {/* Top navigation */}
      <div className="flex items-center justify-between">
        <button
          onClick={onBack}
          className="flex items-center space-x-2 text-sm text-slate-400 hover:text-white transition"
        >
          <ArrowLeft className="w-4 h-4" />
          <span>Back to Projects</span>
        </button>

        <div className="flex items-center space-x-3">
          <button
            onClick={loadDeployments}
            className="p-2 text-slate-400 hover:text-white rounded-xl bg-slate-900 border border-slate-800 hover:bg-slate-800 transition"
          >
            <RefreshCw className="w-4 h-4" />
          </button>
        </div>
      </div>

      {/* Project Overview Card */}
      <div className="bg-slate-900/80 border border-slate-800 rounded-3xl p-6 sm:p-8 backdrop-blur-xl shadow-xl relative overflow-hidden">
        <div className="absolute top-0 right-0 w-96 h-96 bg-indigo-500/10 blur-[100px] rounded-full pointer-events-none" />

        <div className="flex flex-col md:flex-row md:items-center justify-between gap-6 relative z-10">
          <div className="space-y-2">
            <div className="flex items-center space-x-3">
              <h1 className="text-2xl sm:text-3xl font-bold text-white tracking-tight">{project.name}</h1>
              <span className="flex items-center space-x-1 px-3 py-1 rounded-full text-xs font-medium bg-slate-800 text-slate-300 border border-slate-700">
                <GitBranch className="w-3.5 h-3.5 text-indigo-400" />
                <span>{project.branch}</span>
              </span>
            </div>
            <div className="flex items-center space-x-2 text-sm text-slate-400">
              <Globe className="w-4 h-4 text-slate-500" />
              <a href={project.repositoryUrl} target="_blank" rel="noreferrer" className="hover:text-indigo-400 underline underline-offset-4 transition">
                {project.repositoryUrl}
              </a>
            </div>
          </div>

          {/* Action Area: Trigger Deploy */}
          <div className="flex items-center space-x-2">
            <input
              type="text"
              placeholder="Commit SHA (optional)"
              value={commitSha}
              onChange={(e) => setCommitSha(e.target.value)}
              className="bg-slate-950 border border-slate-800 rounded-xl px-3.5 py-2.5 text-xs text-white placeholder-slate-500 focus:outline-none focus:border-indigo-500 w-44 font-mono transition"
            />
            <button
              onClick={handleTriggerDeploy}
              disabled={deploying}
              className="flex items-center space-x-2 px-5 py-2.5 rounded-xl text-sm font-semibold bg-indigo-600 hover:bg-indigo-500 text-white shadow-lg shadow-indigo-600/30 transition disabled:opacity-50"
            >
              {deploying ? <RefreshCw className="w-4 h-4 animate-spin" /> : <Play className="w-4 h-4 fill-white" />}
              <span>{deploying ? 'Deploying...' : 'Deploy Now'}</span>
            </button>
          </div>
        </div>

        {/* Active Deployment banner */}
        {project.activeDeploymentId ? (
          <div className="mt-6 pt-6 border-t border-slate-800/80 flex items-center justify-between flex-wrap gap-3">
            <div className="flex items-center space-x-2">
              <span className="w-2.5 h-2.5 rounded-full bg-emerald-500 animate-pulse" />
              <span className="text-sm font-medium text-slate-300">Live Active Deployment:</span>
              <span className="text-xs font-mono text-emerald-400 bg-emerald-950/60 px-2 py-0.5 rounded border border-emerald-800/50">
                {project.activeDeploymentId}
              </span>
            </div>
            <a
              href={project ? `http://${project.id}.${window.location.host}` : ''}
              target="_blank"
              rel="noopener noreferrer"
              className="flex items-center space-x-1.5 text-sm font-semibold text-emerald-400 hover:text-emerald-300 transition"
            >
              <span>Visit Production Site</span>
              <ExternalLink className="w-4 h-4" />
            </a>
          </div>
        ) : (
          <div className="mt-6 pt-6 border-t border-slate-800/80 text-sm text-slate-500">
            No deployment has been activated yet. Click <span className="text-indigo-400 font-semibold">Deploy Now</span> to trigger the initial build.
          </div>
        )}
      </div>

      {/* GitHub Webhook Info Banner */}
      <div className="bg-gradient-to-r from-slate-900 via-indigo-950/20 to-slate-900 border border-slate-800 rounded-2xl p-5 flex items-center justify-between flex-wrap gap-4">
        <div className="flex items-start space-x-3">
          <div className="p-2 rounded-xl bg-indigo-950 text-indigo-400 border border-indigo-800/60 mt-0.5">
            <Zap className="w-4 h-4" />
          </div>
          <div className="space-y-0.5">
            <h4 className="text-sm font-semibold text-white">GitHub Webhook Auto-Deploy</h4>
            <p className="text-xs text-slate-400">
              Configure this webhook URL in your GitHub repo settings to trigger instant zero-downtime builds on git push.
            </p>
          </div>
        </div>

        <button
          onClick={copyWebhookUrl}
          className="flex items-center space-x-2 px-3.5 py-2 rounded-xl text-xs font-medium bg-slate-800 hover:bg-slate-700 text-slate-200 border border-slate-700 transition"
        >
          {copiedWebhook ? <Check className="w-3.5 h-3.5 text-emerald-400" /> : <Copy className="w-3.5 h-3.5" />}
          <span>{copiedWebhook ? 'Copied URL' : 'Copy Webhook URL'}</span>
        </button>
      </div>

      {/* Danger Zone */}
      <div className="bg-slate-900/50 border border-red-900/30 rounded-2xl p-6">
        <h3 className="text-lg font-semibold text-white mb-4">Project Management</h3>
        <div className="grid sm:grid-cols-2 gap-4">
          <div className="p-5 rounded-xl border border-slate-800 bg-slate-900 flex flex-col justify-between items-start space-y-4">
            <div>
              <h4 className="text-sm font-medium text-slate-200 mb-1">
                {project.isSuspended ? 'Resume Project' : 'Suspend Project'}
              </h4>
              <p className="text-xs text-slate-400">
                {project.isSuspended 
                  ? 'Re-enable routing to your project. It will be instantly available again.'
                  : 'Temporarily block access to your live project. Returns a 403 Forbidden.'}
              </p>
            </div>
            <button
              onClick={handleSuspendToggle}
              className={`flex items-center space-x-2 px-4 py-2 rounded-xl text-sm font-medium transition border ${
                project.isSuspended 
                  ? 'bg-emerald-500/10 text-emerald-400 border-emerald-500/30 hover:bg-emerald-500/20' 
                  : 'bg-amber-500/10 text-amber-400 border-amber-500/30 hover:bg-amber-500/20'
              }`}
            >
              {project.isSuspended ? <Power className="w-4 h-4" /> : <PowerOff className="w-4 h-4" />}
              <span>{project.isSuspended ? 'Resume Project' : 'Suspend Project'}</span>
            </button>
          </div>

          <div className="p-5 rounded-xl border border-red-900/30 bg-slate-900 flex flex-col justify-between items-start space-y-4">
            <div>
              <h4 className="text-sm font-medium text-red-400 mb-1">Delete Project</h4>
              <p className="text-xs text-slate-400">
                Permanently delete this project and all of its deployment history. This action cannot be undone.
              </p>
            </div>
            <button
              onClick={handleDelete}
              className="flex items-center space-x-2 px-4 py-2 rounded-xl text-sm font-medium bg-red-500/10 text-red-400 border border-red-500/30 hover:bg-red-500/20 transition"
            >
              <Trash2 className="w-4 h-4" />
              <span>Delete Project</span>
            </button>
          </div>
        </div>
      </div>

      {/* Deployment List */}
      <DeploymentList
        deployments={deployments}
        project={project}
        onRefresh={loadDeployments}
      />
    </div>
  );
};
